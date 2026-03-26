-- Add base_role to user_types so a UserType maps to a system Casbin role.
-- When a user is created with a UserGroup, the role is auto-derived from this field.
ALTER TABLE user_types ADD COLUMN IF NOT EXISTS base_role VARCHAR(50);
