-- Add configured_by tracking to mpesa_configs (columns were added to entity after V99 ran)
ALTER TABLE mpesa_configs
    ADD COLUMN IF NOT EXISTS configured_by_id   UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS configured_by_name VARCHAR(255);
