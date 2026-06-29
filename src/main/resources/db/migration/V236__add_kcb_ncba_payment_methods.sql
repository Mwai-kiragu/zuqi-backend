-- Add missing payment method codes so all PosPaymentMethod enum values map correctly
-- in createPaymentsForPosSale (maps by posPayment.getPaymentMethod().name() → payment_methods.code).
-- NCBA (code='NCBA') was already inserted in V208; KCB/CARD/BANK_TRANSFER were missing.
INSERT INTO payment_methods (code, name, description, active)
VALUES
    ('KCB',           'KCB',           'KCB STK push mobile payment', true),
    ('CARD',          'Card',          'Debit/credit card payment',   true),
    ('BANK_TRANSFER', 'Bank Transfer', 'Bank transfer payment',       true)
ON CONFLICT (code) DO NOTHING;
