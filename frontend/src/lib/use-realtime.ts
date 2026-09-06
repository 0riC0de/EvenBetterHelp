'use client';
import { useEffect, useRef, useState } from 'react';
import { DEMO, type Signal } from './types';
import { useDesk } from './store';
import { z } from 'zod';
const eventSchema = z.object({ type: z.string(), callId: z.string().uuid().optional(), payload: z.string().max(32000).optional() });

export function useRealtime(onSignal: (signal: Signal) => void) {
  const socket = useRef<WebSocket | null>(null);
  const signalHandler = useRef(onSignal);
  const [connected, setConnected] = useState(false);
  useEffect(() => { signalHandler.current = onSignal; }, [onSignal]);
  useEffect(() => {
    const update = () => useDesk.getState().setOnline(navigator.onLine);
    update(); window.addEventListener('online', update); window.addEventListener('offline', update);
    return () => { window.removeEventListener('online', update); window.removeEventListener('offline', update); };
  }, []);
  useEffect(() => {
    if (DEMO) return;
    let disposed = false;
    let retry = 500;
    let timer: ReturnType<typeof setTimeout>;
    const connect = () => {
      const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
      const ws = new WebSocket(`${protocol}//${location.host}/events`);
      socket.current = ws;
      ws.onopen = () => { retry = 500; setConnected(true); void sync(); };
      ws.onmessage = (event) => handleEvent(event.data, signalHandler.current);
      ws.onclose = () => { setConnected(false); if (!disposed) timer = setTimeout(connect, retry = Math.min(retry * 2, 30000)); };
    };
    connect();
    const polling = setInterval(() => { void sync(); }, 30000);
    return () => { disposed = true; clearTimeout(timer); clearInterval(polling); socket.current?.close(); };
  }, []);
  const send = (signal: Signal) => {
    if (socket.current?.readyState !== WebSocket.OPEN) throw new Error('Live connection is unavailable.');
    socket.current.send(JSON.stringify(signal));
  };
  return { connected, send };
}
async function sync() { try { await useDesk.getState().refresh(); } catch (error) { useDesk.getState().fail(error); } }
function handleEvent(raw: string, onSignal: (signal: Signal) => void) {
  try {
    const event = eventSchema.parse(JSON.parse(raw));
    if (event.type === 'invalidate') { void sync(); return; }
    if (event.callId) onSignal({ callId: event.callId, type: event.type, payload: event.payload ?? '{}' });
  } catch { useDesk.getState().fail(new Error('A live update could not be read. Reconnecting will refresh the inbox.')); }
}
