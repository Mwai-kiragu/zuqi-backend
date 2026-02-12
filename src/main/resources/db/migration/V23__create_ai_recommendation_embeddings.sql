-- AI Recommendation Embeddings
-- Vector embeddings for historical recommendations to provide RAG context to AI agent
-- Enables semantic search for similar past recommendations and outcomes
-- Part of Phase 6: AI Agent Module with RAG Memory

CREATE TABLE IF NOT EXISTS ai_recommendation_embeddings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id   UUID NOT NULL,
    distributor_id      UUID NOT NULL,
    embedding           vector(768) NOT NULL,
    recommendation_summary TEXT,
    model_version       VARCHAR(50),
    created_at          TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_recommendation_embeddings_recommendation FOREIGN KEY (recommendation_id)
        REFERENCES ai_recommendations(id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_embeddings_distributor FOREIGN KEY (distributor_id)
        REFERENCES distributors(id) ON DELETE CASCADE,
    UNIQUE(recommendation_id)
);

-- Comments for documentation
COMMENT ON TABLE ai_recommendation_embeddings IS 'Vector embeddings for historical recommendations to enable RAG-enhanced agent context';
COMMENT ON COLUMN ai_recommendation_embeddings.embedding IS '768-dimensional vector embedding (dimension depends on embedding model)';
COMMENT ON COLUMN ai_recommendation_embeddings.recommendation_summary IS 'Human-readable summary used for embedding';
COMMENT ON COLUMN ai_recommendation_embeddings.model_version IS 'Embedding model version identifier';

-- Indexes for lookups (following existing migration patterns)
CREATE INDEX idx_recommendation_embeddings_recommendation ON ai_recommendation_embeddings(recommendation_id);
CREATE INDEX idx_recommendation_embeddings_distributor ON ai_recommendation_embeddings(distributor_id);

-- pgvector index for similarity search
CREATE INDEX idx_recommendation_embeddings_similarity ON ai_recommendation_embeddings
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
