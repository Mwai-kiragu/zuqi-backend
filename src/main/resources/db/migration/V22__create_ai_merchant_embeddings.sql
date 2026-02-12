-- AI Merchant Embeddings
-- Vector embeddings for merchant profiles used in RAG (Retrieval-Augmented Generation)
-- Enables semantic search for credit scoring and merchant analysis
-- Part of Phase 2: Credit Scoring Enhancement with RAG

CREATE TABLE IF NOT EXISTS ai_merchant_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL,
    distributor_id  UUID NOT NULL,
    embedding       vector(1536) NOT NULL,
    text_content    TEXT NOT NULL,
    embedding_model VARCHAR(50) NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_merchant_embeddings_merchant FOREIGN KEY (merchant_id)
        REFERENCES merchants(id) ON DELETE CASCADE,
    CONSTRAINT fk_merchant_embeddings_distributor FOREIGN KEY (distributor_id)
        REFERENCES distributors(id) ON DELETE CASCADE,
    CONSTRAINT uq_merchant_embedding UNIQUE(merchant_id, embedding_model)
);

-- Comments for documentation
COMMENT ON TABLE ai_merchant_embeddings IS 'Vector embeddings for merchant profiles to enable RAG-enhanced credit scoring';
COMMENT ON COLUMN ai_merchant_embeddings.embedding IS 'OpenAI ada-002 1536-dimensional vector embedding';
COMMENT ON COLUMN ai_merchant_embeddings.text_content IS 'Original text that was embedded: merchant profile, payment history, order patterns';
COMMENT ON COLUMN ai_merchant_embeddings.embedding_model IS 'Model used: text-embedding-ada-002, text-embedding-3-small, etc.';
COMMENT ON COLUMN ai_merchant_embeddings.metadata IS 'JSON: {profile_version, included_features: [order_history, payment_behavior], data_date}';
COMMENT ON CONSTRAINT uq_merchant_embedding ON ai_merchant_embeddings IS 'One embedding per merchant per model version';

-- Indexes for vector similarity search and lookups
CREATE INDEX IF NOT EXISTS idx_merchant_embeddings_merchant
    ON ai_merchant_embeddings(merchant_id);

CREATE INDEX IF NOT EXISTS idx_merchant_embeddings_distributor
    ON ai_merchant_embeddings(distributor_id);

CREATE INDEX IF NOT EXISTS idx_merchant_embeddings_model
    ON ai_merchant_embeddings(embedding_model);

CREATE INDEX IF NOT EXISTS idx_merchant_embeddings_updated
    ON ai_merchant_embeddings(updated_at DESC);

-- pgvector HNSW index for fast cosine similarity search
-- Using m=16 (connections per layer) and ef_construction=64 (build-time quality)
CREATE INDEX IF NOT EXISTS idx_merchant_embeddings_vector
    ON ai_merchant_embeddings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
