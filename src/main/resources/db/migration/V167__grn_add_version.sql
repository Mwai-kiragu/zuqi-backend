-- Add optimistic-locking version column missing from V166
ALTER TABLE goods_receipt_notes
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
