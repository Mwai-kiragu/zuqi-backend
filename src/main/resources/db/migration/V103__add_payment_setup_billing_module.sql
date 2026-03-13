-- Add paymentSetup as a standalone billing module
INSERT INTO billing_modules (module_key, display_name, description, sort_order) VALUES
  ('paymentSetup', 'Payment Setup', 'M-Pesa and cash payment method configuration', 26)
ON CONFLICT (module_key) DO NOTHING;

-- Add paymentSetup to GOLD package
UPDATE billing_packages
SET modules = '["dashboard","orders","merchants","customers","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","gl","ai","credit","pos","branches","stockTransfers","stockTakes","bankReconciliation","taxRates","expenses","fundsTransfer","warehouses","paymentSetup"]'
WHERE name = 'GOLD';

-- Add paymentSetup to SILVER package
UPDATE billing_packages
SET modules = '["dashboard","orders","merchants","customers","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","pos","branches","stockTransfers","stockTakes","bankReconciliation","taxRates","expenses","fundsTransfer","warehouses","paymentSetup"]'
WHERE name = 'SILVER';
