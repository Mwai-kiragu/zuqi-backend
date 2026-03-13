-- ===================================================
-- AI Assistant Chat History Table
-- Part of AI Chat Feature — Q&A and Report Generation
-- Author: Zuqi Engineering
-- Date: 2026-03-13
-- ===================================================

CREATE TABLE IF NOT EXISTS ai_chat_messages (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID         NOT NULL,
    distributor_id   UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    content          TEXT         NOT NULL,
    message_type     VARCHAR(30)  NOT NULL DEFAULT 'CHAT',
    report_type      VARCHAR(50),
    report_params    JSONB,
    token_count      INTEGER,
    model_name       VARCHAR(100),
    duration_ms      BIGINT,
    created_at       TIMESTAMP    DEFAULT NOW(),
    CONSTRAINT fk_chat_messages_distributor
        FOREIGN KEY (distributor_id) REFERENCES distributors(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_chat_message_role
        CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT chk_chat_message_type
        CHECK (message_type IN ('CHAT', 'REPORT'))
);

COMMENT ON TABLE  ai_chat_messages IS 'Conversation history for AI assistant. Each row is one turn (USER or ASSISTANT).';
COMMENT ON COLUMN ai_chat_messages.conversation_id IS 'Groups turns into sessions. Frontend generates UUID on first message.';
COMMENT ON COLUMN ai_chat_messages.role            IS 'USER = human input, ASSISTANT = AI reply';
COMMENT ON COLUMN ai_chat_messages.message_type    IS 'CHAT = Q&A turn, REPORT = generated report (stored as markdown in content)';
COMMENT ON COLUMN ai_chat_messages.report_type     IS 'SALES|INVENTORY|PAYMENT|CREDIT_RISK|REP_PERFORMANCE|MERCHANT_SUMMARY|DEMAND_FORECAST|ANOMALY_SUMMARY';
COMMENT ON COLUMN ai_chat_messages.report_params   IS 'JSON filter params used for report generation (e.g. periodDays, warehouseId)';
COMMENT ON COLUMN ai_chat_messages.duration_ms     IS 'LLM response time in milliseconds (ASSISTANT rows only)';

CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation
    ON ai_chat_messages(conversation_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_distributor_user
    ON ai_chat_messages(distributor_id, user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_distributor_conv
    ON ai_chat_messages(distributor_id, conversation_id);

CREATE INDEX IF NOT EXISTS idx_chat_messages_type
    ON ai_chat_messages(message_type);
