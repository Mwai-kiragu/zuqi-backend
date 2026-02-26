-- Activity Log Table
-- Immutable audit trail of all user and system actions across the platform
-- Part of Phase 2: Audit & Compliance Infrastructure

CREATE TABLE IF NOT EXISTS activity_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID,
    user_email   VARCHAR(255),
    user_name    VARCHAR(255),
    action       VARCHAR(50)  NOT NULL,
    entity_type  VARCHAR(100),
    entity_id    UUID,
    entity_name  VARCHAR(255),
    module       VARCHAR(100),
    description  TEXT,
    old_values   JSONB        NOT NULL DEFAULT '{}',
    new_values   JSONB        NOT NULL DEFAULT '{}',
    ip_address   VARCHAR(50),
    user_agent   TEXT,
    success      BOOLEAN      NOT NULL DEFAULT TRUE,
    error_message TEXT,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Comments for documentation
COMMENT ON TABLE  activity_logs IS 'Immutable audit trail — every significant action is recorded here for compliance and investigation';
COMMENT ON COLUMN activity_logs.action      IS 'Action type: CREATE, UPDATE, DELETE, LOGIN, APPROVE, REJECT, etc.';
COMMENT ON COLUMN activity_logs.old_values  IS 'Entity state before the action (empty for CREATE)';
COMMENT ON COLUMN activity_logs.new_values  IS 'Entity state after the action (empty for DELETE)';
COMMENT ON COLUMN activity_logs.module      IS 'Application module: order, payment, credit, inventory, user, etc.';

-- Indexes for activity log queries
CREATE INDEX IF NOT EXISTS idx_activity_logs_user
    ON activity_logs(user_id);

CREATE INDEX IF NOT EXISTS idx_activity_logs_entity
    ON activity_logs(entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_activity_logs_action
    ON activity_logs(action);

CREATE INDEX IF NOT EXISTS idx_activity_logs_module
    ON activity_logs(module);

CREATE INDEX IF NOT EXISTS idx_activity_logs_created
    ON activity_logs(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_activity_logs_failures
    ON activity_logs(created_at DESC) WHERE success = FALSE;
