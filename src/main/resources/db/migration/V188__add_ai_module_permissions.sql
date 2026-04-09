-- ===========================================================================================
-- V188: Add AI sub-module permissions to the permissions table
-- Part of Phase 7 — Granular AI access control via UserType/UserGroup
-- Author: Angela Kalelwa
-- Date: 2026-04-01
--
-- Without this migration, AI sub-modules (AI_CREDIT, AI_DEMAND, etc.) do not appear
-- in /v1/permissions/modules and therefore cannot be assigned in the UserType form.
-- ===========================================================================================

-- AI Assistant (chat widget — base access)
INSERT INTO permissions (name, description, module) VALUES
    ('ai:read',   'Access AI assistant and overview dashboard', 'AI')
ON CONFLICT (name) DO NOTHING;

-- AI Credit Evaluations
INSERT INTO permissions (name, description, module) VALUES
    ('ai_credit:read',   'View credit evaluations and scores',        'AI_CREDIT'),
    ('ai_credit:create', 'Trigger credit evaluation for a merchant',  'AI_CREDIT')
ON CONFLICT (name) DO NOTHING;

-- AI Demand Forecasting & Order Suggestions
INSERT INTO permissions (name, description, module) VALUES
    ('ai_demand:read',   'View demand forecasts and order suggestions', 'AI_DEMAND'),
    ('ai_demand:create', 'Trigger demand model retraining',             'AI_DEMAND')
ON CONFLICT (name) DO NOTHING;

-- AI Route Optimization
INSERT INTO permissions (name, description, module) VALUES
    ('ai_routing:read',   'View and plan delivery routes',    'AI_ROUTING'),
    ('ai_routing:create', 'Trigger route optimization',       'AI_ROUTING')
ON CONFLICT (name) DO NOTHING;

-- AI Driver Route View (driver-specific route view)
INSERT INTO permissions (name, description, module) VALUES
    ('ai_driver_route:read', 'View assigned delivery route (driver view)', 'AI_DRIVER_ROUTE')
ON CONFLICT (name) DO NOTHING;

-- AI Anomaly Detection
INSERT INTO permissions (name, description, module) VALUES
    ('ai_anomaly:read',   'View anomaly alerts (shrinkage, payment anomalies)', 'AI_ANOMALY'),
    ('ai_anomaly:update', 'Acknowledge and resolve anomaly alerts',             'AI_ANOMALY')
ON CONFLICT (name) DO NOTHING;

-- AI Recommendations
INSERT INTO permissions (name, description, module) VALUES
    ('ai_recommendations:read', 'View AI-generated operational recommendations', 'AI_RECOMMENDATIONS')
ON CONFLICT (name) DO NOTHING;

-- AI Compliance Reports
INSERT INTO permissions (name, description, module) VALUES
    ('ai_reports:read',   'View AI-generated compliance and narrative reports', 'AI_REPORTS'),
    ('ai_reports:create', 'Generate AI compliance reports',                     'AI_REPORTS')
ON CONFLICT (name) DO NOTHING;

-- AI System Health (admin only)
INSERT INTO permissions (name, description, module) VALUES
    ('ai_system:read', 'View AI model registry, health, and drift metrics', 'AI_SYSTEM')
ON CONFLICT (name) DO NOTHING;

-- AI Stockout Predictions
INSERT INTO permissions (name, description, module) VALUES
    ('ai_predictions:read', 'View ML stockout predictions and at-risk products', 'AI_PREDICTIONS')
ON CONFLICT (name) DO NOTHING;

-- AI Reorder Suggestions
INSERT INTO permissions (name, description, module) VALUES
    ('ai_reorder:read',   'View AI reorder suggestions',    'AI_REORDER'),
    ('ai_reorder:update', 'Approve or dismiss reorder suggestions', 'AI_REORDER')
ON CONFLICT (name) DO NOTHING;

-- AI Expiry Risk
INSERT INTO permissions (name, description, module) VALUES
    ('ai_expiry:read', 'View product expiry risk scores', 'AI_EXPIRY')
ON CONFLICT (name) DO NOTHING;

-- AI Customer Analytics
INSERT INTO permissions (name, description, module) VALUES
    ('ai_customers:read', 'View customer segments, churn risk, and health scores', 'AI_CUSTOMERS')
ON CONFLICT (name) DO NOTHING;

-- AI Supplier Intelligence
INSERT INTO permissions (name, description, module) VALUES
    ('ai_suppliers:read', 'View AI supplier risk scores and price trend analysis', 'AI_SUPPLIERS')
ON CONFLICT (name) DO NOTHING;

-- AI Pricing Recommendations
INSERT INTO permissions (name, description, module) VALUES
    ('ai_pricing:read', 'View AI-generated pricing recommendations', 'AI_PRICING')
ON CONFLICT (name) DO NOTHING;

-- AI Cash Flow Forecast
INSERT INTO permissions (name, description, module) VALUES
    ('ai_cashflow:read', 'View AI cash flow forecasts and shortfall predictions', 'AI_CASHFLOW')
ON CONFLICT (name) DO NOTHING;

-- ===========================================================================================
-- Assign AI permissions to roles
-- ===========================================================================================

-- SUPER_ADMIN, MERCHANT_ADMIN, DISTRIBUTOR_ADMIN — full AI access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.module IN (
    'AI', 'AI_CREDIT', 'AI_DEMAND', 'AI_ROUTING', 'AI_DRIVER_ROUTE',
    'AI_ANOMALY', 'AI_RECOMMENDATIONS', 'AI_REPORTS', 'AI_SYSTEM',
    'AI_PREDICTIONS', 'AI_REORDER', 'AI_EXPIRY',
    'AI_CUSTOMERS', 'AI_SUPPLIERS', 'AI_PRICING', 'AI_CASHFLOW'
)
AND r.name IN ('SUPER_ADMIN', 'MERCHANT_ADMIN', 'DISTRIBUTOR_ADMIN')
ON CONFLICT DO NOTHING;

-- FINANCE — financial AI modules
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.module IN ('AI', 'AI_DEMAND', 'AI_ANOMALY', 'AI_CREDIT',
                   'AI_CUSTOMERS', 'AI_SUPPLIERS', 'AI_PRICING',
                   'AI_CASHFLOW', 'AI_REPORTS')
AND r.name = 'FINANCE'
ON CONFLICT DO NOTHING;

-- WAREHOUSE_MANAGER — inventory AI modules
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.module IN ('AI', 'AI_DEMAND', 'AI_ANOMALY', 'AI_PREDICTIONS',
                   'AI_REORDER', 'AI_EXPIRY', 'AI_REPORTS')
AND r.name = 'WAREHOUSE_MANAGER'
ON CONFLICT DO NOTHING;

-- SALES_REP — customer-facing AI modules
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.module IN ('AI', 'AI_DEMAND', 'AI_CUSTOMERS')
AND r.name = 'SALES_REP'
ON CONFLICT DO NOTHING;

-- DRIVER — route view only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.module IN ('AI', 'AI_DRIVER_ROUTE')
AND r.name = 'DRIVER'
ON CONFLICT DO NOTHING;

-- MERCHANT — own order suggestions and credit score
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.module IN ('AI', 'AI_DEMAND', 'AI_CREDIT')
AND r.name = 'MERCHANT'
ON CONFLICT DO NOTHING;
