-- Remove stock records that belong to parent-template products (has_variants = true).
-- These products are never sold directly; only their variant children should carry stock.
-- Keeping these records causes double-counting in stock valuation and inventory reports.
DELETE FROM stock
WHERE product_id IN (
    SELECT id FROM products WHERE has_variants = true
);
