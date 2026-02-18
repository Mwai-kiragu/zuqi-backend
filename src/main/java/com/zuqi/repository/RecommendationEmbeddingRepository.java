package com.zuqi.repository;

import com.zuqi.domain.ai.RecommendationEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecommendationEmbeddingRepository extends JpaRepository<RecommendationEmbedding, UUID> {

    Optional<RecommendationEmbedding> findByRecommendationId(UUID recommendationId);

    List<RecommendationEmbedding> findByDistributorId(UUID distributorId);

    void deleteByRecommendationId(UUID recommendationId);

    /**
     * Find N most similar past recommendations using pgvector cosine similarity.
     * Used by the AI agent to retrieve relevant historical context.
     */
    @Query(value = """
            SELECT * FROM ai_recommendation_embeddings
            WHERE distributor_id = :distributorId
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<RecommendationEmbedding> findSimilarRecommendations(
            @Param("embedding") String embedding,
            @Param("distributorId") UUID distributorId,
            @Param("limit") int limit);
}
