package com.zuqi.repository;

import com.zuqi.domain.ai.MerchantEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for merchant embeddings (pgvector similarity search).
 *
 * Blueprint reference: implementation_plan.md Task 2.3
 */
@Repository
public interface MerchantEmbeddingRepository extends JpaRepository<MerchantEmbedding, UUID> {

    /**
     * Find embedding by merchant ID.
     */
    Optional<MerchantEmbedding> findByMerchantId(UUID merchantId);

    /**
     * Find all embeddings for a distributor.
     */
    List<MerchantEmbedding> findByDistributorId(UUID distributorId);

    /**
     * Find N most similar merchants using pgvector cosine similarity.
     *
     * Returns merchants most similar to the given embedding vector,
     * excluding the query merchant itself.
     *
     * @param embedding The query embedding vector as string "[0.1, 0.2, ...]"
     * @param merchantId The merchant to exclude from results
     * @param distributorId Scope to same distributor (multi-tenant isolation)
     * @param limit Number of similar merchants to return
     */
    @Query(value = """
            SELECT * FROM ai_merchant_embeddings
            WHERE distributor_id = :distributorId
              AND merchant_id != :merchantId
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<MerchantEmbedding> findSimilarMerchants(
            @Param("embedding") String embedding,
            @Param("merchantId") UUID merchantId,
            @Param("distributorId") UUID distributorId,
            @Param("limit") int limit
    );

    /**
     * Count embeddings for a distributor.
     */
    long countByDistributorId(UUID distributorId);

    /**
     * Delete embedding by merchant ID.
     */
    void deleteByMerchantId(UUID merchantId);
}
