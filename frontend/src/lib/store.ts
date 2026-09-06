import { create } from 'zustand';
import { createDemo, demoAgent, demoQueues } from './demo';
import { DEMO, type Agent, type Capabilities, type Conversation, type Inbox, type Message, type Queue, type SendInput } from './types';
import { errorMessage, request } from './api';

interface DeskState {
  agent: Agent; queues: Queue[]; conversations: Conversation[]; messages: Record<string, Message[]>;
  capabilities: Capabilities; inbox: Inbox; selectedId: string | null; search: string; online: boolean;
  loading: boolean; error: string | null; needsLogin: boolean;
  initialize: () => Promise<void>; refresh: () => Promise<void>; select: (id: string | null) => Promise<void>;
  setInbox: (inbox: Inbox) => void; setSearch: (search: string) => void; setOnline: (online: boolean) => void;
  dismissError: () => void; fail: (error: unknown) => void; claim: (chat: Conversation) => Promise<void>;
  move: (chat: Conversation, status: Inbox, queueId: string, tags: string[]) => Promise<void>;
  send: (chatId: string, input: SendInput, mediaUrl?: string) => Promise<void>;
}
export const useDesk = create<DeskState>((set, get) => ({
  agent: demoAgent, queues: [], conversations: [], messages: {}, capabilities: { messaging: DEMO, calling: false, uploads: DEMO },
  inbox: 'unassigned', selectedId: null, search: '', online: true, loading: true, error: null, needsLogin: false,
  initialize: async () => {
    if (DEMO) { set({ ...createDemo(), queues: demoQueues, loading: false }); return; }
    try {
      const [agent, queues, capabilities] = await Promise.all([request<Agent>('/me'), request<Queue[]>('/queues'), request<Capabilities>('/capabilities')]);
      set({ agent, queues, capabilities, needsLogin: false }); await get().refresh();
    } catch (error) { set({ needsLogin: true }); get().fail(error); }
    finally { set({ loading: false }); }
  },
  refresh: async () => {
    if (DEMO) return;
    const [conversations, capabilities] = await Promise.all([request<Conversation[]>(`/conversations?search=${encodeURIComponent(get().search)}`), request<Capabilities>('/capabilities')]);
    set({ conversations, capabilities });
    if (get().selectedId) await get().select(get().selectedId);
  },
  select: async (id) => {
    set({ selectedId: id });
    if (DEMO || !id) return;
    try { const messages = await request<Message[]>(`/conversations/${id}/messages`); set((state) => ({ messages: { ...state.messages, [id]: messages } })); }
    catch (error) { get().fail(error); }
  },
  setInbox: (inbox) => set({ inbox, selectedId: null }), setSearch: (search) => set({ search }),
  setOnline: (online) => set({ online }), dismissError: () => set({ error: null }), fail: (error) => set({ error: errorMessage(error) }),
  claim: async (chat) => {
    try {
      const claimed = DEMO ? { ...chat, assignedAgentId: get().agent.id, status: 'assigned' as const } :
        await request<Conversation>(`/queues/${chat.queueId}/claim?conversationId=${chat.id}`, { method: 'POST' });
      set((state) => ({ conversations: replaceChat(state.conversations, claimed), inbox: 'assigned' }));
    } catch (error) { get().fail(error); }
  },
  move: async (chat, status, queueId, tags) => {
    try {
      const moved = DEMO ? { ...chat, status, queueId, tags, assignedAgentId: status === 'unassigned' ? null : chat.assignedAgentId } :
        await request<Conversation>(`/conversations/${chat.id}`, { method: 'PATCH', body: JSON.stringify({ status, queueId, tags }) });
      set((state) => ({ conversations: replaceChat(state.conversations, moved), selectedId: null, inbox: status }));
    } catch (error) { get().fail(error); }
  },
  send: async (chatId, input, mediaUrl) => {
    const pending: Message = { ...input, conversationId: chatId, direction: 'out', createdAt: new Date().toISOString(), status: 'queued', mediaUrl };
    set((state) => ({ messages: upsertMessage(state.messages, pending) }));
    try {
      const saved = DEMO ? { ...pending, status: 'sent' as const } : await request<Message>(`/conversations/${chatId}/messages`, { method: 'POST', body: JSON.stringify(input) });
      set((state) => ({ messages: upsertMessage(state.messages, saved), conversations: state.conversations.map((chat) => chat.id === chatId ? { ...chat, preview: input.body || input.kind, waitingSince: null, unread: 0 } : chat) }));
    } catch (error) { set((state) => ({ messages: upsertMessage(state.messages, { ...pending, status: 'failed' }) })); get().fail(error); }
  },
}));
export function upsertMessage(messages: Record<string, Message[]>, message: Message) {
  const previous = messages[message.conversationId] ?? [];
  return { ...messages, [message.conversationId]: [...previous.filter((item) => item.id !== message.id), message].sort((a, b) => a.createdAt.localeCompare(b.createdAt)) };
}
function replaceChat(chats: Conversation[], chat: Conversation) { return chats.map((item) => item.id === chat.id ? chat : item); }
export function matchesInbox(chat: Conversation, inbox: Inbox, agentId: string) {
  if (chat.status !== inbox) return false;
  return inbox !== 'assigned' || chat.assignedAgentId === agentId;
}
