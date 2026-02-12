-- AI Merchant Embeddings
-- Vector embeddings for merchant profiles used in RAG (Retrieval-Augmented Generation)
-- Enables semantic search for credit scoring and merchant analysis
-- Part of Phase 2: Credit Scoring Enhancement with RAG

CREATE TABLE IF NOT EXISTS ai_merchant_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL,
    distributor_id  UUID NOT NULL,
    embedding       vector(768) NOT NULL,
    feature_summary TEXT,
    model_version   VARCHAR(50),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_merchant_embeddings_merchant FOREIGN KEY (merchant_id)
        REFERENCES merchants(id) ON DELETE CASCADE,
    CONSTRAINT fk_merchant_embeddings_distributor FOREIGN KEY (distributor_id)
        REFERENCES distributors(id) ON DELETE CASCADE,
    UNIQUE(merchant_id)
);

-- Comments for documentation
COMMENT ON TABLE ai_merchant_embeddings IS 'Vector embeddings for merchant profiles to enable RAG-enhanced credit scoring';
COMMENT ON COLUMN ai_merchant_embeddings.embedding IS '768-dimensional vector embedding (dimension depends on embedding model)';
COMMENT ON COLUMN ai_merchant_embeddings.feature_summary IS 'Human-readable summary used for embedding';
COMMENT ON COLUMN ai_merchant_embeddings.model_version IS 'Embedding model version identifier';

-- Indexes for lookups (following existing migration patterns)
CREATE INDEX idx_merchant_embeddings_merchant ON ai_merchant_embeddings(merchant_id);
CREATE INDEX idx_merchant_embeddings_distributor ON ai_merchant_embeddings(distributor_id);

-- pgvector index for similarity search (cosine distance)
CREATE INDEX idx_merchant_embeddings_similarity ON ai_merchant_embeddings
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
