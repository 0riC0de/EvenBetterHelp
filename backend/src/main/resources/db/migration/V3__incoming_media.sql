-- Incoming attachments belong to the customer; only outbound assets have an agent uploader.
ALTER TABLE media_assets ALTER COLUMN agent_id DROP NOT NULL;
