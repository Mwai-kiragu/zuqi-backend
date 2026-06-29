-- Remove CARD and BANK_TRANSFER payment methods — not accepted channels.
-- Null out any payments referencing these before deleting (no such payments
-- should exist yet since they were just inserted in V236).
UPDATE payments SET payment_method_id = NULL
WHERE payment_method_id IN (
    SELECT id FROM payment_methods WHERE code IN ('CARD', 'BANK_TRANSFER')
);

DELETE FROM payment_methods WHERE code IN ('CARD', 'BANK_TRANSFER');
