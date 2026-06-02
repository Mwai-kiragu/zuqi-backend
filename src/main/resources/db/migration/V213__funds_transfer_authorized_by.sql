ALTER TABLE funds_transfers
    ADD COLUMN IF NOT EXISTS authorized_by_id   UUID,
    ADD COLUMN IF NOT EXISTS authorized_by_name VARCHAR(200);
