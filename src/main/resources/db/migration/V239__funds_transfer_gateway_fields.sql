-- V239: Add payment gateway tracking fields to funds_transfers
ALTER TABLE funds_transfers
    ADD COLUMN IF NOT EXISTS gateway_transaction_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS gateway_status         VARCHAR(30),
    ADD COLUMN IF NOT EXISTS gateway_response       TEXT;

CREATE INDEX IF NOT EXISTS idx_ft_gateway_txn ON funds_transfers (gateway_transaction_id)
    WHERE gateway_transaction_id IS NOT NULL;
