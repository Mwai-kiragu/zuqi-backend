-- MERCHANT_ADMIN: full POS access (same as DISTRIBUTOR_ADMIN)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'MERCHANT_ADMIN', '/v1/pos/terminals',         'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/terminals',         'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/shifts/open',       'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/shifts/current',    'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/shifts/:id/close',  'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/sales',             'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/sales',             'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/sales/:id',         'GET'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/sales/:id',         'PUT'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/sales/:id/items',   'PUT'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/sales/:id/payment', 'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/sales/:id/complete','POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/sales/:id/cancel',  'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/sales/:id/refund',  'POST'),
    ('p', 'MERCHANT_ADMIN', '/v1/pos/reports/summary',   'GET'),
    -- SALES_REP: can create and manage own sales
    ('p', 'SALES_REP', '/v1/pos/shifts/current',         'GET'),
    ('p', 'SALES_REP', '/v1/pos/sales',                  'POST'),
    ('p', 'SALES_REP', '/v1/pos/sales',                  'GET'),
    ('p', 'SALES_REP', '/v1/pos/sales/:id',              'GET'),
    ('p', 'SALES_REP', '/v1/pos/sales/:id/items',        'PUT'),
    ('p', 'SALES_REP', '/v1/pos/sales/:id/payment',      'POST'),
    ('p', 'SALES_REP', '/v1/pos/sales/:id/complete',     'POST'),
    ('p', 'SALES_REP', '/v1/pos/sales/:id/cancel',       'POST')
ON CONFLICT DO NOTHING;
