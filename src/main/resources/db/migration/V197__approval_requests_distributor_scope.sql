-- Scope approval requests to the distributor (business) that created them.
-- Without this, approvers see pending requests from all businesses.
ALTER TABLE approval_requests
    ADD COLUMN IF NOT EXISTS distributor_id UUID REFERENCES distributors(id);

CREATE INDEX IF NOT EXISTS idx_approval_requests_distributor
    ON approval_requests(distributor_id);

-- Back-fill existing rows from the order they reference (best effort)
UPDATE approval_requests ar
SET distributor_id = o.distributor_id
FROM orders o
WHERE ar.entity_type = 'ORDER'
  AND ar.entity_id   = o.id
  AND ar.distributor_id IS NULL;
