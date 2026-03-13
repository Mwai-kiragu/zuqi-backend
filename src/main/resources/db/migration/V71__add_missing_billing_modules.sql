-- Add POS, Branches, Stock Transfers, Stock Takes billing modules
INSERT INTO billing_modules (module_key, display_name, description, sort_order) VALUES
('pos',            'Point of Sale',    'POS terminals, shifts and sales',               17),
('branches',       'Branches',         'Branch management and multi-location support',  18),
('stockTransfers', 'Stock Transfers',  'Inter-warehouse stock transfer management',     19),
('stockTakes',     'Stock Takes',      'Inventory stock take and reconciliation',       20)
ON CONFLICT (module_key) DO NOTHING;

-- Include POS, Branches, Stock Transfers, Stock Takes in GOLD package
UPDATE billing_packages
SET modules = '["dashboard","orders","merchants","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","gl","ai","credit","pos","branches","stockTransfers","stockTakes"]'
WHERE name = 'GOLD';

-- Include POS and Branches in SILVER package
UPDATE billing_packages
SET modules = '["dashboard","orders","merchants","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","pos","branches","stockTransfers","stockTakes"]'
WHERE name = 'SILVER';
