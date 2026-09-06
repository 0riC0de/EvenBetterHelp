'use client';
import { Avatar, Badge, Box, Button, Chip, Divider, InputAdornment, LinearProgress, List, ListItemButton, Stack, TextField, Typography } from '@mui/material';
import { AccessTimeRounded, ArrowForwardRounded, CheckCircleOutlineRounded, InboxRounded, PersonOutlineRounded, PriorityHighRounded, SearchRounded, WhatsApp } from '@mui/icons-material';
import { useDesk, matchesInbox } from '@/lib/store';
import { DEMO, type Conversation, type Inbox } from '@/lib/types';
import { useEffect, useState } from 'react';

export const inboxes = [
  { id: 'unassigned' as const, label: 'Unassigned', icon: InboxRounded },
  { id: 'assigned' as const, label: 'Assigned to me', icon: PersonOutlineRounded },
  { id: 'escalated' as const, label: 'Escalated', icon: PriorityHighRounded },
  { id: 'resolved' as const, label: 'Resolved', icon: CheckCircleOutlineRounded },
];
export function initials(name: string) { return name.split(' ').map((word) => word[0]).slice(0, 2).join(''); }
export function QueueBadge({ chat }: { chat: Conversation }) {
  const queues = useDesk((state) => state.queues);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 15000); return () => clearInterval(timer); }, []);
  if (!chat.waitingSince) return null;
  const elapsed = Math.max(0, Math.floor((now - Date.parse(chat.waitingSince)) / 60000));
  const limit = (queues.find((queue) => queue.id === chat.queueId)?.slaSeconds ?? 900) / 60;
  const overdue = elapsed >= limit;
  return <Stack direction="row" spacing={0.5} alignItems="center" sx={{ color: overdue ? 'error.main' : 'text.secondary' }}><AccessTimeRounded sx={{ fontSize: 14 }} /><Typography variant="caption">{elapsed}m{overdue ? ' · overdue' : ''}</Typography></Stack>;
}
export function InboxNavigation() {
  const { inbox, conversations, agent, setInbox, queues } = useDesk();
  return <>
    <Typography variant="overline" sx={{ px: 2, color: 'text.secondary', letterSpacing: 1.6 }}>Workspace</Typography>
    <List sx={{ pt: 1 }}>{inboxes.map(({ id, label, icon: Icon }) => <ListItemButton key={id} selected={inbox === id} onClick={() => setInbox(id)} sx={{ borderRadius: 2, mb: 0.5, minHeight: 48, '&.Mui-selected': { bgcolor: 'primary.light', color: 'primary.dark' } }}>
      <Icon sx={{ mr: 1.5, fontSize: 21 }} /><Typography variant="body2" sx={{ flex: 1, fontWeight: inbox === id ? 700 : 500 }}>{label}</Typography><Typography variant="caption">{conversations.filter((chat) => matchesInbox(chat, id, agent.id)).length}</Typography>
    </ListItemButton>)}</List>
    <Divider sx={{ my: 3 }} /><Typography variant="overline" sx={{ px: 2, color: 'text.secondary', letterSpacing: 1.6 }}>Departments</Typography>
    <Stack spacing={2} sx={{ px: 2, mt: 2 }}>{queues.map((queue) => <Stack key={queue.id} direction="row" alignItems="center" spacing={1.5}><Box sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: 'primary.main' }} /><Typography variant="body2">{queue.name}</Typography></Stack>)}</Stack>
  </>;
}
export function InboxList() {
  const { conversations, inbox, search, setSearch, agent, selectedId, select, loading } = useDesk();
  const [department, setDepartment] = useState('all');
  const queues = useDesk((state) => state.queues);
  const chats = conversations.filter((chat) => matchesInbox(chat, inbox, agent.id)).filter((chat) => matchesSearch(chat, search)).filter((chat) => department === 'all' || chat.queueId === department);
  const title = inboxes.find((item) => item.id === inbox)?.label;
  return <Stack sx={{ height: '100%', minHeight: 0, bgcolor: 'background.paper' }}>
    <Box sx={{ p: 3, pb: 2 }}><Stack direction="row" alignItems="center" justifyContent="space-between"><Typography variant="h5">{title}</Typography><Chip size="small" label={chats.length} sx={{ bgcolor: 'primary.light', color: 'primary.main', fontWeight: 700 }} /></Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75, mb: 2.5 }}>{inboxDescription(inbox)}</Typography>
      <TextField fullWidth size="small" placeholder="Search conversations" value={search} onChange={(event) => setSearch(event.target.value)} slotProps={{ htmlInput: { 'aria-label': 'Search conversations' }, input: { startAdornment: <InputAdornment position="start"><SearchRounded fontSize="small" /></InputAdornment> } }} />
      <TextField select fullWidth size="small" value={department} onChange={(event) => setDepartment(event.target.value)} slotProps={{ select: { native: true }, htmlInput: { 'aria-label': 'Filter by department' } }} sx={{ mt: 1.5 }}><option value="all">All departments</option>{queues.map((queue) => <option key={queue.id} value={queue.id}>{queue.name}</option>)}</TextField>
    </Box><Divider />{loading && <LinearProgress />}
    <Box sx={{ overflowY: 'auto', flex: 1 }}>{chats.map((chat) => <ConversationRow key={chat.id} chat={chat} selected={selectedId === chat.id} onSelect={() => { void select(chat.id); }} />)}{!loading && chats.length === 0 && <Box sx={{ p: 4, textAlign: 'center', color: 'text.secondary' }}><CheckCircleOutlineRounded sx={{ fontSize: 36, mb: 1 }} /><Typography variant="h6">You’re all caught up</Typography><Typography variant="body2">Conversations matching this view will appear here.</Typography></Box>}</Box>
    <Divider /><Stack direction="row" spacing={1} alignItems="center" sx={{ px: 3, py: 1.5, color: 'text.secondary' }}><WhatsApp sx={{ fontSize: 16 }} /><Typography variant="caption">{DEMO ? 'Demo inbox · fictional conversations' : 'WhatsApp team inbox'}</Typography></Stack>
  </Stack>;
}
function matchesSearch(chat: Conversation, search: string) {
  if (!DEMO) return true;
  return `${chat.name} ${chat.preview} ${chat.tags.join(' ')}`.toLowerCase().includes(search.toLowerCase());
}
function inboxDescription(inbox: Inbox) {
  const descriptions = { unassigned: 'A little help starts with a conversation.', assigned: 'Your conversations, all in one place.', escalated: 'Conversations that need extra attention.', resolved: 'Good conversations. Happy endings.' };
  return descriptions[inbox];
}
function ConversationRow({ chat, selected, onSelect }: { chat: Conversation; selected: boolean; onSelect: () => void }) {
  return <ListItemButton selected={selected} onClick={onSelect} sx={{ px: 3, py: 2.5, borderBottom: 1, borderColor: 'divider', alignItems: 'flex-start', borderLeft: 3, borderLeftColor: selected ? 'primary.main' : 'transparent', '&.Mui-selected': { bgcolor: 'primary.light' } }}>
    <Badge color="success" variant="dot" overlap="circular" anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}><Avatar sx={{ bgcolor: selected ? 'primary.main' : 'background.default', color: selected ? 'primary.contrastText' : 'text.secondary', fontSize: 14, fontWeight: 700, width: 44, height: 44 }}>{initials(chat.name)}</Avatar></Badge>
    <Box sx={{ ml: 1.5, minWidth: 0, flex: 1 }}><Stack direction="row" justifyContent="space-between" spacing={1}><Typography variant="body2" fontWeight={700} noWrap>{chat.name}</Typography>{chat.unread > 0 && <Box sx={{ px: 0.7, minWidth: 18, height: 18, bgcolor: 'primary.main', color: 'white', borderRadius: 1, textAlign: 'center', fontSize: 11 }}>{chat.unread}</Box>}</Stack><Typography variant="body2" color="text.secondary" sx={{ mt: 0.6, overflow: 'hidden', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' }}>{chat.preview}</Typography><Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mt: 1.5 }}><Chip label={chat.tags[0] ?? 'General'} size="small" variant="outlined" /><QueueBadge chat={chat} /></Stack></Box>
  </ListItemButton>;
}
export function EmptyThread() {
  const { conversations, inbox, agent, select } = useDesk();
  const next = conversations.find((chat) => matchesInbox(chat, inbox, agent.id));
  return <Stack alignItems="center" justifyContent="center" sx={{ height: '100%', p: 4, textAlign: 'center' }}><Box sx={{ width: 88, height: 88, display: 'grid', placeItems: 'center', bgcolor: 'primary.light', color: 'primary.main', borderRadius: 6, mb: 3 }}><WhatsApp sx={{ fontSize: 44 }} /></Box><Typography variant="h4">Make someone’s day better.</Typography><Typography color="text.secondary" sx={{ mt: 1.5, maxWidth: 340 }}>Pick a conversation and give your customers a little help from a real person.</Typography>{next && <Button variant="contained" endIcon={<ArrowForwardRounded />} sx={{ mt: 3 }} onClick={() => { void select(next.id); }}>Open next conversation</Button>}<Typography variant="caption" color="text.secondary" sx={{ mt: 5 }}>One shared inbox. A more human connection.</Typography></Stack>;
}
