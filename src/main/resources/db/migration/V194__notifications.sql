CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT,
    entity_type VARCHAR(50),
    entity_id UUID,
    entity_name VARCHAR(200),
    approval_request_id UUID REFERENCES approval_requests(id) ON DELETE SET NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, read);

-- Casbin: every authenticated role can GET/PUT their notifications
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p','SUPER_ADMIN','/v1/notifications','GET'),('p','SUPER_ADMIN','/v1/notifications/.*','GET'),('p','SUPER_ADMIN','/v1/notifications/.*','PUT'),
  ('p','MERCHANT_ADMIN','/v1/notifications','GET'),('p','MERCHANT_ADMIN','/v1/notifications/.*','GET'),('p','MERCHANT_ADMIN','/v1/notifications/.*','PUT'),
  ('p','DISTRIBUTOR_ADMIN','/v1/notifications','GET'),('p','DISTRIBUTOR_ADMIN','/v1/notifications/.*','GET'),('p','DISTRIBUTOR_ADMIN','/v1/notifications/.*','PUT'),
  ('p','SALES_REP','/v1/notifications','GET'),('p','SALES_REP','/v1/notifications/.*','GET'),('p','SALES_REP','/v1/notifications/.*','PUT'),
  ('p','WAREHOUSE_MANAGER','/v1/notifications','GET'),('p','WAREHOUSE_MANAGER','/v1/notifications/.*','GET'),('p','WAREHOUSE_MANAGER','/v1/notifications/.*','PUT'),
  ('p','FINANCE','/v1/notifications','GET'),('p','FINANCE','/v1/notifications/.*','GET'),('p','FINANCE','/v1/notifications/.*','PUT'),
  ('p','DRIVER','/v1/notifications','GET'),('p','DRIVER','/v1/notifications/.*','GET'),('p','DRIVER','/v1/notifications/.*','PUT'),
  ('p','INITIATOR','/v1/notifications','GET'),('p','INITIATOR','/v1/notifications/.*','GET'),('p','INITIATOR','/v1/notifications/.*','PUT'),
  ('p','VERIFIER','/v1/notifications','GET'),('p','VERIFIER','/v1/notifications/.*','GET'),('p','VERIFIER','/v1/notifications/.*','PUT'),
  ('p','AUTHORIZER','/v1/notifications','GET'),('p','AUTHORIZER','/v1/notifications/.*','GET'),('p','AUTHORIZER','/v1/notifications/.*','PUT')
ON CONFLICT DO NOTHING;
