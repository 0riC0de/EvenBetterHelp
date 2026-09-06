'use client';
import { Box, Button, Paper, Stack, Typography } from '@mui/material';
import { DoneAllRounded, DoneRounded, InsertDriveFileOutlined, ReplyRounded, ScheduleRounded } from '@mui/icons-material';
import { useEffect, useRef, useState } from 'react';
import type { Message } from '@/lib/types';
import { request } from '@/lib/api';
import { AudioPlayer } from './audio';
import { useDesk } from '@/lib/store';
export function ChatBubble({ message, onReply }: { message: Message; onReply: (message: Message) => void }) {
  const origin = useRef<number | null>(null);
  const outbound = message.direction === 'out';
  return <Stack direction="row" justifyContent={outbound ? 'flex-end' : 'flex-start'} sx={{ mb: 2 }}>
    <Box sx={{ maxWidth: { xs: '90%', md: '78%' } }} onTouchStart={(event) => { origin.current = event.touches[0].clientX; }} onTouchEnd={(event) => { if (origin.current !== null && event.changedTouches[0].clientX - origin.current > 70) onReply(message); origin.current = null; }}>
      <Paper sx={{ px: 2, py: 1.5, bgcolor: outbound ? 'primary.light' : 'background.paper', border: 1, borderColor: outbound ? 'primary.light' : 'divider', borderRadius: 3, borderBottomRightRadius: outbound ? 4 : 12, borderBottomLeftRadius: outbound ? 12 : 4 }}>
        {message.replyTo && <Stack direction="row" spacing={1} sx={{ mb: 1, borderLeft: 2, borderColor: 'primary.main', pl: 1, color: 'text.secondary' }}><ReplyRounded fontSize="small" /><Typography variant="caption">Reply to an earlier message</Typography></Stack>}
        <MessageContent message={message} />
        <Stack direction="row" alignItems="center" justifyContent="flex-end" spacing={0.6} sx={{ mt: 0.75, color: 'text.secondary' }}><Typography variant="caption">{new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</Typography>{outbound && <Receipt status={message.status} />}</Stack>
      </Paper><Stack direction="row" justifyContent={outbound ? 'flex-end' : 'flex-start'}><Button size="small" onClick={() => onReply(message)} sx={{ minHeight: 32, color: 'text.secondary', fontSize: 11 }}>Reply</Button>{message.status === 'failed' && <Button size="small" color="error" onClick={() => { void useDesk.getState().send(message.conversationId, { id: message.id, kind: message.kind, body: message.body, mediaId: message.mediaId ?? undefined, replyTo: message.replyTo ?? undefined }, message.mediaUrl); }}>Retry send</Button>}</Stack>
    </Box>
  </Stack>;
}
function Receipt({ status }: { status: Message['status'] }) {
  if (status === 'failed') return <Typography variant="caption" color="error">Failed</Typography>;
  if (status === 'queued') return <ScheduleRounded sx={{ fontSize: 14 }} aria-label="Queued" />;
  if (status === 'sent') return <DoneRounded sx={{ fontSize: 14 }} aria-label="Sent" />;
  return <DoneAllRounded sx={{ fontSize: 15, color: status === 'read' ? 'info.main' : 'text.secondary' }} aria-label={status} />;
}
function MessageContent({ message }: { message: Message }) {
  if (message.kind === 'text') return <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{message.body}</Typography>;
  return <Attachment message={message} />;
}
function Attachment({ message }: { message: Message }) {
  const [url, setUrl] = useState(message.mediaUrl ?? '');
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    if (!message.mediaId) return;
    let mounted = true;
    request<{ url: string }>(`/media/${message.mediaId}`).then((result) => { if (mounted) setUrl(result.url); }).catch(() => { if (mounted) setFailed(true); });
    return () => { mounted = false; };
  }, [message.mediaId]);
  if (failed) return <Typography color="error" variant="body2">Attachment unavailable. Reopen this conversation to retry.</Typography>;
  if (!url) return <Typography variant="body2">Loading attachment…</Typography>;
  const renderers = {
    voice: () => <AudioPlayer src={url} />,
    image: () => <Box component="img" src={url} alt={message.body || 'Customer attachment'} sx={{ display: 'block', maxWidth: '100%', maxHeight: 300, borderRadius: 2 }} />,
    sticker: () => <Box component="img" src={url} alt="Sticker" sx={{ width: 120, height: 120, objectFit: 'contain' }} />,
    document: () => <Button component="a" href={url} target="_blank" rel="noopener noreferrer" startIcon={<InsertDriveFileOutlined />}>{message.body || 'Open document'}</Button>,
    text: () => <Typography>{message.body}</Typography>,
  };
  return renderers[message.kind]();
}
