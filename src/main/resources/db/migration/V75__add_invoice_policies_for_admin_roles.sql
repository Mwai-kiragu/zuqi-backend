-- Casbin policies for DISTRIBUTOR_ADMIN and MERCHANT_ADMIN to access invoice endpoints

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES

-- DISTRIBUTOR_ADMIN: full invoice management
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/:id', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/number/:id', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/order/:id', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/pos-sale/:id', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/distributor/:id', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/merchant/:id', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/overdue', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/count', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/status-counts', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/:id/send', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/:id/viewed', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/:id/payment', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/invoices/:id/cancel', 'POST'),

-- MERCHANT_ADMIN: full invoice management (read-only on key ops)
('p', 'MERCHANT_ADMIN', '/v1/invoices', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/:id', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/number/:id', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/order/:id', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/pos-sale/:id', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/distributor/:id', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/merchant/:id', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/overdue', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/count', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/status-counts', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/:id/send', 'POST'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/:id/viewed', 'POST'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/:id/payment', 'POST'),
('p', 'MERCHANT_ADMIN', '/v1/invoices/:id/cancel', 'POST')

ON CONFLICT DO NOTHING;
