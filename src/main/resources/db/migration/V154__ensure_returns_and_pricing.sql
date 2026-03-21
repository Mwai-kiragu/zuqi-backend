-- ===========================================================================================
-- V154__ensure_returns_and_pricing.sql
-- Safety migration: ensures returns and pricing tables exist.
-- V149 (returns) and V150 (pricing) are marked as success in flyway_schema_history
-- but the tables may be missing if the DB was restored without them.
-- All statements use IF NOT EXISTS — fully idempotent.
-- ===========================================================================================

-- ── Returns (V149) ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS sales_returns (
  id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  return_number  VARCHAR(50)   UNIQUE NOT NULL,
  distributor_id UUID          NOT NULL REFERENCES distributors(id),
  order_id       UUID          REFERENCES orders(id),
  customer_id    UUID          REFERENCES customers(id),
  reason         TEXT,
  status         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
  total_amount   NUMERIC(15,2) NOT NULL DEFAULT 0,
  refund_method  VARCHAR(30),
  created_by_id  UUID          REFERENCES users(id),
  created_at     TIMESTAMP     NOT NULL DEFAULT now(),
  updated_at     TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sales_return_items (
  id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  return_id    UUID          NOT NULL REFERENCES sales_returns(id) ON DELETE CASCADE,
  product_id   UUID          NOT NULL REFERENCES products(id),
  quantity     NUMERIC(15,3) NOT NULL,
  unit_price   NUMERIC(15,2) NOT NULL,
  total_amount NUMERIC(15,2) NOT NULL,
  reason       VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS purchase_returns (
  id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  return_number    VARCHAR(50)   UNIQUE NOT NULL,
  distributor_id   UUID          NOT NULL REFERENCES distributors(id),
  supplier_id      UUID          NOT NULL REFERENCES suppliers(id),
  supplier_bill_id UUID          REFERENCES supplier_bills(id),
  reason           TEXT,
  status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
  total_amount     NUMERIC(15,2) NOT NULL DEFAULT 0,
  created_by_id    UUID          REFERENCES users(id),
  created_at       TIMESTAMP     NOT NULL DEFAULT now(),
  updated_at       TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS purchase_return_items (
  id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  return_id    UUID          NOT NULL REFERENCES purchase_returns(id) ON DELETE CASCADE,
  product_id   UUID          NOT NULL REFERENCES products(id),
  quantity     NUMERIC(15,3) NOT NULL,
  unit_price   NUMERIC(15,2) NOT NULL,
  total_amount NUMERIC(15,2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sales_returns_distributor    ON sales_returns(distributor_id);
CREATE INDEX IF NOT EXISTS idx_sales_returns_order          ON sales_returns(order_id);
CREATE INDEX IF NOT EXISTS idx_sales_returns_customer       ON sales_returns(customer_id);
CREATE INDEX IF NOT EXISTS idx_purchase_returns_distributor ON purchase_returns(distributor_id);
CREATE INDEX IF NOT EXISTS idx_purchase_returns_supplier    ON purchase_returns(supplier_id);

-- ── Pricing (V150) ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS price_lists (
  id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  distributor_id UUID         NOT NULL REFERENCES distributors(id),
  name           VARCHAR(100) NOT NULL,
  description    TEXT,
  is_default     BOOLEAN      NOT NULL DEFAULT false,
  active         BOOLEAN      NOT NULL DEFAULT true,
  valid_from     DATE,
  valid_to       DATE,
  created_at     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS price_list_items (
  id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  price_list_id    UUID          NOT NULL REFERENCES price_lists(id) ON DELETE CASCADE,
  product_id       UUID          NOT NULL REFERENCES products(id),
  unit_price       NUMERIC(15,2) NOT NULL,
  discount_percent NUMERIC(5,2)  NOT NULL DEFAULT 0,
  UNIQUE(price_list_id, product_id)
);

ALTER TABLE customers ADD COLUMN IF NOT EXISTS price_list_id UUID REFERENCES price_lists(id);

CREATE TABLE IF NOT EXISTS promotions (
  id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  distributor_id   UUID          NOT NULL REFERENCES distributors(id),
  name             VARCHAR(100)  NOT NULL,
  promotion_type   VARCHAR(30)   NOT NULL,
  discount_value   NUMERIC(10,2),
  min_order_amount NUMERIC(15,2),
  product_id       UUID          REFERENCES products(id),
  category_id      INT           REFERENCES product_categories(id),
  valid_from       DATE          NOT NULL,
  valid_to         DATE          NOT NULL,
  active           BOOLEAN       NOT NULL DEFAULT true,
  created_at       TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_price_lists_distributor ON price_lists(distributor_id);
CREATE INDEX IF NOT EXISTS idx_price_list_items_list   ON price_list_items(price_list_id);
CREATE INDEX IF NOT EXISTS idx_promotions_distributor  ON promotions(distributor_id);
CREATE INDEX IF NOT EXISTS idx_promotions_product      ON promotions(product_id);
CREATE INDEX IF NOT EXISTS idx_promotions_dates        ON promotions(valid_from, valid_to);

-- Casbin policies (ON CONFLICT DO NOTHING — idempotent)
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
  ('p', 'SUPER_ADMIN',       '/v1/purchase-returns/.*', 'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/price-lists',         'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/price-lists',         'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/price-lists/.*',      'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/price-lists/.*',      'PUT'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/price-lists/.*',      'DELETE'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/promotions',          'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/promotions',          'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/promotions/.*',       'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/promotions/.*',       'PUT'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/promotions/.*',       'DELETE'),
  ('p', 'MERCHANT_ADMIN',    '/v1/price-lists',         'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/price-lists',         'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/price-lists/.*',      'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/price-lists/.*',      'PUT'),
  ('p', 'MERCHANT_ADMIN',    '/v1/price-lists/.*',      'DELETE'),
  ('p', 'MERCHANT_ADMIN',    '/v1/promotions',          'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/promotions',          'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/promotions/.*',       'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/promotions/.*',       'PUT'),
  ('p', 'MERCHANT_ADMIN',    '/v1/promotions/.*',       'DELETE'),
  ('p', 'SUPER_ADMIN',       '/v1/price-lists',         'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/price-lists/.*',      'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/promotions',          'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/promotions/.*',       'GET')
ON CONFLICT DO NOTHING;
