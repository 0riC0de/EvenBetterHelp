'use client';
import { Alert, Box, Button, Chip, IconButton, Paper, Stack, TextField, Typography } from '@mui/material';
import { AttachFileRounded, CloseRounded, SendRounded } from '@mui/icons-material';
import { useRef, useState } from 'react';
import { useDesk } from '@/lib/store';
import { DEMO, type Conversation, type Message, type MessageKind } from '@/lib/types';
import { uploadFile } from '@/lib/api';
import { VoiceRecorder } from './audio';

export function Composer({ chat, reply, clearReply }: { chat: Conversation; reply: Message | null; clearReply: () => void }) {
  const { agent, claim, online, capabilities, fail } = useDesk();
  if (chat.status === 'unassigned') return <Paper sx={{ p: 2, borderTop: 1, borderColor: 'divider' }}><Stack direction="row" alignItems="center" spacing={2}><Box sx={{ flex: 1 }}><Typography variant="body2" fontWeight={700}>Be the person who helps.</Typography><Typography variant="caption" color="text.secondary">Claim this conversation to start replying.</Typography></Box><Button disabled={!online} variant="contained" onClick={() => { void claim(chat); }}>Assign to me</Button></Stack></Paper>;
  if (chat.status === 'resolved') return <Alert severity="success" sx={{ borderRadius: 0 }}>This conversation is resolved.</Alert>;
  if (chat.assignedAgentId !== agent.id) return <Alert severity="info">Another agent is handling this conversation.</Alert>;
  if (!capabilities.messaging) return <Alert severity="info">Connect the WhatsApp bridge to start sending messages.</Alert>;
  return <ComposeInput key={chat.id} chat={chat} reply={reply} clearReply={clearReply} fail={fail} />;
}
function ComposeInput({ chat, reply, clearReply, fail }: { chat: Conversation; reply: Message | null; clearReply: () => void; fail: (error: unknown) => void }) {
  const online = useDesk((state) => state.online);
  const [draft, setDraft] = useState('');
  const [attachment, setAttachment] = useState<{ blob: Blob; name: string; kind: MessageKind } | null>(null);
  const [busy, setBusy] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const submit = async () => {
    if (busy || !online || (!draft.trim() && !attachment)) return;
    setBusy(true);
    try {
      const mediaId = !DEMO && attachment ? await uploadFile(chat.id, attachment.blob, attachment.name) : undefined;
      const mediaUrl = DEMO && attachment ? URL.createObjectURL(attachment.blob) : undefined;
      await useDesk.getState().send(chat.id, { id: crypto.randomUUID(), kind: attachment?.kind ?? 'text', body: draft.trim() || attachment?.name || '', mediaId, replyTo: reply?.id }, mediaUrl);
      setDraft(''); setAttachment(null); clearReply();
    } catch (error) { fail(error); } finally { setBusy(false); }
  };
  const choose = (file?: File) => {
    if (!file) return;
    if (file.size > 16 * 1024 * 1024) { fail(new Error('Choose a file smaller than 16 MB.')); return; }
    setAttachment({ blob: file, name: file.name, kind: attachmentKind(file.type) });
  };
  return <Box sx={{ p: { xs: 1.5, md: 2.5 }, bgcolor: 'background.paper', borderTop: 1, borderColor: 'divider' }}>
    {reply && <Stack direction="row" alignItems="center" sx={{ borderLeft: 3, borderColor: 'primary.main', pl: 1.5, mb: 1 }}><Typography variant="body2" noWrap sx={{ flex: 1 }}>Replying to: {reply.body}</Typography><IconButton aria-label="Cancel reply" onClick={clearReply}><CloseRounded fontSize="small" /></IconButton></Stack>}
    {attachment && <Chip sx={{ mb: 1, maxWidth: '100%' }} label={attachment.name} onDelete={() => setAttachment(null)} />}
    <Stack direction="row" spacing={0.5} alignItems="flex-end">
      <Box component="input" ref={fileInput} type="file" accept="image/png,image/jpeg,image/webp,application/pdf,audio/webm,audio/ogg,audio/mp4" sx={{ display: 'none' }} onChange={(event) => { choose(event.target.files?.[0]); event.target.value = ''; }} />
      <IconButton aria-label="Attach a file" disabled={busy} onClick={() => fileInput.current?.click()}><AttachFileRounded /></IconButton>
      <TextField multiline maxRows={5} fullWidth size="small" placeholder="Write a thoughtful reply…" value={draft} onChange={(event) => setDraft(event.target.value)} slotProps={{ htmlInput: { maxLength: 10000, 'aria-label': 'Message' } }} onKeyDown={(event) => { if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) { event.preventDefault(); void submit(); } }} />
      <VoiceRecorder onComplete={(blob) => setAttachment({ blob, name: 'Voice message', kind: 'voice' })} onError={(message) => fail(new Error(message))} />
      <IconButton color="primary" disabled={busy || !online || (!draft.trim() && !attachment)} aria-label="Send message" onClick={() => { void submit(); }} sx={{ bgcolor: 'primary.main', color: 'primary.contrastText', '&:hover': { bgcolor: 'primary.dark' } }}><SendRounded fontSize="small" /></IconButton>
    </Stack><Typography variant="caption" color="text.secondary" sx={{ display: { xs: 'none', md: 'block' }, mt: 1, pl: 6 }}>Enter for a new line · Ctrl + Enter to send</Typography>
  </Box>;
}
function attachmentKind(type: string): MessageKind {
  if (type.startsWith('audio/')) return 'voice';
  if (type.startsWith('image/')) return 'image';
  return 'document';
}
