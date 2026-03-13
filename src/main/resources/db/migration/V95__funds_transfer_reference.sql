-- Link funds transfers to source documents (expense or purchase order)
ALTER TABLE funds_transfers
    ADD COLUMN reference_type VARCHAR(30),   -- 'EXPENSE' | 'PURCHASE_ORDER'
    ADD COLUMN reference_id   UUID;

CREATE INDEX idx_funds_transfers_ref ON funds_transfers(reference_type, reference_id)
    WHERE reference_id IS NOT NULL;
