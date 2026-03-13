-- M-Pesa Configurations: stores Paybill / Till credentials per Merchant
CREATE TABLE mpesa_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    business_name   VARCHAR(255) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,          -- PAYBILL | TILL
    business_short_code VARCHAR(20) NOT NULL,
    till_number     VARCHAR(20),
    store_number    VARCHAR(20),
    ho_number       VARCHAR(20),
    business_no     VARCHAR(20),
    account_reference VARCHAR(100),
    consumer_key    TEXT,
    consumer_secret TEXT,
    pass_key        TEXT,
    third_party_callback VARCHAR(500),
    external_id     VARCHAR(100),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    terms_accepted      BOOLEAN NOT NULL DEFAULT TRUE,
    configured_by_id    UUID REFERENCES users(id) ON DELETE SET NULL,
    configured_by_name  VARCHAR(255),     -- denormalized full name for display
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_mpesa_configs_merchant_id ON mpesa_configs(merchant_id);
CREATE INDEX idx_mpesa_configs_status ON mpesa_configs(status);

-- Tracks every STK push request for reconciliation
CREATE TABLE mpesa_stk_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_id        VARCHAR(100) NOT NULL,
    reference_type      VARCHAR(30) NOT NULL,       -- ORDER | POS_SALE | INVOICE
    merchant_id         UUID NOT NULL,
    distributor_id      UUID,
    phone_number        VARCHAR(20) NOT NULL,
    amount              NUMERIC(15,2) NOT NULL,
    checkout_request_id VARCHAR(100),
    merchant_request_id VARCHAR(100),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result_code         VARCHAR(10),
    result_desc         VARCHAR(255),
    mpesa_receipt_number VARCHAR(50),
    callback_received_at TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_mpesa_stk_checkout_id ON mpesa_stk_requests(checkout_request_id);
CREATE INDEX idx_mpesa_stk_reference_id ON mpesa_stk_requests(reference_id);
CREATE INDEX idx_mpesa_stk_status ON mpesa_stk_requests(status);
