-- Persist receipts that arrive before the outbound acknowledgement is committed.
CREATE TABLE provider_receipts (
    provider_id TEXT PRIMARY KEY,
    status TEXT NOT NULL CHECK (status IN ('sent','delivered','read')),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
