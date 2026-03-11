-- Add warehouses as a standalone billing module
INSERT INTO billing_modules (module_key, display_name, description, sort_order) VALUES
  ('warehouses', 'Warehouses', 'Warehouse creation and management', 25)
ON CONFLICT (module_key) DO NOTHING;

-- Add warehouses to GOLD package
UPDATE billing_packages
SET modules = '["dashboard","orders","merchants","customers","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","gl","ai","credit","pos","branches","stockTransfers","stockTakes","bankReconciliation","taxRates","expenses","fundsTransfer","warehouses"]'
WHERE name = 'GOLD';

-- Add warehouses to SILVER package
UPDATE billing_packages
SET modules = '["dashboard","orders","merchants","customers","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","pos","branches","stockTransfers","stockTakes","bankReconciliation","taxRates","expenses","fundsTransfer","warehouses"]'
WHERE name = 'SILVER';
