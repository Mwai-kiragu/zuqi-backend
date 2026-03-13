-- Rename billing module key from 'merchants' to 'customers'
UPDATE billing_modules
SET module_key   = 'customers',
    display_name = 'Customers',
    description  = 'Customer management'
WHERE module_key = 'merchants';

-- Update billing_packages that reference 'merchants' in their modules column (stored as TEXT/JSON)
UPDATE billing_packages
SET modules = REPLACE(modules::text, '"merchants"', '"customers"')
WHERE modules::text LIKE '%"merchants"%';

-- Update custom_modules in distributor_subscriptions (TEXT column storing JSON array)
UPDATE distributor_subscriptions
SET custom_modules = REPLACE(custom_modules, '"merchants"', '"customers"')
WHERE custom_modules LIKE '%"merchants"%';
