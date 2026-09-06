CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE queues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    sla_seconds INTEGER NOT NULL DEFAULT 900 CHECK (sla_seconds > 0)
);
CREATE TABLE agent_queues (
    agent_id UUID REFERENCES agents(id) ON DELETE CASCADE,
    queue_id UUID REFERENCES queues(id) ON DELETE CASCADE,
    PRIMARY KEY (agent_id, queue_id)
);
CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_jid TEXT NOT NULL,
    customer_name TEXT NOT NULL,
    queue_id UUID NOT NULL REFERENCES queues(id),
    assigned_agent_id UUID REFERENCES agents(id),
    status TEXT NOT NULL DEFAULT 'unassigned' CHECK (status IN ('unassigned','assigned','escalated','resolved')),
    tags TEXT[] NOT NULL DEFAULT '{}',
    metadata JSONB NOT NULL DEFAULT '{}',
    preview TEXT NOT NULL DEFAULT '',
    unread INTEGER NOT NULL DEFAULT 0 CHECK (unread >= 0),
    waiting_since TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (status <> 'assigned' OR assigned_agent_id IS NOT NULL),
    CHECK (status <> 'unassigned' OR assigned_agent_id IS NULL)
);
CREATE UNIQUE INDEX one_open_chat ON conversations(customer_jid) WHERE status <> 'resolved';
CREATE INDEX queue_claim_order ON conversations(queue_id, created_at, id) WHERE assigned_agent_id IS NULL AND status = 'unassigned';
CREATE INDEX assigned_inbox ON conversations(assigned_agent_id, updated_at DESC, id);
CREATE INDEX queue_inbox ON conversations(queue_id, updated_at DESC, id);
CREATE INDEX waiting_sla ON conversations(waiting_since) WHERE status <> 'resolved';
CREATE INDEX conversation_tags ON conversations USING GIN(tags);

CREATE TABLE media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    agent_id UUID NOT NULL REFERENCES agents(id),
    object_key TEXT NOT NULL UNIQUE,
    file_name TEXT NOT NULL,
    content_type TEXT NOT NULL,
    bytes BIGINT NOT NULL CHECK (bytes BETWEEN 1 AND 16777216),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    provider_id TEXT UNIQUE,
    sender_agent_id UUID REFERENCES agents(id),
    direction TEXT NOT NULL CHECK (direction IN ('in','out')),
    kind TEXT NOT NULL CHECK (kind IN ('text','image','document','sticker','voice')),
    body TEXT NOT NULL DEFAULT '' CHECK (length(body) <= 10000),
    media_id UUID REFERENCES media_assets(id),
    reply_to UUID REFERENCES messages(id),
    status TEXT NOT NULL CHECK (status IN ('queued','sent','delivered','read','failed')),
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', body)) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (kind = 'text' OR media_id IS NOT NULL)
);
CREATE INDEX messages_timeline ON messages(conversation_id, created_at DESC, id);
CREATE INDEX messages_search ON messages USING GIN(search_vector);
CREATE TABLE message_outbox (
    message_id UUID PRIMARY KEY REFERENCES messages(id),
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_until TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error TEXT
);
CREATE INDEX pending_outbox ON message_outbox(available_at) WHERE completed_at IS NULL;

CREATE TABLE call_sessions (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id),
    agent_id UUID NOT NULL REFERENCES agents(id),
    provider_id TEXT UNIQUE,
    direction TEXT NOT NULL CHECK (direction IN ('in','out')),
    kind TEXT NOT NULL CHECK (kind IN ('voice','video')),
    status TEXT NOT NULL CHECK (status IN ('ringing','connecting','active','ended','declined','missed','failed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ
);
CREATE INDEX call_history ON call_sessions(conversation_id, created_at DESC);
CREATE UNIQUE INDEX one_active_call_per_agent ON call_sessions(agent_id) WHERE status IN ('ringing','connecting','active');
CREATE TABLE routing_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id UUID NOT NULL REFERENCES queues(id),
    priority INTEGER NOT NULL DEFAULT 100,
    field TEXT NOT NULL CHECK (field IN ('keyword','tag','region')),
    pattern TEXT NOT NULL CHECK (length(pattern) > 0),
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX routing_priority ON routing_rules(priority, id) WHERE enabled;
CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    agent_id UUID REFERENCES agents(id),
    conversation_id UUID REFERENCES conversations(id),
    action TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
