-- V208: NCBA payment configuration tables
-- Drop any leftover tables from failed prior attempts (idempotent clean slate)
DROP TABLE IF EXISTS ncba_stk_requests;
DROP TABLE IF EXISTS ncba_configs;

CREATE TABLE ncba_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    business_name VARCHAR(255) NOT NULL,
    paybill_no VARCHAR(50) NOT NULL,
    network VARCHAR(50) DEFAULT 'Safaricom',
    lookup_id VARCHAR(100),
    webhook_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    configured_by_id UUID REFERENCES users(id),
    configured_by_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX idx_ncba_configs_merchant_id ON ncba_configs(merchant_id);
CREATE INDEX idx_ncba_configs_status ON ncba_configs(status);

CREATE TABLE ncba_stk_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_id VARCHAR(100) NOT NULL,
    reference_type VARCHAR(30) NOT NULL,
    merchant_id UUID NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    account_no VARCHAR(100),
    lookup_id VARCHAR(100),
    transaction_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result_desc VARCHAR(255),
    callback_received_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX idx_ncba_stk_reference_id ON ncba_stk_requests(reference_id);
CREATE INDEX idx_ncba_stk_status ON ncba_stk_requests(status);
CREATE INDEX idx_ncba_stk_transaction_id ON ncba_stk_requests(transaction_id);

-- Insert NCBA as a payment method
INSERT INTO payment_methods (code, name, description, active)
VALUES ('NCBA', 'NCBA', 'NCBA Mobile Paybill Payment', true)
ON CONFLICT (code) DO UPDATE SET name = 'NCBA', active = true;
