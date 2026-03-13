-- Add bankReconciliation, taxRates, and expenses to billing_modules
INSERT INTO billing_modules (module_key, display_name, description, sort_order) VALUES
('bankReconciliation', 'Bank Reconciliation', 'Bank statement reconciliation',                  21),
('taxRates',           'Tax Rates',           'Tax rate configuration and management',          22),
('expenses',           'Expenses',            'Business expense tracking and approval workflow', 23)
ON CONFLICT (module_key) DO NOTHING;

-- Add new modules to GOLD package
UPDATE billing_packages
SET modules = '["dashboard","orders","merchants","customers","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","gl","ai","credit","pos","branches","stockTransfers","stockTakes","bankReconciliation","taxRates","expenses"]'
WHERE name = 'GOLD';

-- Add bankReconciliation, taxRates, expenses to SILVER package
UPDATE billing_packages
SET modules = '["dashboard","orders","merchants","customers","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","pos","branches","stockTransfers","stockTakes","bankReconciliation","taxRates","expenses"]'
WHERE name = 'SILVER';
