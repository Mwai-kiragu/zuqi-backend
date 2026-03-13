-- Add cash_enabled flag to merchants; defaults to true so all existing merchants keep cash on
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS cash_enabled BOOLEAN NOT NULL DEFAULT TRUE;
