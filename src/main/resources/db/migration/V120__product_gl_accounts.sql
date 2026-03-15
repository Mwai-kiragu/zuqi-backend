-- Allow each product to override the default GL accounts used during auto-posting.
-- When set, sales of this product post to the specified revenue/COGS account
-- instead of the distributor's default SALES_REVENUE / COST_OF_GOODS_SOLD account.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS revenue_account_id UUID REFERENCES gl_accounts(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS cogs_account_id    UUID REFERENCES gl_accounts(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_products_revenue_account ON products(revenue_account_id);
CREATE INDEX IF NOT EXISTS idx_products_cogs_account    ON products(cogs_account_id);
