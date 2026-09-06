-- Development bootstrap only. Never run automatically in production.
INSERT INTO agents(id,subject,name,email) VALUES
('11111111-1111-4111-8111-111111111111','local-agent','Alex Morgan','alex@example.test') ON CONFLICT DO NOTHING;
INSERT INTO queues(id,name,sla_seconds) VALUES
('22222222-2222-4222-8222-222222222222','Customer support',900),
('33333333-3333-4333-8333-333333333333','Billing & payments',1200) ON CONFLICT DO NOTHING;
INSERT INTO agent_queues SELECT a.id,q.id FROM agents a CROSS JOIN queues q WHERE a.subject='local-agent' ON CONFLICT DO NOTHING;
INSERT INTO routing_rules(queue_id,priority,field,pattern) VALUES ('33333333-3333-4333-8333-333333333333',10,'keyword','refund');
INSERT INTO conversations(id,customer_jid,customer_name,queue_id,preview,unread,waiting_since) VALUES
('44444444-4444-4444-8444-444444444444','15555550100@s.whatsapp.net','Demo customer','22222222-2222-4222-8222-222222222222','Could you help with my order?',1,now()) ON CONFLICT DO NOTHING;
INSERT INTO messages(id,conversation_id,direction,kind,body,status) VALUES
('55555555-5555-4555-8555-555555555555','44444444-4444-4444-8444-444444444444','in','text','Could you help with my order?','delivered') ON CONFLICT DO NOTHING;
