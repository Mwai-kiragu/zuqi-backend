-- Goods Receipt Notes (GRN) — records physical receipt of goods against a Purchase Order
CREATE TABLE IF NOT EXISTS goods_receipt_notes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grn_number      VARCHAR(30)  NOT NULL UNIQUE,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    supplier_id     UUID NOT NULL REFERENCES suppliers(id),
    distributor_id  UUID NOT NULL,
    warehouse_id    UUID NOT NULL REFERENCES warehouses(id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    delivery_note_number VARCHAR(100),
    notes           TEXT,
    items           JSONB        NOT NULL DEFAULT '[]',
    total_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,
    confirmed_by    UUID REFERENCES users(id),
    confirmed_at    TIMESTAMP,
    rejected_by     UUID REFERENCES users(id),
    rejected_at     TIMESTAMP,
    rejection_reason TEXT,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_grn_po        ON goods_receipt_notes(purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_grn_supplier  ON goods_receipt_notes(supplier_id);
CREATE INDEX IF NOT EXISTS idx_grn_distributor ON goods_receipt_notes(distributor_id);
CREATE INDEX IF NOT EXISTS idx_grn_status    ON goods_receipt_notes(status);

-- Casbin: access rules for GRN endpoints
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'SUPER_ADMIN',        '/v1/grns',          'GET'),
  ('p', 'SUPER_ADMIN',        '/v1/grns/.*',        'GET'),
  ('p', 'SUPER_ADMIN',        '/v1/grns',          'POST'),
  ('p', 'SUPER_ADMIN',        '/v1/grns/.*',        'PUT'),
  ('p', 'MERCHANT_ADMIN',     '/v1/grns',          'GET'),
  ('p', 'MERCHANT_ADMIN',     '/v1/grns/.*',        'GET'),
  ('p', 'MERCHANT_ADMIN',     '/v1/grns',          'POST'),
  ('p', 'MERCHANT_ADMIN',     '/v1/grns/.*',        'PUT'),
  ('p', 'DISTRIBUTOR_ADMIN',  '/v1/grns',          'GET'),
  ('p', 'DISTRIBUTOR_ADMIN',  '/v1/grns/.*',        'GET'),
  ('p', 'DISTRIBUTOR_ADMIN',  '/v1/grns',          'POST'),
  ('p', 'DISTRIBUTOR_ADMIN',  '/v1/grns/.*',        'PUT'),
  ('p', 'WAREHOUSE_MANAGER',  '/v1/grns',          'GET'),
  ('p', 'WAREHOUSE_MANAGER',  '/v1/grns/.*',        'GET'),
  ('p', 'WAREHOUSE_MANAGER',  '/v1/grns',          'POST'),
  ('p', 'WAREHOUSE_MANAGER',  '/v1/grns/.*',        'PUT'),
  ('p', 'FINANCE',            '/v1/grns',          'GET'),
  ('p', 'FINANCE',            '/v1/grns/.*',        'GET')
ON CONFLICT DO NOTHING;
