-- Backfill Payment records for post-completion POS settle payments.
-- settleBalance() was not creating Payment entities, so any pos_sale_payment
-- recorded after a sale's completed_at has no corresponding Payment row.
-- We identify them as: pos_sale_payments where created_at > sale.completed_at
-- (addPayment only runs on UNPAID sales, so any payment after completed_at
-- must have come from settleBalance).
INSERT INTO payments (
    payment_number,
    source_type,
    pos_sale_id,
    distributor_id,
    payment_method_id,
    amount,
    currency,
    status,
    payment_date,
    external_reference,
    notes,
    reconciled,
    created_at,
    updated_at
)
SELECT
    'PAY-BKFL-' || SUBSTRING(psp.id::text, 1, 8),
    'POS_SALE',
    psp.sale_id,
    b.distributor_id,
    pm.id,
    psp.amount,
    'KES',
    'COMPLETED',
    psp.created_at,
    COALESCE(psp.reference_number, ''),
    psp.notes,
    false,
    NOW(),
    NOW()
FROM pos_sale_payments psp
JOIN pos_sales ps ON ps.id = psp.sale_id
JOIN distributor_branches b ON b.id = ps.branch_id
LEFT JOIN payment_methods pm ON pm.code = psp.payment_method::text
WHERE ps.completed_at IS NOT NULL
  AND psp.created_at > ps.completed_at
  AND NOT EXISTS (
      SELECT 1 FROM payments p
      WHERE p.pos_sale_id = psp.sale_id
        AND p.amount = psp.amount
        AND ABS(EXTRACT(EPOCH FROM (p.created_at - psp.created_at))) < 10
  );
