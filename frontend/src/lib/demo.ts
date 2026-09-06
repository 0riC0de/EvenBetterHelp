import type { Agent, Conversation, Message, Queue } from './types';
export const demoAgent: Agent = { id: '11111111-1111-4111-8111-111111111111', name: 'Alex Morgan' };
export const demoQueues: Queue[] = [
  { id: 'support', name: 'Customer support', slaSeconds: 900 },
  { id: 'billing', name: 'Billing & payments', slaSeconds: 1200 },
  { id: 'technical', name: 'Technical support', slaSeconds: 1800 },
];
const people = [
  ['Sofia Chen', 'SC', 'Hi! Could you help me change the delivery address?', 'support', 'unassigned', 'Delivery', '12'],
  ['James Wilson', 'JW', 'I was charged twice for my subscription this month.', 'billing', 'unassigned', 'Payment', '18'],
  ['Amira Hassan', 'AH', 'That worked perfectly. Thank you so much!', 'technical', 'assigned', 'Account', '3'],
  ['Oliver Brooks', 'OB', 'Do you have an update on order #10482?', 'support', 'assigned', 'Delivery', '6'],
  ['Emma Laurent', 'EL', 'Voice message', 'support', 'unassigned', 'New customer', '4'],
  ['Daniel Park', 'DP', 'The verification code still hasn’t arrived.', 'technical', 'escalated', 'Priority', '25'],
  ['Maya Patel', 'MP', 'Can I switch to an annual plan?', 'billing', 'unassigned', 'Subscription', '2'],
  ['Noah Williams', 'NW', 'All sorted. Have a lovely day!', 'support', 'resolved', 'Delivery', '45'],
];
export function createDemo(now = Date.now()): { conversations: Conversation[]; messages: Record<string, Message[]> } {
  const conversations = people.map((person, index) => makeConversation(person, index, now));
  const messages = Object.fromEntries(conversations.map((chat) => [chat.id, conversationMessages(chat, now)]));
  return { conversations, messages };
}
function makeConversation(person: string[], index: number, now: number): Conversation {
  const [name, , preview, queueId, status, tag, minutes] = person;
  const assignedAgentId = ['assigned', 'escalated', 'resolved'].includes(status) ? demoAgent.id : null;
  return { id: `demo-${index}`, name, jid: `+1 (202) 555-01${10 + index}`, queueId, status: status as Conversation['status'],
    assignedAgentId, tags: [tag], preview, unread: status === 'resolved' ? 0 : 1,
    waitingSince: status === 'resolved' ? null : new Date(now - Number(minutes) * 60000).toISOString(), updatedAt: new Date(now - index * 60000).toISOString() };
}
function conversationMessages(chat: Conversation, now: number): Message[] {
  const lines: Array<[Message['direction'], string]> = [
    ['in', `Hello, I’m ${chat.name.split(' ')[0]}. I have a quick question about my account.`],
    ['out', 'Hi there! You’re in the right place. Tell us a little more and we’ll be happy to help.'],
    ['in', chat.preview],
  ];
  return lines.map(([direction, body], index) => ({ id: `${chat.id}-${index}`, conversationId: chat.id, direction,
    kind: 'text', body, status: 'read', createdAt: new Date(now - (20 - index * 3) * 60000).toISOString() }));
}
