package com.zuqi.ai.service;

import com.zuqi.domain.ai.MerchantEmbedding;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing merchant profile embeddings for RAG (Retrieval-Augmented Generation).
 *
 * Enables semantic similarity search during credit scoring to find comparable
 * merchant profiles and provide peer context to the LLM.
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.3
 */
public interface MerchantEmbeddingService {

    /**
     * Embed a merchant's profile and store in pgvector.
     *
     * Process:
     * 1. Fetch merchant features via MerchantFeatureService
     * 2. Convert features to human-readable text summary
     * 3. Generate embedding via EmbeddingModel
     * 4. Store in ai_merchant_embeddings table
     *
     * @param merchantId Merchant to embed
     * @return Created/updated embedding entity
     */
    MerchantEmbedding embedMerchant(UUID merchantId);

    /**
     * Find N most similar merchants based on profile similarity.
     *
     * Uses pgvector cosine similarity search to find merchants
     * with similar business characteristics.
     *
     * @param merchantId Query merchant
     * @param limit Number of similar merchants to return (default: 5)
     * @return List of similar merchant embeddings with their feature summaries
     */
    List<MerchantEmbedding> findSimilarMerchants(UUID merchantId, int limit);

    /**
     * Batch embed all merchants for a distributor.
     *
     * Used for initial population or full refresh of embeddings.
     *
     * @param distributorId Distributor scope
     * @return Number of merchants embedded
     */
    int embedAllMerchants(UUID distributorId);

    /**
     * Refresh embeddings older than N days.
     *
     * Scheduled job to keep embeddings current as merchant behavior evolves.
     *
     * @param daysOld Refresh embeddings older than this many days
     * @return Number of embeddings refreshed
     */
    int refreshStaleEmbeddings(int daysOld);

    /**
     * Get embedding for a merchant (if exists).
     *
     * @param merchantId Merchant ID
     * @return Embedding or null if not yet embedded
     */
    MerchantEmbedding getEmbedding(UUID merchantId);

    /**
     * Delete embedding for a merchant.
     *
     * @param merchantId Merchant ID
     */
    void deleteEmbedding(UUID merchantId);
}
