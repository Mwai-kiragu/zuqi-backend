-- V184: Product Variations support
-- A product can have variants (e.g. Arsenal Home Jersey → Small, Medium, Large, XL)
-- Variants are stored as regular products with a parent_product_id FK

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS has_variants       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS parent_product_id  UUID REFERENCES products(id),
    ADD COLUMN IF NOT EXISTS variant_name       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS variant_attributes JSONB;

CREATE INDEX IF NOT EXISTS idx_products_parent ON products(parent_product_id);

-- Casbin: variant sub-endpoints
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'SUPER_ADMIN',       '/v1/products/.*/variants',   'GET'),
    ('p', 'SUPER_ADMIN',       '/v1/products/.*/variants',   'POST'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/products/.*/variants',   'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/products/.*/variants',   'POST'),
    ('p', 'MERCHANT_ADMIN',    '/v1/products/.*/variants',   'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/products/.*/variants',   'POST'),
    ('p', 'SALES_REP',         '/v1/products/.*/variants',   'GET'),
    ('p', 'WAREHOUSE_MANAGER', '/v1/products/.*/variants',   'GET')
ON CONFLICT DO NOTHING;
