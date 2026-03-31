-- V191 widened workflow_tier to VARCHAR(100) but did not drop the CHECK constraint.
-- This drops it so comma-separated tiers (e.g. "INITIATOR,VERIFIER") are accepted.
ALTER TABLE user_groups DROP CONSTRAINT IF EXISTS user_groups_workflow_tier_check;
