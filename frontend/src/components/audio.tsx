'use client';
import { Box, Button, IconButton, Stack, Typography } from '@mui/material';
import { DeleteOutlineRounded, MicRounded, PauseRounded, PlayArrowRounded, StopRounded } from '@mui/icons-material';
import { useEffect, useRef, useState } from 'react';
import { errorMessage } from '@/lib/api';

export function AudioPlayer({ src }: { src: string }) {
  const ref = useRef<HTMLAudioElement>(null);
  const [playing, setPlaying] = useState(false);
  const [rate, setRate] = useState(1);
  const [duration, setDuration] = useState(0);
  const toggle = async () => { const audio = ref.current; if (!audio) return; if (audio.paused) await audio.play(); else audio.pause(); };
  const speed = () => { const next = ({ 1: 1.5, 1.5: 2, 2: 1 })[rate] ?? 1; setRate(next); if (ref.current) ref.current.playbackRate = next; };
  return <Stack direction="row" alignItems="center" spacing={1}>
    <Box component="audio" ref={ref} src={src} preload="metadata" onPlay={() => setPlaying(true)} onPause={() => setPlaying(false)} onEnded={() => setPlaying(false)} onLoadedMetadata={() => setDuration(ref.current?.duration ?? 0)} />
    <IconButton aria-label={playing ? 'Pause voice message' : 'Play voice message'} onClick={() => { void toggle().catch(() => setPlaying(false)); }}>{playing ? <PauseRounded /> : <PlayArrowRounded />}</IconButton>
    <Typography variant="body2">Voice message · {Number.isFinite(duration) ? Math.round(duration) : '—'}s</Typography><Button size="small" onClick={speed} aria-label="Playback speed">{rate}×</Button>
  </Stack>;
}
export function VoiceRecorder({ onComplete, onError }: { onComplete: (blob: Blob) => void; onError: (message: string) => void }) {
  const recorder = useRef<MediaRecorder | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const context = useRef<AudioContext | null>(null);
  const canvas = useRef<HTMLCanvasElement>(null);
  const animation = useRef(0);
  const discard = useRef(false);
  const [recording, setRecording] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const cleanup = () => { stream.current?.getTracks().forEach((track) => track.stop()); cancelAnimationFrame(animation.current); void context.current?.close().catch(() => undefined); context.current = null; };
  useEffect(() => () => { discard.current = true; if (recorder.current?.state === 'recording') recorder.current.stop(); cleanup(); }, []);
  useEffect(() => {
    if (!recording) return;
    const timer = setInterval(() => setSeconds((value) => value + 1), 1000);
    return () => clearInterval(timer);
  }, [recording]);
  useEffect(() => { if (seconds >= 120) recorder.current?.stop(); }, [seconds]);
  const start = async () => {
    try {
      stream.current = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true }, video: false });
      const mimeType = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4'].find((type) => MediaRecorder.isTypeSupported(type));
      if (!mimeType) throw new Error('Voice recording is unavailable in this browser.');
      const capture = new MediaRecorder(stream.current, { mimeType, audioBitsPerSecond: 32000 });
      const chunks: Blob[] = []; discard.current = false; recorder.current = capture;
      capture.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data); };
      capture.onstop = () => { cleanup(); setRecording(false); if (!discard.current) onComplete(new Blob(chunks, { type: mimeType })); };
      capture.start(250); setSeconds(0); setRecording(true); attachWaveform(stream.current);
    } catch (error) { cleanup(); onError(errorMessage(error)); }
  };
  const attachWaveform = (input: MediaStream) => {
    const audio = new AudioContext(); context.current = audio;
    const analyser = audio.createAnalyser(); analyser.fftSize = 256; audio.createMediaStreamSource(input).connect(analyser);
    const draw = () => { paintWaveform(canvas.current, analyser); animation.current = requestAnimationFrame(draw); }; draw();
  };
  if (!recording) return <IconButton aria-label="Record voice message" onClick={() => { void start(); }}><MicRounded /></IconButton>;
  return <Stack direction="row" alignItems="center"><IconButton aria-label="Discard recording" onClick={() => { discard.current = true; recorder.current?.stop(); }}><DeleteOutlineRounded /></IconButton><Box component="canvas" ref={canvas} width={100} height={32} sx={{ width: 70, height: 32, color: 'primary.main' }} /><Typography variant="caption">{seconds}s</Typography><IconButton color="error" aria-label="Finish recording" onClick={() => recorder.current?.stop()}><StopRounded /></IconButton></Stack>;
}
function paintWaveform(canvas: HTMLCanvasElement | null, analyser: AnalyserNode) {
  const context = canvas?.getContext('2d');
  if (!canvas || !context) return;
  const data = new Uint8Array(analyser.frequencyBinCount); analyser.getByteTimeDomainData(data);
  context.clearRect(0, 0, canvas.width, canvas.height); context.lineWidth = 2;
  context.strokeStyle = getComputedStyle(canvas).color; context.beginPath();
  data.forEach((value, index) => { const x = index / data.length * canvas.width; const y = value / 255 * canvas.height; if (index === 0) context.moveTo(x, y); else context.lineTo(x, y); });
  context.stroke();
}
