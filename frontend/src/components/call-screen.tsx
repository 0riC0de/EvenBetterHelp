'use client';
import { Avatar, Box, Button, Dialog, IconButton, Stack, Typography } from '@mui/material';
import { CallEndRounded, CameraswitchRounded, MicOffRounded, MicRounded, PhoneRounded, VideocamOffRounded, VideocamRounded, VolumeUpRounded } from '@mui/icons-material';
import { useEffect, useRef, useState } from 'react';
import type { CallState } from '@/lib/types';
import { initials } from './inbox';

export function CallScreen({ call, remote, local, onEnd, onAccept, onMute, onCamera, onSwitch }: {
  call: CallState; remote: MediaStream | null; local: MediaStream | null; onEnd: () => void; onAccept: () => void;
  onMute: (muted: boolean) => void; onCamera: (enabled: boolean) => void; onSwitch: () => void;
}) {
  const [seconds, setSeconds] = useState(0);
  const [speakerError, setSpeakerError] = useState('');
  const remoteRef = useRef<HTMLVideoElement>(null);
  const localRef = useRef<HTMLVideoElement>(null);
  useEffect(() => { if (remoteRef.current) remoteRef.current.srcObject = remote; }, [remote]);
  useEffect(() => { if (localRef.current) localRef.current.srcObject = local; }, [local]);
  useEffect(() => { if (call.status !== 'active') return; const timer = setInterval(() => setSeconds((value) => value + 1), 1000); return () => clearInterval(timer); }, [call.status]);
  useEffect(() => { if (call.status !== 'ringing') return; navigator.vibrate?.([200, 100, 200]); }, [call.status]);
  const chooseSpeaker = async () => {
    try {
      const devices = navigator.mediaDevices as MediaDevices & { selectAudioOutput?: () => Promise<MediaDeviceInfo> };
      if (!devices.selectAudioOutput) throw new Error('Use your phone’s audio output controls.');
      const device = await devices.selectAudioOutput(); await remoteRef.current?.setSinkId(device.deviceId);
    } catch (error) { setSpeakerError(error instanceof Error ? error.message : 'Audio output could not be changed.'); }
  };
  return <Dialog open fullScreen PaperProps={{ sx: { bgcolor: 'primary.dark', color: 'primary.contrastText' } }}><Stack alignItems="center" justifyContent="space-between" sx={{ height: '100%', py: 5, px: 3 }}>
    <Typography variant="overline" sx={{ letterSpacing: 3 }}>EvenBetterHelp · {call.kind} call</Typography>
    <Stack alignItems="center" spacing={2}><Avatar sx={{ width: 100, height: 100, bgcolor: 'primary.main', fontSize: 36 }}>{initials(call.name)}</Avatar><Typography variant="h4">{call.name}</Typography><Typography>{call.status === 'active' ? `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` : callLabel(call)}</Typography></Stack>
    <Stack direction="row" spacing={1} sx={{ maxHeight: '30vh', maxWidth: 600 }}><Box component="video" ref={remoteRef} autoPlay playsInline sx={{ maxWidth: '70%', maxHeight: '30vh', borderRadius: 2, display: call.kind === 'video' ? 'block' : 'none' }} /><Box component="video" ref={localRef} autoPlay playsInline muted sx={{ maxWidth: '30%', maxHeight: '20vh', borderRadius: 2, display: call.kind === 'video' ? 'block' : 'none' }} /></Stack>
    <Stack alignItems="center" spacing={3} sx={{ pb: 'env(safe-area-inset-bottom)' }}>{speakerError && <Typography variant="body2">{speakerError}</Typography>}
      <CallControls call={call} onAccept={onAccept} onMute={onMute} onCamera={onCamera} onSwitch={onSwitch} chooseSpeaker={() => { void chooseSpeaker(); }} />
      <Button color="error" variant="contained" size="large" startIcon={<CallEndRounded />} onClick={onEnd} sx={{ minWidth: 180, borderRadius: 8 }}>{call.status === 'ringing' ? 'Decline' : 'End call'}</Button>
    </Stack>
  </Stack></Dialog>;
}
function CallControls({ call, onAccept, onMute, onCamera, onSwitch, chooseSpeaker }: {
  call: CallState; onAccept: () => void; onMute: (muted: boolean) => void; onCamera: (enabled: boolean) => void; onSwitch: () => void; chooseSpeaker: () => void;
}) {
  const [muted, setMuted] = useState(false);
  const [camera, setCamera] = useState(true);
  if (call.status === 'ringing') return <Button variant="contained" startIcon={<PhoneRounded />} onClick={onAccept}>Accept call</Button>;
  return <Stack direction="row" spacing={2}>
    <IconButton color="inherit" aria-label={muted ? 'Unmute' : 'Mute'} onClick={() => { setMuted(!muted); onMute(!muted); }}>{muted ? <MicOffRounded /> : <MicRounded />}</IconButton>
    <IconButton color="inherit" aria-label="Choose speaker" onClick={chooseSpeaker}><VolumeUpRounded /></IconButton>
    {call.kind === 'video' && <><IconButton color="inherit" aria-label="Toggle camera" onClick={() => { setCamera(!camera); onCamera(!camera); }}>{camera ? <VideocamRounded /> : <VideocamOffRounded />}</IconButton><IconButton color="inherit" aria-label="Switch camera" onClick={onSwitch}><CameraswitchRounded /></IconButton></>}
  </Stack>;
}
function callLabel(call: CallState) { if (call.status === 'ringing') return 'Incoming call…'; if (call.status === 'ended') return 'Call ended'; return 'Connecting securely…'; }
