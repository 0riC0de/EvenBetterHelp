CREATE TABLE gateway_deliveries (
    client_id UUID PRIMARY KEY,
    payload_hash TEXT NOT NULL,
    provider_id TEXT,
    status TEXT NOT NULL CHECK (status IN ('sending','accepted','uncertain')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE gateway_callbacks (
    event_key TEXT PRIMARY KEY,
    payload TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ
);
CREATE INDEX gateway_pending_callbacks ON gateway_callbacks(available_at) WHERE delivered_at IS NULL;
