-- Approval Requests table
CREATE TABLE approval_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_number      VARCHAR(50)  NOT NULL UNIQUE,
    workflow_type       VARCHAR(50)  NOT NULL,
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           UUID,
    entity_name         VARCHAR(255),
    requested_by_id     UUID         NOT NULL,
    requested_by_email  VARCHAR(255) NOT NULL,
    requested_by_name   VARCHAR(255),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    description         TEXT,
    current_values      JSONB        NOT NULL DEFAULT '{}',
    requested_values    JSONB        NOT NULL DEFAULT '{}',
    required_approvals  INTEGER      NOT NULL DEFAULT 1,
    received_approvals  INTEGER      NOT NULL DEFAULT 0,
    amount              NUMERIC(15, 2),
    rejection_reason    TEXT,
    approved_at         TIMESTAMP,
    rejected_at         TIMESTAMP,
    cancelled_at        TIMESTAMP,
    expires_at          TIMESTAMP,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP
);

-- Approval Actions table (individual approver decisions)
CREATE TABLE approval_actions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_request_id UUID         NOT NULL REFERENCES approval_requests(id) ON DELETE CASCADE,
    approver_id         UUID         NOT NULL,
    approver_email      VARCHAR(255) NOT NULL,
    approver_name       VARCHAR(255),
    decision            VARCHAR(20)  NOT NULL,
    approval_level      INTEGER      NOT NULL DEFAULT 1,
    comments            TEXT,
    action_at           TIMESTAMP    NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_approval_requests_status       ON approval_requests(status);
CREATE INDEX idx_approval_requests_type         ON approval_requests(workflow_type);
CREATE INDEX idx_approval_requests_entity       ON approval_requests(entity_type, entity_id);
CREATE INDEX idx_approval_requests_requester    ON approval_requests(requested_by_id);
CREATE INDEX idx_approval_requests_created      ON approval_requests(created_at DESC);

CREATE INDEX idx_approval_actions_request       ON approval_actions(approval_request_id);
CREATE INDEX idx_approval_actions_approver      ON approval_actions(approver_id);

COMMENT ON TABLE approval_requests IS 'Maker/checker approval workflow requests';
COMMENT ON TABLE approval_actions  IS 'Individual approver decisions on approval requests';
