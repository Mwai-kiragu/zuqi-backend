-- V106: Deactivate unsupported payment methods — only CASH and MPESA are active for now.
-- The rest remain in the table for future use but won't appear in active payment method lists.
UPDATE payment_methods SET active = false WHERE code IN ('KCB_TRANSFER', 'KCB_VOOMA', 'CREDIT');
