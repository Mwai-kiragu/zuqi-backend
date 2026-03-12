-- V112: KCB STK push request tracking table
CREATE TABLE IF NOT EXISTS kcb_stk_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_id VARCHAR(100) NOT NULL,
    reference_type VARCHAR(30) NOT NULL,
    merchant_id UUID NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    zed_stk_id VARCHAR(100),
    stk_order_id VARCHAR(100),
    request_reference_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result_desc VARCHAR(255),
    callback_received_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX idx_kcb_stk_reference_id ON kcb_stk_requests(reference_id);
CREATE INDEX idx_kcb_stk_status ON kcb_stk_requests(status);
CREATE INDEX idx_kcb_stk_zed_id ON kcb_stk_requests(zed_stk_id);
