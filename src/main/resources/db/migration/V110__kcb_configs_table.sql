-- V110: KCB payment configuration table
CREATE TABLE IF NOT EXISTS kcb_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    business_name VARCHAR(255) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    kcb_account_type VARCHAR(50),
    business_no VARCHAR(50),
    account_type VARCHAR(50),
    is_subscription_account BOOLEAN NOT NULL DEFAULT false,
    third_party_callback VARCHAR(500),
    consumer_key TEXT,
    consumer_secret TEXT,
    pass_key TEXT,
    external_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    configured_by_id UUID REFERENCES users(id),
    configured_by_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX idx_kcb_configs_merchant_id ON kcb_configs(merchant_id);
CREATE INDEX idx_kcb_configs_status ON kcb_configs(status);
