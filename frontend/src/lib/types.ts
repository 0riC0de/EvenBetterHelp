export type Inbox = 'unassigned' | 'assigned' | 'escalated' | 'resolved';
export type MessageKind = 'text' | 'image' | 'document' | 'sticker' | 'voice';
export type MessageStatus = 'queued' | 'sent' | 'delivered' | 'read' | 'failed';
export interface Agent { id: string; name: string }
export interface Queue { id: string; name: string; slaSeconds: number }
export interface Conversation {
  id: string; name: string; jid: string; queueId: string; assignedAgentId: string | null;
  status: Inbox; tags: string[]; preview: string; unread: number; waitingSince: string | null; updatedAt: string;
}
export interface Message {
  id: string; conversationId: string; direction: 'in' | 'out'; kind: MessageKind;
  body: string; status: MessageStatus; createdAt: string; mediaId?: string | null;
  mediaUrl?: string; replyTo?: string | null;
}
export interface SendInput { id: string; kind: MessageKind; body: string; mediaId?: string; replyTo?: string }
export interface Capabilities { messaging: boolean; calling: boolean; uploads: boolean }
export interface CallState { id: string; conversationId: string; name: string; kind: 'voice' | 'video'; status: 'ringing' | 'connecting' | 'active' | 'ended'; incoming: boolean }
export interface Signal { callId: string; type: string; payload: string }
export const DEMO = process.env.NEXT_PUBLIC_DEMO_MODE !== 'false';
