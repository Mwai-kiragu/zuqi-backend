-- V132: Supplier Bills table + FundsTransfer extensions

-- Supplier Bills
CREATE TABLE IF NOT EXISTS supplier_bills (
  id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  bill_number       VARCHAR(50)  NOT NULL UNIQUE,
  distributor_id    UUID         NOT NULL REFERENCES distributors(id),
  supplier_id       UUID         NOT NULL REFERENCES suppliers(id),
  purchase_order_id UUID         REFERENCES purchase_orders(id),
  reference_number  VARCHAR(100),
  bill_date         DATE         NOT NULL,
  due_date          DATE,
  bill_type         VARCHAR(20)  NOT NULL DEFAULT 'SERVICES',
  description       TEXT,
  items             JSONB,
  total_amount      DECIMAL(15,2) NOT NULL,
  paid_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
  status            VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
  gl_posted         BOOLEAN      NOT NULL DEFAULT FALSE,
  notes             TEXT,
  created_by        UUID REFERENCES users(id),
  created_at        TIMESTAMPTZ  DEFAULT now(),
  updated_at        TIMESTAMPTZ  DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_supplier_bills_distributor ON supplier_bills(distributor_id);
CREATE INDEX IF NOT EXISTS idx_supplier_bills_supplier    ON supplier_bills(supplier_id);
CREATE INDEX IF NOT EXISTS idx_supplier_bills_status      ON supplier_bills(status);
CREATE INDEX IF NOT EXISTS idx_supplier_bills_due_date    ON supplier_bills(due_date);

-- Extend funds_transfers with supplier linkage
ALTER TABLE funds_transfers
  ADD COLUMN IF NOT EXISTS supplier_id      UUID REFERENCES suppliers(id),
  ADD COLUMN IF NOT EXISTS supplier_bill_id UUID REFERENCES supplier_bills(id);

CREATE INDEX IF NOT EXISTS idx_ft_supplier_id      ON funds_transfers(supplier_id);
CREATE INDEX IF NOT EXISTS idx_ft_supplier_bill_id ON funds_transfers(supplier_bill_id);

-- Casbin rules for supplier-bills (DISTRIBUTOR_ADMIN + FINANCE)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/supplier-bills',    'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/supplier-bills',    'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/supplier-bills/.*', 'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/supplier-bills/.*', 'PUT'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/supplier-bills/.*', 'POST'),
  ('p', 'FINANCE',           '/v1/supplier-bills',    'GET'),
  ('p', 'FINANCE',           '/v1/supplier-bills/.*', 'GET'),
  ('p', 'FINANCE',           '/v1/supplier-bills',    'POST'),
  ('p', 'FINANCE',           '/v1/supplier-bills/.*', 'POST')
ON CONFLICT DO NOTHING;

-- Casbin rules for new FT disburse endpoint
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/.*/disburse', 'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/.*/disburse', 'POST'),
  ('p', 'FINANCE',           '/v1/funds-transfers/.*/disburse', 'POST')
ON CONFLICT DO NOTHING;
