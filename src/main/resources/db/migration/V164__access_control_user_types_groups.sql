-- V164: Access Control — UserTypes, UserTypePermissions, UserGroups, user_group_id on users

-- ─── user_types ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_types (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    distributor_id  UUID        REFERENCES distributors(id) ON DELETE CASCADE,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (distributor_id, name)
);

-- ─── user_type_permissions ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_type_permissions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_type_id    UUID        NOT NULL REFERENCES user_types(id) ON DELETE CASCADE,
    module          VARCHAR(60) NOT NULL,
    can_create      BOOLEAN     NOT NULL DEFAULT FALSE,
    can_read        BOOLEAN     NOT NULL DEFAULT FALSE,
    can_update      BOOLEAN     NOT NULL DEFAULT FALSE,
    can_delete      BOOLEAN     NOT NULL DEFAULT FALSE,
    can_approve     BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (user_type_id, module)
);

-- ─── user_groups ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_groups (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    distributor_id  UUID        REFERENCES distributors(id) ON DELETE CASCADE,
    user_type_id    UUID        NOT NULL REFERENCES user_types(id),
    workflow_tier   VARCHAR(20) CHECK (workflow_tier IN ('INITIATOR', 'VERIFIER', 'AUTHORIZER')),
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (distributor_id, name)
);

-- ─── users: add user_group_id (nullable — system users stay NULL) ─────────────
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS user_group_id UUID REFERENCES user_groups(id) ON DELETE SET NULL;

-- ─── indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_user_types_distributor         ON user_types(distributor_id);
CREATE INDEX IF NOT EXISTS idx_user_type_permissions_type     ON user_type_permissions(user_type_id);
CREATE INDEX IF NOT EXISTS idx_user_groups_distributor        ON user_groups(distributor_id);
CREATE INDEX IF NOT EXISTS idx_user_groups_user_type          ON user_groups(user_type_id);
CREATE INDEX IF NOT EXISTS idx_users_user_group               ON users(user_group_id);

-- ─── Casbin: DISTRIBUTOR_ADMIN and MERCHANT_ADMIN can manage access control ───
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/user-types',          'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/user-types',          'POST'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/user-types/.*',       'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/user-types/.*',       'PUT'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/user-types/.*',       'DELETE'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/user-groups',         'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/user-groups',         'POST'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/user-groups/.*',      'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/user-groups/.*',      'PUT'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/user-groups/.*',      'DELETE'),
    ('p', 'MERCHANT_ADMIN',    '/v1/user-types',          'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/user-types',          'POST'),
    ('p', 'MERCHANT_ADMIN',    '/v1/user-types/.*',       'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/user-types/.*',       'PUT'),
    ('p', 'MERCHANT_ADMIN',    '/v1/user-types/.*',       'DELETE'),
    ('p', 'MERCHANT_ADMIN',    '/v1/user-groups',         'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/user-groups',         'POST'),
    ('p', 'MERCHANT_ADMIN',    '/v1/user-groups/.*',      'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/user-groups/.*',      'PUT'),
    ('p', 'MERCHANT_ADMIN',    '/v1/user-groups/.*',      'DELETE'),
    ('p', 'SUPER_ADMIN',       '/v1/user-types',          'GET'),
    ('p', 'SUPER_ADMIN',       '/v1/user-types',          'POST'),
    ('p', 'SUPER_ADMIN',       '/v1/user-types/.*',       'GET'),
    ('p', 'SUPER_ADMIN',       '/v1/user-types/.*',       'PUT'),
    ('p', 'SUPER_ADMIN',       '/v1/user-types/.*',       'DELETE'),
    ('p', 'SUPER_ADMIN',       '/v1/user-groups',         'GET'),
    ('p', 'SUPER_ADMIN',       '/v1/user-groups',         'POST'),
    ('p', 'SUPER_ADMIN',       '/v1/user-groups/.*',      'GET'),
    ('p', 'SUPER_ADMIN',       '/v1/user-groups/.*',      'PUT'),
    ('p', 'SUPER_ADMIN',       '/v1/user-groups/.*',      'DELETE')
ON CONFLICT DO NOTHING;
