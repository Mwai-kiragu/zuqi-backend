-- Add per-module approval requirement flag to UserType permissions.
-- When true, CREATE actions by users of that type for the module are routed through approval.
ALTER TABLE user_type_permissions
    ADD COLUMN IF NOT EXISTS requires_approval BOOLEAN NOT NULL DEFAULT FALSE;
