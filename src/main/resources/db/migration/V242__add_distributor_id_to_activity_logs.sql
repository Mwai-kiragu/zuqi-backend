-- Add distributor_id to activity_logs for reliable tenant-scoped querying.
-- Without this column, scoping relied on resolving user_id → distributor via the users table,
-- which silently excluded entries for users whose distributorId was null or mismatched.
ALTER TABLE activity_logs
    ADD COLUMN IF NOT EXISTS distributor_id UUID;

CREATE INDEX IF NOT EXISTS idx_activity_logs_distributor
    ON activity_logs(distributor_id);
