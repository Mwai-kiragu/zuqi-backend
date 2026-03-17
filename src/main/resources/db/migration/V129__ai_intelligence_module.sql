-- Update existing AI module: rename "AI Features" → "AI Intelligence"
-- and split into dedicated sub-modules for each AI capability

-- Update the parent AI module display name and description
UPDATE billing_modules
SET display_name = 'AI Intelligence',
    description  = 'AI-powered intelligence suite: anomaly detection, credit scoring, demand forecasting, route optimisation, recommendations, compliance reports, stockout predictions and system health monitoring'
WHERE module_key = 'ai';

-- Add sub-modules for each AI capability (only if they don't already exist)
INSERT INTO billing_modules (id, module_key, display_name, description, active, sort_order)
SELECT gen_random_uuid(), vals.module_key, vals.display_name, vals.description, true, vals.sort_order
FROM (VALUES
    ('ai_anomaly',         'Anomaly Detection',     'Real-time detection of unusual patterns in payments, inventory and transactions',         141),
    ('ai_credit',          'Credit Evaluations',    'ML-powered credit scoring and risk assessment for customers',                             142),
    ('ai_demand',          'Demand Forecasting',    'Predict future product demand and optimise inventory replenishment',                      143),
    ('ai_routing',         'Route Planner',         'AI-optimised delivery route planning for maximum fleet efficiency',                       144),
    ('ai_recommendations', 'Recommendations',       'Intelligent, data-driven business recommendations tailored to your operations',           145),
    ('ai_reports',         'AI Reports',            'Automated compliance reports and AI-generated business analytics',                        146),
    ('ai_predictions',     'Stockout Predictions',  'Anticipate stockouts before they happen with ML-based inventory predictions',             147),
    ('ai_system',          'AI System Health',      'Monitor AI model performance, data phases and system-wide health metrics',                148)
) AS vals(module_key, display_name, description, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM billing_modules WHERE module_key = vals.module_key
);
