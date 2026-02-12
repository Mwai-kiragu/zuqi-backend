-- AI Recommendation Embeddings
-- Vector embeddings for historical recommendations to provide RAG context to AI agent
-- Enables semantic search for similar past recommendations and outcomes
-- Part of Phase 6: AI Agent Module with RAG Memory

CREATE TABLE IF NOT EXISTS ai_recommendation_embeddings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id   UUID NOT NULL,
    distributor_id      UUID NOT NULL,
    embedding           vector(1536) NOT NULL,
    text_content        TEXT NOT NULL,
    embedding_model     VARCHAR(50) NOT NULL,
    metadata            JSONB,
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_recommendation_embeddings_recommendation FOREIGN KEY (recommendation_id)
        REFERENCES ai_recommendations(id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_embeddings_distributor FOREIGN KEY (distributor_id)
        REFERENCES distributors(id) ON DELETE CASCADE,
    CONSTRAINT uq_recommendation_embedding UNIQUE(recommendation_id, embedding_model)
);

-- Comments for documentation
COMMENT ON TABLE ai_recommendation_embeddings IS 'Vector embeddings for historical recommendations to enable RAG-enhanced agent context';
COMMENT ON COLUMN ai_recommendation_embeddings.embedding IS 'OpenAI ada-002 1536-dimensional vector embedding';
COMMENT ON COLUMN ai_recommendation_embeddings.text_content IS 'Original text embedded: observation + recommendation + outcome for completed recommendations';
COMMENT ON COLUMN ai_recommendation_embeddings.embedding_model IS 'Model used: text-embedding-ada-002, text-embedding-3-small, etc.';
COMMENT ON COLUMN ai_recommendation_embeddings.metadata IS 'JSON: {recommendation_type, priority, status, outcome_quality, effectiveness_score}';
COMMENT ON CONSTRAINT uq_recommendation_embedding ON ai_recommendation_embeddings IS 'One embedding per recommendation per model version';

-- Indexes for vector similarity search and lookups
CREATE INDEX IF NOT EXISTS idx_recommendation_embeddings_recommendation
    ON ai_recommendation_embeddings(recommendation_id);

CREATE INDEX IF NOT EXISTS idx_recommendation_embeddings_distributor
    ON ai_recommendation_embeddings(distributor_id);

CREATE INDEX IF NOT EXISTS idx_recommendation_embeddings_model
    ON ai_recommendation_embeddings(embedding_model);

CREATE INDEX IF NOT EXISTS idx_recommendation_embeddings_updated
    ON ai_recommendation_embeddings(updated_at DESC);

-- pgvector HNSW index for fast cosine similarity search
-- Using m=16 (connections per layer) and ef_construction=64 (build-time quality)
CREATE INDEX IF NOT EXISTS idx_recommendation_embeddings_vector
    ON ai_recommendation_embeddings USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
