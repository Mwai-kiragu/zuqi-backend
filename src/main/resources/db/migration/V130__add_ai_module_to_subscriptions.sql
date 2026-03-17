-- Add "ai" module to the specific CUSTOM subscription for distributor 076ea82b-1d46-44be-b816-9aaf04428ab9
-- Also add to ALL other CUSTOM subscriptions that are missing it (future-proof)

UPDATE distributor_subscriptions
SET custom_modules = (custom_modules::jsonb || '["ai"]'::jsonb)::text
WHERE package_type = 'CUSTOM'
  AND custom_modules IS NOT NULL
  AND NOT (custom_modules::jsonb @> '["ai"]'::jsonb);

-- Also ensure all standard package definitions (billing_packages) include "ai"
UPDATE billing_packages
SET modules = (modules::jsonb || '["ai"]'::jsonb)::text
WHERE modules IS NOT NULL
  AND NOT (modules::jsonb @> '["ai"]'::jsonb);
