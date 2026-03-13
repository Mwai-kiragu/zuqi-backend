-- V109: Consolidate KCB methods into a single KCB entry (Mobile to Bank Transfer)
UPDATE payment_methods
SET code = 'KCB', name = 'KCB', description = 'KCB Mobile to Bank Transfer', active = true
WHERE code = 'KCB_TRANSFER';
