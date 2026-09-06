'use client';
import { useCallback, useEffect, useRef, useState } from 'react';
import { request } from './api';
import { useDesk } from './store';
import { DEMO, type CallState, type Conversation, type Signal } from './types';
import { CallConnection } from './webrtc';
import { useRealtime } from './use-realtime';

export function useCalls() {
  const [call, setCall] = useState<CallState | null>(null);
  const [local, setLocal] = useState<MediaStream | null>(null);
  const [remote, setRemote] = useState<MediaStream | null>(null);
  const connection = useRef<CallConnection | null>(null);
  const pending = useRef<Signal[]>([]);
  const onSignal = useCallback((signal: Signal) => {
    if (signal.type === 'incoming') { setCall(incomingCall(signal)); return; }
    if (['end', 'decline', 'missed', 'failed'].includes(signal.type)) { connection.current?.close(); connection.current = null; setCall(null); pending.current = []; return; }
    if (!connection.current) { queueSignal(pending.current, signal); return; }
    void connection.current?.receive(signal).catch(useDesk.getState().fail);
  }, []);
  const realtime = useRealtime(onSignal);
  useEffect(() => () => connection.current?.close(), []);
  const start = async (chat: Conversation, kind: 'voice' | 'video') => {
    const state = useDesk.getState();
    if (!state.capabilities.calling) { state.fail(new Error(DEMO ? 'Calls are unavailable in the demo. Live WhatsApp calls require a connected Cobalt media gateway.' : 'Your call media gateway is not connected.')); return; }
    const id = crypto.randomUUID();
    try {
      await request(`/conversations/${chat.id}/calls`, { method: 'POST', body: JSON.stringify({ id, kind }) });
      const { iceServers } = await request<{ iceServers: RTCIceServer[] }>('/ice-servers');
      const peer = new CallConnection(realtime.send, id, setRemote, (status) => updateCallState(status, setCall));
      connection.current = peer; setCall({ id, conversationId: chat.id, name: chat.name, kind, status: 'connecting', incoming: false });
      setLocal(await peer.open(kind === 'video', iceServers)); await peer.offer();
    } catch (error) { cleanupCall(id, realtime.send, connection.current); connection.current = null; setCall(null); state.fail(error); }
  };
  const accept = async () => {
    if (!call) return;
    try {
      const { iceServers } = await request<{ iceServers: RTCIceServer[] }>('/ice-servers');
      const peer = new CallConnection(realtime.send, call.id, setRemote, (status) => updateCallState(status, setCall));
      connection.current = peer; setLocal(await peer.open(call.kind === 'video', iceServers));
      realtime.send({ callId: call.id, type: 'accept', payload: '{}' });
      for (const signal of pending.current) await peer.receive(signal);
      pending.current = []; setCall({ ...call, status: 'connecting' });
    } catch (error) { cleanupCall(call.id, realtime.send, connection.current); connection.current = null; setCall(null); useDesk.getState().fail(error); }
  };
  const end = () => {
    if (call) cleanupCall(call.id, realtime.send, connection.current, call.status === 'ringing' ? 'decline' : 'end');
    connection.current?.close(); connection.current = null; setCall(null); setLocal(null); setRemote(null);
  };
  return { call, local, remote, start, end, connected: realtime.connected,
    accept: () => { void accept(); },
    mute: (muted: boolean) => connection.current?.mute(muted), camera: (enabled: boolean) => connection.current?.camera(enabled),
    switchCamera: () => { void connection.current?.switchCamera().catch(useDesk.getState().fail); },
  };
}
function incomingCall(signal: Signal): CallState {
  const event = JSON.parse(signal.payload);
  const chat = useDesk.getState().conversations.find((item) => item.id === event.conversationId);
  return { id: signal.callId, conversationId: event.conversationId, kind: event.kind, name: chat?.name ?? 'WhatsApp customer', status: 'ringing', incoming: true };
}
function queueSignal(pending: Signal[], signal: Signal) { if (pending.length < 64) pending.push(signal); }
function cleanupCall(id: string, send: (signal: Signal) => void, peer: CallConnection | null, type = 'end') {
  try { send({ callId: id, type, payload: '{}' }); } catch (error) { useDesk.getState().fail(error); }
  peer?.close();
}
function updateCallState(status: RTCPeerConnectionState, set: (update: (current: CallState | null) => CallState | null) => void) {
  if (status === 'connected') set((current) => current ? { ...current, status: 'active' } : null);
  if (['failed', 'closed', 'disconnected'].includes(status)) set((current) => current ? { ...current, status: 'ended' } : null);
}
