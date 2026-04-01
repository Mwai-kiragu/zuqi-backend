-- Remove price list items that reference parent template products (has_variants = true)
-- These should never appear in a price list — only their variant children should
DELETE FROM price_list_items pli
USING products p
WHERE pli.product_id = p.id
  AND p.has_variants = true;

-- Backfill: add variant products (has_variants=false, parent_product_id IS NOT NULL)
-- to each distributor's default price list where they are not already present
INSERT INTO price_list_items (id, price_list_id, product_id, unit_price, discount_percent)
SELECT
    gen_random_uuid(),
    pl.id,
    p.id,
    COALESCE(p.unit_price, 0),
    0
FROM products p
JOIN price_lists pl
    ON pl.distributor_id = p.distributor_id
    AND pl.is_default = true
    AND pl.active = true
WHERE p.has_variants = false
  AND p.parent_product_id IS NOT NULL
  AND p.active = true
  AND NOT EXISTS (
      SELECT 1 FROM price_list_items pli2
      WHERE pli2.price_list_id = pl.id
        AND pli2.product_id = p.id
  );
