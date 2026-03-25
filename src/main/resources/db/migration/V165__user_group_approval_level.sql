-- Add approval_level to user_groups to define ordering within a workflow tier
-- Level 1 = first approver (e.g. branch verifier), Level 2 = final sign-off (e.g. finance authorizer)
ALTER TABLE user_groups
  ADD COLUMN IF NOT EXISTS approval_level INT;
