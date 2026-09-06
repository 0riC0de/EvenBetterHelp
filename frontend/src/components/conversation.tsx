'use client';
import { Avatar, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, IconButton, MenuItem, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { ArrowBackRounded, CheckRounded, ExpandMoreRounded, PhoneOutlined, VideocamOutlined, WhatsApp } from '@mui/icons-material';
import { useEffect, useRef, useState } from 'react';
import { useDesk } from '@/lib/store';
import type { Conversation, Inbox, Message } from '@/lib/types';
import { initials, QueueBadge } from './inbox';
import { ChatBubble } from './chat-bubble';
import { Composer } from './composer';

export function ConversationThread({ chat, onCall }: { chat: Conversation; onCall: (chat: Conversation, kind: 'voice' | 'video') => void }) {
  const { messages, queues, select, move, agent } = useDesk();
  const [reply, setReply] = useState<Message | null>(null);
  const [routing, setRouting] = useState(false);
  const end = useRef<HTMLDivElement>(null);
  const timeline = messages[chat.id] ?? [];
  const own = chat.assignedAgentId === agent.id && chat.status !== 'resolved';
  useEffect(() => { end.current?.scrollIntoView({ behavior: 'auto' }); }, [timeline.length, chat.id]);
  return <Stack sx={{ height: '100%', minHeight: 0 }}>
    <Stack direction="row" alignItems="center" spacing={1.5} sx={{ px: { xs: 1, md: 3 }, py: 2, bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider' }}>
      <IconButton aria-label="Back to inbox" onClick={() => { void select(null); }} sx={{ display: { md: 'none' } }}><ArrowBackRounded /></IconButton><Avatar sx={{ bgcolor: 'primary.light', color: 'primary.dark', fontSize: 16 }}>{initials(chat.name)}</Avatar>
      <Box sx={{ flex: 1, minWidth: 0 }}><Typography variant="h6" noWrap>{chat.name}</Typography><Stack direction="row" spacing={0.5} alignItems="center" color="text.secondary"><WhatsApp sx={{ fontSize: 13 }} /><Typography variant="caption">{chat.jid}</Typography></Stack></Box>
      <Tooltip title="Voice call"><IconButton aria-label="Start voice call" disabled={!own} onClick={() => onCall(chat, 'voice')}><PhoneOutlined fontSize="small" /></IconButton></Tooltip><Tooltip title="Video call"><IconButton aria-label="Start video call" disabled={!own} onClick={() => onCall(chat, 'video')}><VideocamOutlined fontSize="small" /></IconButton></Tooltip>
    </Stack>
    <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ px: { xs: 2, md: 3 }, py: 1, bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider' }}><Button size="small" color="inherit" disabled={!own} endIcon={<ExpandMoreRounded />} onClick={() => setRouting(true)}>{queues.find((queue) => queue.id === chat.queueId)?.name ?? 'Department'}</Button><QueueBadge chat={chat} /></Stack>
    <Box sx={{ flex: 1, overflowY: 'auto', px: { xs: 2, md: 4 }, py: 3 }}>
      <Stack alignItems="center" spacing={1} sx={{ mb: 4 }}><Chip size="small" label="Conversation" sx={{ bgcolor: 'background.paper', color: 'text.secondary' }} /><Typography variant="caption" color="text.secondary">Customer support · WhatsApp</Typography></Stack>
      {timeline.map((message) => <ChatBubble key={message.id} message={message} onReply={setReply} />)}<div ref={end} />
    </Box>
    {own && <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ px: 2, py: 0.5, bgcolor: 'background.paper', borderTop: 1, borderColor: 'divider' }}><Typography variant="caption" color="text.secondary">Assigned to you</Typography><Button size="small" startIcon={<CheckRounded />} onClick={() => { void move(chat, 'resolved', chat.queueId, chat.tags); }}>Resolve</Button></Stack>}
    <Composer chat={chat} reply={reply} clearReply={() => setReply(null)} />
    {routing && <RoutingSheet chat={chat} close={() => setRouting(false)} />}
  </Stack>;
}
function RoutingSheet({ chat, close }: { chat: Conversation; close: () => void }) {
  const { queues, move } = useDesk();
  const [queueId, setQueueId] = useState(chat.queueId);
  const [status, setStatus] = useState<Inbox>(chat.status);
  const [tags, setTags] = useState(chat.tags.join(', '));
  return <Dialog open onClose={close} fullWidth maxWidth="xs" sx={{ '& .MuiDialog-container': { alignItems: { xs: 'flex-end', sm: 'center' } }, '& .MuiDialog-paper': { m: { xs: 0, sm: 4 }, width: { xs: '100%', sm: undefined }, borderRadius: { xs: '20px 20px 0 0', sm: 3 } } }}><DialogTitle>Route conversation</DialogTitle><Divider /><DialogContent><Stack spacing={3} sx={{ pt: 1 }}><TextField select label="Department" value={queueId} onChange={(event) => setQueueId(event.target.value)}>{queues.map((queue) => <MenuItem key={queue.id} value={queue.id}>{queue.name}</MenuItem>)}</TextField><TextField select label="Move to" value={status} onChange={(event) => setStatus(event.target.value as Inbox)}><MenuItem value="assigned">Assigned to me</MenuItem><MenuItem value="unassigned">Unassigned</MenuItem><MenuItem value="escalated">Escalated</MenuItem><MenuItem value="resolved">Resolved</MenuItem></TextField><TextField label="Tags" helperText="Separate tags with commas" value={tags} onChange={(event) => setTags(event.target.value)} /></Stack></DialogContent><DialogActions sx={{ p: 3 }}><Button onClick={close}>Cancel</Button><Button variant="contained" onClick={() => { void move(chat, status, queueId, tags.split(',').map((tag) => tag.trim()).filter(Boolean)); close(); }}>Save changes</Button></DialogActions></Dialog>;
}
