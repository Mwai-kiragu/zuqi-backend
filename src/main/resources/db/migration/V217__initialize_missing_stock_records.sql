-- Backfill zero-quantity stock records for every active product × active warehouse combination
-- that doesn't already have a stock entry. This ensures all products appear in inventory.
INSERT INTO stock (id, warehouse_id, product_id, quantity, reserved_quantity, updated_at)
SELECT
    gen_random_uuid(),
    w.id,
    p.id,
    0,
    0,
    NOW()
FROM products p
JOIN warehouses w ON w.distributor_id = p.distributor_id
WHERE p.has_variants = false
  AND p.active    = true
  AND w.active    = true
  AND NOT EXISTS (
    SELECT 1 FROM stock s WHERE s.product_id = p.id AND s.warehouse_id = w.id
  );
