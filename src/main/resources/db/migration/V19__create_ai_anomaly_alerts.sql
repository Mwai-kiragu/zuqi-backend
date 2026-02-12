-- AI Anomaly Alerts
-- Centralized alert management for anomaly detection across inventory, payments, and data quality
-- Part of Phase 4: Anomaly Detection Module

CREATE TABLE IF NOT EXISTS ai_anomaly_alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_type      VARCHAR(50) NOT NULL,
    severity        VARCHAR(20) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    distributor_id  UUID NOT NULL,
    anomaly_score   DOUBLE PRECISION,
    description     TEXT NOT NULL,
    context         JSONB,
    status          VARCHAR(20) DEFAULT 'OPEN',
    resolved_by     VARCHAR(100),
    resolved_at     TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_anomaly_distributor FOREIGN KEY (distributor_id)
        REFERENCES distributors(id) ON DELETE CASCADE,
    CONSTRAINT chk_alert_type CHECK (alert_type IN (
        'shrinkage', 'payment_anomaly', 'payment_distress', 'data_quality',
        'fraud_detection', 'unusual_order', 'price_anomaly'
    )),
    CONSTRAINT chk_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED'))
);

-- Comments for documentation
COMMENT ON TABLE ai_anomaly_alerts IS 'Centralized anomaly detection alerts with workflow management';
COMMENT ON COLUMN ai_anomaly_alerts.alert_type IS 'Type of anomaly: shrinkage, payment_anomaly, data_quality, etc.';
COMMENT ON COLUMN ai_anomaly_alerts.entity_type IS 'What entity triggered alert: warehouse_sku, payment, order, merchant';
COMMENT ON COLUMN ai_anomaly_alerts.entity_id IS 'ID of the entity that triggered the alert';
COMMENT ON COLUMN ai_anomaly_alerts.anomaly_score IS 'ML model anomaly score (0-1), higher = more anomalous';
COMMENT ON COLUMN ai_anomaly_alerts.context IS 'JSON: Supporting data for investigation (expected vs actual values, etc.)';
COMMENT ON COLUMN ai_anomaly_alerts.status IS 'Alert workflow: OPEN -> ACKNOWLEDGED -> RESOLVED/DISMISSED';

-- Indexes for alert management
CREATE INDEX IF NOT EXISTS idx_anomaly_alerts_distributor
    ON ai_anomaly_alerts(distributor_id, status);

CREATE INDEX IF NOT EXISTS idx_anomaly_alerts_status
    ON ai_anomaly_alerts(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_anomaly_alerts_severity
    ON ai_anomaly_alerts(severity, status);

CREATE INDEX IF NOT EXISTS idx_anomaly_alerts_type
    ON ai_anomaly_alerts(alert_type, status);

CREATE INDEX IF NOT EXISTS idx_anomaly_alerts_entity
    ON ai_anomaly_alerts(entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_anomaly_alerts_created
    ON ai_anomaly_alerts(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_anomaly_alerts_open
    ON ai_anomaly_alerts(distributor_id, severity, created_at DESC) WHERE status = 'OPEN';
