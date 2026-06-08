-- Link purchase returns to the GRN from which goods were originally received
ALTER TABLE purchase_returns
    ADD COLUMN IF NOT EXISTS grn_id UUID REFERENCES goods_receipt_notes(id);

CREATE INDEX IF NOT EXISTS idx_purchase_returns_grn ON purchase_returns(grn_id);
