-- Add SALES_RETURNS to the permissions table so it appears in /v1/permissions/modules
-- and can be assigned to UserTypes
INSERT INTO permissions (name, description, module) VALUES
    ('sales_returns:read',   'View sales returns',   'SALES_RETURNS'),
    ('sales_returns:create', 'Create sales returns', 'SALES_RETURNS'),
    ('sales_returns:update', 'Update sales returns', 'SALES_RETURNS'),
    ('sales_returns:delete', 'Delete sales returns', 'SALES_RETURNS')
ON CONFLICT (name) DO NOTHING;
