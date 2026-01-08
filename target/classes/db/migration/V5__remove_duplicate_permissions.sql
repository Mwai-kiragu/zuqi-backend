-- Remove old V1 permissions that are duplicated by V3 permissions
-- First remove from role_permissions to avoid foreign key constraint
DELETE FROM role_permissions WHERE permission_id IN (
    SELECT id FROM permissions WHERE name IN (
        'user:read', 'user:write', 'user:delete',
        'order:read', 'order:write', 'order:delete',
        'payment:read', 'payment:write', 'payment:reconcile',
        'inventory:read', 'inventory:write',
        'credit:read', 'credit:write', 'credit:approve',
        'merchant:read', 'merchant:write',
        'report:read', 'report:export',
        'settings:read', 'settings:write'
    )
);

-- Now delete the old permissions
DELETE FROM permissions WHERE name IN (
    'user:read', 'user:write', 'user:delete',
    'order:read', 'order:write', 'order:delete',
    'payment:read', 'payment:write', 'payment:reconcile',
    'inventory:read', 'inventory:write',
    'credit:read', 'credit:write', 'credit:approve',
    'merchant:read', 'merchant:write',
    'report:read', 'report:export',
    'settings:read', 'settings:write'
);
