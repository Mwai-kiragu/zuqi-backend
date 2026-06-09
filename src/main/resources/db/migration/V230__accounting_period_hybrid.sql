-- Hybrid accounting period: auto-lock after grace period, manual close after checklist

ALTER TABLE gl_periods
    ADD COLUMN IF NOT EXISTS grace_period_days INT NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS auto_locked       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS closed_notes      TEXT;

-- Migrate existing data: CLOSED periods that predate the hybrid model
-- treat them as already-locked-and-closed (set auto_locked = false, they were manual)
-- No data migration needed — existing status values map correctly:
-- OPEN stays OPEN, LOCKED stays LOCKED, CLOSED stays CLOSED (now means fully closed)

COMMENT ON COLUMN gl_periods.grace_period_days IS
    'Days after period end_date before auto-lock kicks in (default 5)';
COMMENT ON COLUMN gl_periods.auto_locked IS
    'True when period was locked automatically by the scheduler vs manually by a user';
COMMENT ON COLUMN gl_periods.closed_notes IS
    'Checklist notes recorded by the user when manually closing the period';
