-- Add Casbin policies for MERCHANT to access invoice endpoints
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'MERCHANT', '/v1/invoices', 'GET'),
    ('p', 'MERCHANT', '/v1/invoices/:id', 'GET'),
    ('p', 'MERCHANT', '/v1/invoices/merchant/:merchantId', 'GET'),
    ('p', 'MERCHANT', '/v1/invoices/order/:orderId', 'GET'),
    ('p', 'MERCHANT', '/v1/invoices/status-counts', 'GET'),
    ('p', 'MERCHANT', '/v1/invoices/count', 'GET'),
    ('p', 'MERCHANT', '/v1/invoices/:id/viewed', 'POST'),
    ('p', 'MERCHANT', '/v1/invoices/:id/payment', 'POST')
ON CONFLICT DO NOTHING;
