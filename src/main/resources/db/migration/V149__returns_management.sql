-- ===========================================================================================
-- V149__returns_management.sql
-- Sales returns and purchase returns tables
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS sales_returns (
  id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  return_number VARCHAR(50)  UNIQUE NOT NULL,
  distributor_id UUID        NOT NULL REFERENCES distributors(id),
  order_id      UUID         REFERENCES orders(id),
  customer_id   UUID         REFERENCES customers(id),
  reason        TEXT,
  status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
  total_amount  NUMERIC(15,2) NOT NULL DEFAULT 0,
  refund_method VARCHAR(30),
  created_by_id UUID         REFERENCES users(id),
  created_at    TIMESTAMP    NOT NULL DEFAULT now(),
  updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sales_return_items (
  id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  return_id     UUID         NOT NULL REFERENCES sales_returns(id) ON DELETE CASCADE,
  product_id    UUID         NOT NULL REFERENCES products(id),
  quantity      NUMERIC(15,3) NOT NULL,
  unit_price    NUMERIC(15,2) NOT NULL,
  total_amount  NUMERIC(15,2) NOT NULL,
  reason        VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS purchase_returns (
  id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  return_number    VARCHAR(50)  UNIQUE NOT NULL,
  distributor_id   UUID         NOT NULL REFERENCES distributors(id),
  supplier_id      UUID         NOT NULL REFERENCES suppliers(id),
  supplier_bill_id UUID         REFERENCES supplier_bills(id),
  reason           TEXT,
  status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
  total_amount     NUMERIC(15,2) NOT NULL DEFAULT 0,
  created_by_id    UUID         REFERENCES users(id),
  created_at       TIMESTAMP    NOT NULL DEFAULT now(),
  updated_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS purchase_return_items (
  id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  return_id     UUID         NOT NULL REFERENCES purchase_returns(id) ON DELETE CASCADE,
  product_id    UUID         NOT NULL REFERENCES products(id),
  quantity      NUMERIC(15,3) NOT NULL,
  unit_price    NUMERIC(15,2) NOT NULL,
  total_amount  NUMERIC(15,2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sales_returns_distributor  ON sales_returns(distributor_id);
CREATE INDEX IF NOT EXISTS idx_sales_returns_order        ON sales_returns(order_id);
CREATE INDEX IF NOT EXISTS idx_sales_returns_customer     ON sales_returns(customer_id);
CREATE INDEX IF NOT EXISTS idx_purchase_returns_distributor ON purchase_returns(distributor_id);
CREATE INDEX IF NOT EXISTS idx_purchase_returns_supplier  ON purchase_returns(supplier_id);

-- Casbin policies for returns
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/sales-returns',       'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/sales-returns',       'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/sales-returns/.*',    'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/sales-returns/.*',    'PUT'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-returns',    'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-returns',    'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-returns/.*', 'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/purchase-returns/.*', 'PUT'),
  ('p', 'MERCHANT_ADMIN',    '/v1/sales-returns',       'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/sales-returns',       'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/sales-returns/.*',    'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/sales-returns/.*',    'PUT'),
  ('p', 'MERCHANT_ADMIN',    '/v1/purchase-returns',    'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/purchase-returns',    'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/purchase-returns/.*', 'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/purchase-returns/.*', 'PUT'),
  ('p', 'SUPER_ADMIN',       '/v1/sales-returns',       'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/sales-returns/.*',    'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/purchase-returns',    'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/purchase-returns/.*', 'GET')
ON CONFLICT DO NOTHING;
