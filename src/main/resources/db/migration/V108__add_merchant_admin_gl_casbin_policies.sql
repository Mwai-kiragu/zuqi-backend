-- Add MERCHANT_ADMIN Casbin policies for all GL and Accounting endpoints
-- MERCHANT_ADMIN = brand owner, needs full visibility into their distributors' GL data

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES

-- GL Accounts
('p', 'MERCHANT_ADMIN', '/v1/gl/accounts',              '.*'),
('p', 'MERCHANT_ADMIN', '/v1/gl/accounts/:id',          '.*'),
('p', 'MERCHANT_ADMIN', '/v1/gl/accounts/:id/deactivate','POST'),
('p', 'MERCHANT_ADMIN', '/v1/gl/accounts/seed',         'POST'),

-- GL Periods
('p', 'MERCHANT_ADMIN', '/v1/gl/periods',               '.*'),
('p', 'MERCHANT_ADMIN', '/v1/gl/periods/:id',           '.*'),
('p', 'MERCHANT_ADMIN', '/v1/gl/periods/:id/close',     'POST'),
('p', 'MERCHANT_ADMIN', '/v1/gl/periods/:id/lock',      'POST'),
('p', 'MERCHANT_ADMIN', '/v1/gl/periods/:id/reopen',    'POST'),

-- Cost Centres
('p', 'MERCHANT_ADMIN', '/v1/gl/cost-centers',          '.*'),
('p', 'MERCHANT_ADMIN', '/v1/gl/cost-centers/:id',      '.*'),
('p', 'MERCHANT_ADMIN', '/v1/gl/cost-centers/:id/activate', 'POST'),

-- Journal Entries
('p', 'MERCHANT_ADMIN', '/v1/gl/journals',              '.*'),
('p', 'MERCHANT_ADMIN', '/v1/gl/journals/:id',          '.*'),
('p', 'MERCHANT_ADMIN', '/v1/gl/journals/:id/submit',   'POST'),
('p', 'MERCHANT_ADMIN', '/v1/gl/journals/:id/approve',  'POST'),
('p', 'MERCHANT_ADMIN', '/v1/gl/journals/:id/reject',   'POST'),
('p', 'MERCHANT_ADMIN', '/v1/gl/journals/:id/reverse',  'POST'),

-- Budgets
('p', 'MERCHANT_ADMIN', '/v1/gl/budgets',               '.*'),

-- GL Reports
('p', 'MERCHANT_ADMIN', '/v1/gl/reports/trial-balance', 'GET'),
('p', 'MERCHANT_ADMIN', '/v1/gl/reports/budget-variance','GET'),

-- Bank Reconciliation
('p', 'MERCHANT_ADMIN', '/v1/accounting/bank-reconciliations',      '.*'),
('p', 'MERCHANT_ADMIN', '/v1/accounting/bank-reconciliations/:id',  '.*'),
('p', 'MERCHANT_ADMIN', '/v1/accounting/bank-reconciliations/:id/items', '.*'),
('p', 'MERCHANT_ADMIN', '/v1/accounting/bank-reconciliations/:id/reconcile', 'POST'),

-- Tax Rates
('p', 'MERCHANT_ADMIN', '/v1/accounting/tax-rates',     '.*'),
('p', 'MERCHANT_ADMIN', '/v1/accounting/tax-rates/:id', '.*')

ON CONFLICT DO NOTHING;
