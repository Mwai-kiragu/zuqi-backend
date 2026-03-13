-- Add all_branches flag to products table
ALTER TABLE products ADD COLUMN all_branches BOOLEAN NOT NULL DEFAULT TRUE;

-- Create product_branch_prices table for branch-specific availability and price overrides
CREATE TABLE product_branch_prices (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id   UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    branch_id    UUID NOT NULL REFERENCES distributor_branches(id) ON DELETE CASCADE,
    unit_price   DECIMAL(15,2),       -- NULL = use product's default unit_price
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(product_id, branch_id)
);

CREATE INDEX idx_pbp_product_id ON product_branch_prices(product_id);
CREATE INDEX idx_pbp_branch_id ON product_branch_prices(branch_id);
