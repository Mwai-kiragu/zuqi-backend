-- Allow workflow_tier to hold comma-separated values (e.g. "INITIATOR,VERIFIER")
ALTER TABLE user_groups ALTER COLUMN workflow_tier TYPE VARCHAR(100);
