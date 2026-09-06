'use client';
import { Alert, Avatar, BottomNavigation, BottomNavigationAction, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Divider, IconButton, Paper, Snackbar, Stack, TextField, Typography } from '@mui/material';
import { CheckRounded, HeadsetMicRounded, NotificationsNoneRounded, WifiOffRounded } from '@mui/icons-material';
import { useEffect, useState } from 'react';
import { useDesk, matchesInbox } from '@/lib/store';
import { DEMO, type Inbox } from '@/lib/types';
import { useCalls } from '@/lib/use-calls';
import { InboxList, InboxNavigation, EmptyThread, inboxes, initials } from './inbox';
import { ConversationThread } from './conversation';
import { CallScreen } from './call-screen';

export function Workspace() {
  const { initialize, error, dismissError, online, needsLogin, selectedId, conversations, inbox, search } = useDesk();
  const calls = useCalls();
  const chat = conversations.find((item) => item.id === selectedId);
  useEffect(() => { void initialize(); if ('serviceWorker' in navigator) void navigator.serviceWorker.register('/sw.js').catch(() => undefined); }, [initialize]);
  useEffect(() => { if (DEMO) return; const timer = setTimeout(() => { void useDesk.getState().refresh().catch(useDesk.getState().fail); }, 300); return () => clearTimeout(timer); }, [search]);
  return <Box sx={{ height: '100dvh', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
    <Header connected={calls.connected} />{!online && <Alert icon={<WifiOffRounded />} severity="warning" sx={{ borderRadius: 0 }}>You’re offline. Your open conversation is still here.</Alert>}
    <Box sx={{ display: 'flex', flex: 1, minHeight: 0 }}>
      <Sidebar />
      <Box component="main" sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', p: { xs: 0, md: 3 }, pt: { md: 3 } }}>
        <WorkspaceHeading />
        <Paper sx={{ flex: 1, minHeight: 0, display: 'grid', gridTemplateColumns: { xs: '1fr', md: '350px minmax(0, 1fr)' }, overflow: 'hidden', border: { xs: 0, md: 1 }, borderColor: 'divider', borderRadius: { xs: 0, md: 3 } }}>
          <Box sx={{ minHeight: 0, display: { xs: chat ? 'none' : 'block', md: 'block' }, borderRight: { md: 1 }, borderColor: 'divider' }}><InboxList /></Box>
          <Box sx={{ minHeight: 0, minWidth: 0, bgcolor: 'background.default', display: { xs: chat ? 'block' : 'none', md: 'block' } }}>{chat ? <ConversationThread key={chat.id} chat={chat} onCall={(conversation, kind) => { void calls.start(conversation, kind); }} /> : <EmptyThread />}</Box>
        </Paper>
      </Box>
    </Box>
    {!chat && <BottomNavigation showLabels value={inbox} onChange={(_, value: Inbox) => useDesk.getState().setInbox(value)} sx={{ display: { md: 'none' }, borderTop: 1, borderColor: 'divider', height: 'calc(64px + env(safe-area-inset-bottom))', pb: 'env(safe-area-inset-bottom)' }}>{inboxes.map(({ id, label, icon: Icon }) => <BottomNavigationAction key={id} value={id} label={label} icon={<Icon />} sx={{ minWidth: 0, '& .MuiBottomNavigationAction-label': { fontSize: 11 } }} />)}</BottomNavigation>}
    <Snackbar open={Boolean(error)} onClose={dismissError} anchorOrigin={{ vertical: 'top', horizontal: 'center' }}><Alert severity="warning" onClose={dismissError} sx={{ maxWidth: 520 }}>{error}</Alert></Snackbar>
    {needsLogin && <Login />}
    {calls.call && <CallScreen call={calls.call} local={calls.local} remote={calls.remote} onEnd={calls.end} onAccept={calls.accept} onMute={calls.mute} onCamera={calls.camera} onSwitch={calls.switchCamera} />}
  </Box>;
}
function Header({ connected }: { connected: boolean }) {
  const { agent, fail } = useDesk();
  const notify = async () => {
    if (!('Notification' in window)) { fail(new Error('This browser does not support notifications.')); return; }
    const permission = await Notification.requestPermission();
    fail(new Error(permission === 'granted' ? 'Notifications allowed. Background delivery still requires a push subscription service.' : 'Notifications are blocked. You can change this in browser settings.'));
  };
  return <Stack component="header" direction="row" alignItems="center" sx={{ height: { xs: 64, md: 76 }, flexShrink: 0, px: { xs: 2, md: 3.5 }, borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper', gap: 1.5 }}>
    <Box sx={{ width: 35, height: 35, bgcolor: 'primary.main', color: 'white', display: 'grid', placeItems: 'center', borderRadius: 2 }}><HeadsetMicRounded sx={{ fontSize: 22 }} /></Box><Typography variant="h6" sx={{ fontSize: { xs: 16, md: 18 }, letterSpacing: '-0.6px' }}>evenbetter<span>help</span><Box component="span" sx={{ color: 'primary.main' }}>.</Box></Typography>
    <Divider orientation="vertical" flexItem sx={{ mx: 2, my: 3, display: { xs: 'none', md: 'block' } }} /><Typography variant="body2" color="text.secondary" sx={{ display: { xs: 'none', md: 'block' } }}>Team workspace</Typography><Box sx={{ flex: 1 }} />
    <Chip size="small" variant="outlined" icon={<Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: DEMO ? 'secondary.main' : 'primary.main' }} />} label={connectionLabel(connected)} sx={{ px: 0.5, borderColor: 'divider', display: { xs: 'none', sm: 'flex' } }} />
    <IconButton aria-label="Enable notifications" onClick={() => { void notify(); }}><NotificationsNoneRounded /></IconButton><Avatar sx={{ width: 34, height: 34, bgcolor: 'primary.light', color: 'primary.dark', fontSize: 12, fontWeight: 700 }}>{initials(agent.name)}</Avatar>
  </Stack>;
}
function connectionLabel(connected: boolean) { if (DEMO) return 'Demo workspace'; return connected ? 'Live updates connected' : 'Reconnecting'; }
function Sidebar() {
  const { agent } = useDesk();
  return <Stack component="nav" aria-label="Main navigation" sx={{ width: 238, flexShrink: 0, borderRight: 1, borderColor: 'divider', p: 2, pt: 3, display: { xs: 'none', md: 'flex' }, bgcolor: 'background.paper' }}><InboxNavigation /><Box sx={{ flex: 1 }} /><Paper sx={{ p: 2, bgcolor: 'background.default', mb: 2 }}><Stack direction="row" spacing={1} alignItems="center"><CheckRounded fontSize="small" color="primary" /><Typography variant="body2" fontWeight={600}>A shared effort</Typography></Stack><Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>Small moments of help make a big difference.</Typography></Paper><Divider /><Stack direction="row" spacing={1.5} sx={{ pt: 2 }}><Avatar sx={{ bgcolor: 'primary.light', color: 'primary.main', width: 36, height: 36, fontSize: 13 }}>{initials(agent.name)}</Avatar><Box><Typography variant="body2" fontWeight={600}>{agent.name}</Typography><Typography variant="caption" color="text.secondary">Support agent</Typography></Box></Stack></Stack>;
}
function WorkspaceHeading() {
  const { conversations, agent } = useDesk();
  const waiting = conversations.filter((chat) => matchesInbox(chat, 'unassigned', agent.id)).length;
  return <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2.5, display: { xs: 'none', md: 'flex' } }}><Box><Typography variant="h4">Team inbox</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>Every conversation is a chance to help.</Typography></Box><Stack direction="row" spacing={1} alignItems="center"><Typography variant="h5" color="primary">{waiting}</Typography><Typography variant="body2" color="text.secondary">waiting for a little help</Typography></Stack></Stack>;
}
function Login() {
  const [token, setToken] = useState('');
  const [busy, setBusy] = useState(false);
  const login = async () => {
    setBusy(true);
    try {
      const result = await fetch('/api/session', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }) });
      if (!result.ok) throw new Error('This sign-in token is invalid or has expired.');
      await useDesk.getState().initialize();
    } catch (error) { useDesk.getState().fail(error); } finally { setBusy(false); }
  };
  return <Dialog open fullWidth maxWidth="xs"><DialogTitle>Sign in to your workspace</DialogTitle><DialogContent><Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>Enter the short-lived agent token issued by your identity service.</Typography><TextField label="Agent token" fullWidth type="password" autoComplete="off" value={token} onChange={(event) => setToken(event.target.value)} /></DialogContent><DialogActions sx={{ p: 3 }}><Button fullWidth variant="contained" disabled={busy || !token} onClick={() => { void login(); }}>{busy ? <CircularProgress size={22} /> : 'Continue'}</Button></DialogActions></Dialog>;
}
