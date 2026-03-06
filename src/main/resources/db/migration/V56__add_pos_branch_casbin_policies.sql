-- Casbin policies for Branch Management
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
-- DISTRIBUTOR_ADMIN can manage branches
('p', 'DISTRIBUTOR_ADMIN', '/v1/branches', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/branches', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/branches/*', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/branches/*', 'PUT'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/branches/*/activate', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/branches/*/deactivate', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/branches/*/users', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/branches/*/users', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/branches/*/users/*', 'DELETE'),

-- WAREHOUSE_MANAGER can view branches
('p', 'WAREHOUSE_MANAGER', '/v1/branches', 'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/branches/*', 'GET'),

-- All authenticated users can switch branch
('p', 'DISTRIBUTOR_ADMIN', '/v1/auth/switch-branch', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/auth/switch-branch', 'POST'),
('p', 'SALES_REP', '/v1/auth/switch-branch', 'POST'),

-- POS policies
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/terminals', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/terminals', 'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/pos/terminals', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/shifts/*', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/shifts/*', 'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/pos/shifts/open', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/pos/shifts/*/close', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/pos/shifts/current', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/sales', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/sales', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/sales/*', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/sales/*', 'PUT'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/sales/*/payment', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/sales/*/complete', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/sales/*/cancel', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/sales/*/refund', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/pos/sales', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/pos/sales', 'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/pos/sales/*', 'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/pos/sales/*', 'PUT'),
('p', 'WAREHOUSE_MANAGER', '/v1/pos/sales/*/payment', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/pos/sales/*/complete', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/pos/sales/*/cancel', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/reports/summary', 'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/pos/reports/summary', 'GET'),

-- Stock Transfer policies
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/transfers', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/transfers', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/transfers/*', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/transfers/*/approve', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/transfers/*/cancel', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/transfers/*/receive', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/inventory/transfers', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/inventory/transfers', 'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/inventory/transfers/*', 'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/inventory/transfers/*/approve', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/inventory/transfers/*/receive', 'POST'),

-- Stock Take policies
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/stock-takes', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/stock-takes', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/stock-takes/*', 'GET'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/stock-takes/*', 'PUT'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/stock-takes/*/complete', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/stock-takes/*/approve', 'POST'),
('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/stock-takes/*/cancel', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/inventory/stock-takes', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/inventory/stock-takes', 'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/inventory/stock-takes/*', 'GET'),
('p', 'WAREHOUSE_MANAGER', '/v1/inventory/stock-takes/*/items/*', 'PUT'),
('p', 'WAREHOUSE_MANAGER', '/v1/inventory/stock-takes/*/complete', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/inventory/stock-takes/*/approve', 'POST'),
('p', 'WAREHOUSE_MANAGER', '/v1/inventory/stock-takes/*/cancel', 'POST')

ON CONFLICT DO NOTHING;
