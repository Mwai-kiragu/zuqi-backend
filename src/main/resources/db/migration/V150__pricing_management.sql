-- ===========================================================================================
-- V150__pricing_management.sql
-- Price lists, price list items, and promotions
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS price_lists (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  distributor_id UUID NOT NULL REFERENCES distributors(id),
  name          VARCHAR(100) NOT NULL,
  description   TEXT,
  is_default    BOOLEAN NOT NULL DEFAULT false,
  active        BOOLEAN NOT NULL DEFAULT true,
  valid_from    DATE,
  valid_to      DATE,
  created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS price_list_items (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  price_list_id UUID NOT NULL REFERENCES price_lists(id) ON DELETE CASCADE,
  product_id    UUID NOT NULL REFERENCES products(id),
  unit_price    NUMERIC(15,2) NOT NULL,
  discount_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
  UNIQUE(price_list_id, product_id)
);

ALTER TABLE customers ADD COLUMN IF NOT EXISTS price_list_id UUID REFERENCES price_lists(id);

CREATE TABLE IF NOT EXISTS promotions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  distributor_id  UUID NOT NULL REFERENCES distributors(id),
  name            VARCHAR(100) NOT NULL,
  promotion_type  VARCHAR(30) NOT NULL,
  discount_value  NUMERIC(10,2),
  min_order_amount NUMERIC(15,2),
  product_id      UUID REFERENCES products(id),
  category_id     INT  REFERENCES product_categories(id),
  valid_from      DATE NOT NULL,
  valid_to        DATE NOT NULL,
  active          BOOLEAN NOT NULL DEFAULT true,
  created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_price_lists_distributor  ON price_lists(distributor_id);
CREATE INDEX IF NOT EXISTS idx_price_list_items_list    ON price_list_items(price_list_id);
CREATE INDEX IF NOT EXISTS idx_promotions_distributor   ON promotions(distributor_id);
CREATE INDEX IF NOT EXISTS idx_promotions_product       ON promotions(product_id);
CREATE INDEX IF NOT EXISTS idx_promotions_dates         ON promotions(valid_from, valid_to);

-- Casbin policies
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/price-lists',       'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/price-lists',       'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/price-lists/.*',    'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/price-lists/.*',    'PUT'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/price-lists/.*',    'DELETE'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/promotions',        'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/promotions',        'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/promotions/.*',     'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/promotions/.*',     'PUT'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/promotions/.*',     'DELETE'),
  ('p', 'MERCHANT_ADMIN',    '/v1/price-lists',       'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/price-lists',       'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/price-lists/.*',    'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/price-lists/.*',    'PUT'),
  ('p', 'MERCHANT_ADMIN',    '/v1/price-lists/.*',    'DELETE'),
  ('p', 'MERCHANT_ADMIN',    '/v1/promotions',        'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/promotions',        'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/promotions/.*',     'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/promotions/.*',     'PUT'),
  ('p', 'MERCHANT_ADMIN',    '/v1/promotions/.*',     'DELETE'),
  ('p', 'SUPER_ADMIN',       '/v1/price-lists',       'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/price-lists/.*',    'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/promotions',        'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/promotions/.*',     'GET')
ON CONFLICT DO NOTHING;
