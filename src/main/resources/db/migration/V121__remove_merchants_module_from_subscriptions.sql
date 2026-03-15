-- Remove deprecated 'merchants' module key from all subscription custom_modules lists.
-- The 'merchants' concept was renamed to 'customers'; the module key is no longer valid.

UPDATE distributor_subscriptions
SET custom_modules = (
    SELECT COALESCE(jsonb_agg(elem)::text, '[]')
    FROM jsonb_array_elements_text(custom_modules::jsonb) AS elem
    WHERE elem <> 'merchants'
)
WHERE custom_modules IS NOT NULL
  AND custom_modules::jsonb @> '["merchants"]'::jsonb;
