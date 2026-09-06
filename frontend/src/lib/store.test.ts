import { describe, expect, it } from 'vitest';
import { matchesInbox, upsertMessage } from './store';
import { createDemo, demoAgent } from './demo';
describe('agent inbox isolation', () => {
  it('does not include conversations assigned to another agent in mine', () => {
    const chat = createDemo().conversations.find((item) => item.status === 'assigned')!;
    expect(matchesInbox(chat, 'assigned', demoAgent.id)).toBe(true);
    expect(matchesInbox(chat, 'assigned', 'other-agent')).toBe(false);
    expect(matchesInbox(chat, 'unassigned', demoAgent.id)).toBe(false);
  });
});
describe('optimistic message reconciliation', () => {
  it('replaces retry and acknowledgement with one message using the same id', () => {
    const message = Object.values(createDemo().messages)[0][0];
    let result = upsertMessage({}, { ...message, status: 'queued' });
    result = upsertMessage(result, { ...message, status: 'failed' });
    result = upsertMessage(result, { ...message, status: 'delivered' });
    expect(result[message.conversationId]).toHaveLength(1);
    expect(result[message.conversationId][0].status).toBe('delivered');
  });
  it('preserves other conversations when receiving an update', () => {
    const data = createDemo();
    const message = Object.values(data.messages)[0][0];
    const result = upsertMessage(data.messages, message);
    expect(Object.keys(result)).toHaveLength(8);
  });
});
