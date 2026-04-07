-- Add all missing modules to the permissions table so they appear in
-- GET /v1/permissions/modules and can be assigned to UserTypes.
-- Module names must match keys in the frontend backendModuleToFrontend map.

INSERT INTO permissions (name, description, module) VALUES
    -- Expenses
    ('expenses:read',   'View expenses',          'EXPENSES'),
    ('expenses:create', 'Create expenses',        'EXPENSES'),
    ('expenses:update', 'Update expenses',        'EXPENSES'),
    ('expenses:delete', 'Delete expenses',        'EXPENSES'),
    -- Procurement / Purchase Requisitions & Orders
    ('procurement:read',   'View procurement',    'PROCUREMENT'),
    ('procurement:create', 'Create procurement',  'PROCUREMENT'),
    ('procurement:update', 'Update procurement',  'PROCUREMENT'),
    ('procurement:delete', 'Delete procurement',  'PROCUREMENT'),
    -- Approvals
    ('approvals:read',    'View approvals',       'APPROVALS'),
    ('approvals:create',  'Submit approvals',     'APPROVALS'),
    ('approvals:update',  'Process approvals',    'APPROVALS'),
    -- Approval Thresholds
    ('approval_thresholds:read',   'View approval thresholds',   'APPROVAL_THRESHOLDS'),
    ('approval_thresholds:create', 'Create approval thresholds', 'APPROVAL_THRESHOLDS'),
    ('approval_thresholds:update', 'Update approval thresholds', 'APPROVAL_THRESHOLDS'),
    ('approval_thresholds:delete', 'Delete approval thresholds', 'APPROVAL_THRESHOLDS'),
    -- Billing
    ('billing:read',   'View billing & subscriptions', 'BILLING'),
    ('billing:update', 'Manage billing',               'BILLING'),
    -- Audit Logs
    ('audit_logs:read', 'View audit logs', 'AUDIT_LOGS'),
    -- Data Import
    ('data_import:create', 'Import data', 'DATA_IMPORT'),
    ('data_import:read',   'View import history', 'DATA_IMPORT'),
    -- Sales Team
    ('sales_team:read',   'View sales team',   'SALES_TEAM'),
    ('sales_team:create', 'Add sales members', 'SALES_TEAM'),
    ('sales_team:update', 'Manage sales team', 'SALES_TEAM'),
    -- General Ledger (alias — GL_REPORTS already exists)
    ('general_ledger:read', 'View general ledger', 'GENERAL_LEDGER'),
    -- AI modules
    ('ai:read',                    'Access AI assistant',             'AI'),
    ('ai_credit:read',             'AI credit risk analysis',         'AI_CREDIT'),
    ('ai_demand:read',             'AI demand forecasting',           'AI_DEMAND'),
    ('ai_routing:read',            'AI delivery routing',             'AI_ROUTING'),
    ('ai_anomaly:read',            'AI anomaly detection',            'AI_ANOMALY'),
    ('ai_recommendations:read',    'AI product recommendations',      'AI_RECOMMENDATIONS'),
    ('ai_reports:read',            'AI analytics reports',            'AI_REPORTS'),
    ('ai_system:read',             'AI system management',            'AI_SYSTEM'),
    ('ai_predictions:read',        'AI sales predictions',            'AI_PREDICTIONS'),
    ('ai_reorder:read',            'AI reorder suggestions',          'AI_REORDER'),
    ('ai_expiry:read',             'AI expiry tracking',              'AI_EXPIRY'),
    ('ai_customers:read',          'AI customer insights',            'AI_CUSTOMERS'),
    ('ai_suppliers:read',          'AI supplier analytics',           'AI_SUPPLIERS'),
    ('ai_pricing:read',            'AI pricing optimization',         'AI_PRICING'),
    ('ai_cashflow:read',           'AI cashflow forecasting',         'AI_CASHFLOW'),
    ('ai_driver_route:read',       'AI driver route optimization',    'AI_DRIVER_ROUTE')
ON CONFLICT (name) DO NOTHING;
