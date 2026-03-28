-- Add CRM as a billing module
INSERT INTO billing_modules (module_key, display_name, description, sort_order)
VALUES ('crm', 'CRM', 'Customer relationship management — log interactions, track follow-ups', 27)
ON CONFLICT (module_key) DO NOTHING;

-- Add CRM to permissions table so it appears in the User Type permission matrix
INSERT INTO permissions (name, description, module)
VALUES
    ('crm:read',   'View customer interactions',   'CRM'),
    ('crm:create', 'Log new interactions',         'CRM'),
    ('crm:update', 'Edit interactions',            'CRM'),
    ('crm:delete', 'Delete interactions',          'CRM')
ON CONFLICT (name) DO NOTHING;

-- Add CRM to SILVER package (idempotent JSON update)
UPDATE billing_packages
SET modules = (
    SELECT jsonb_agg(elem ORDER BY elem)
    FROM (
        SELECT DISTINCT jsonb_array_elements_text(modules::jsonb) AS elem
        UNION SELECT 'crm'
    ) t
)::text
WHERE name = 'SILVER'
  AND NOT (modules::jsonb @> '["crm"]');

-- Add CRM to GOLD package (idempotent JSON update)
UPDATE billing_packages
SET modules = (
    SELECT jsonb_agg(elem ORDER BY elem)
    FROM (
        SELECT DISTINCT jsonb_array_elements_text(modules::jsonb) AS elem
        UNION SELECT 'crm'
    ) t
)::text
WHERE name = 'GOLD'
  AND NOT (modules::jsonb @> '["crm"]');
