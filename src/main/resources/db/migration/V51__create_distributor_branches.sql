-- Create distributor_branches table
CREATE TABLE IF NOT EXISTS distributor_branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50),
    address TEXT,
    city VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_headquarters BOOLEAN NOT NULL DEFAULT FALSE,
    manager_id UUID REFERENCES users(id),
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    CONSTRAINT uq_branch_code_distributor UNIQUE (code, distributor_id)
);

CREATE INDEX IF NOT EXISTS idx_branches_distributor ON distributor_branches(distributor_id);
CREATE INDEX IF NOT EXISTS idx_branches_status ON distributor_branches(status);

-- Create branch_users join table
CREATE TABLE IF NOT EXISTS branch_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id UUID NOT NULL REFERENCES distributor_branches(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    assigned_by UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    CONSTRAINT uq_branch_user UNIQUE (branch_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_branch_users_branch ON branch_users(branch_id);
CREATE INDEX IF NOT EXISTS idx_branch_users_user ON branch_users(user_id);
