-- V227: Supplier confirmation tokens and response tracking for Purchase Orders

ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS supplier_response   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS supplier_notes      TEXT,
    ADD COLUMN IF NOT EXISTS supplier_responded_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS po_confirmation_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    po_id      UUID        NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    token      VARCHAR(64) NOT NULL UNIQUE,
    action     VARCHAR(20) NOT NULL,   -- CONFIRM | DECLINE | PARTIAL
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_po_conf_token ON po_confirmation_tokens(token);
CREATE INDEX IF NOT EXISTS idx_po_conf_po_id ON po_confirmation_tokens(po_id);

-- Extend PoStatus enum to include DECLINED
ALTER TABLE purchase_orders
    DROP CONSTRAINT IF EXISTS purchase_orders_status_check;

ALTER TABLE purchase_orders
    ALTER COLUMN status TYPE VARCHAR(25);
