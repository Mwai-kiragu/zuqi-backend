-- Activity Log table (full audit trail)
CREATE TABLE activity_logs (
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

-- Indexes for common query patterns
CREATE INDEX idx_activity_logs_user    ON activity_logs(user_id);
CREATE INDEX idx_activity_logs_entity  ON activity_logs(entity_type, entity_id);
CREATE INDEX idx_activity_logs_action  ON activity_logs(action);
CREATE INDEX idx_activity_logs_module  ON activity_logs(module);
CREATE INDEX idx_activity_logs_created ON activity_logs(created_at DESC);

-- Partial index for failures only (for security monitoring)
CREATE INDEX idx_activity_logs_failures ON activity_logs(created_at DESC) WHERE success = FALSE;

COMMENT ON TABLE activity_logs IS 'Complete audit trail for all system actions — required for compliance and segregation of duties';
