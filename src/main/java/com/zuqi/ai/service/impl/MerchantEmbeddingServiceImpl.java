package com.zuqi.ai.service.impl;

import com.zuqi.ai.feature.MerchantFeatureService;
import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.service.MerchantEmbeddingService;
import com.zuqi.domain.ai.MerchantEmbedding;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.repository.MerchantEmbeddingRepository;
import com.zuqi.repository.MerchantRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of MerchantEmbeddingService for RAG-based credit scoring.
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantEmbeddingServiceImpl implements MerchantEmbeddingService {

    private final MerchantEmbeddingRepository embeddingRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantFeatureService merchantFeatureService;
    private final EmbeddingModel embeddingModel;

    private static final String EMBEDDING_MODEL_VERSION = "all-MiniLM-L6-v2";

    @Override
    @Transactional
    public MerchantEmbedding embedMerchant(UUID merchantId) {
        log.info("Generating embedding for merchant {}", merchantId);

        // Fetch merchant
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

        // Compute features
        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId);

        // Convert features to text summary for embedding
        String featureSummary = buildFeatureSummary(merchant, features);

        // Generate embedding
        Embedding embedding = embeddingModel.embed(TextSegment.from(featureSummary)).content();
        String embeddingVector = formatEmbeddingAsString(embedding.vectorAsList());

        // Save or update
        MerchantEmbedding merchantEmbedding = embeddingRepository.findByMerchantId(merchantId)
                .orElse(MerchantEmbedding.builder()
                        .merchant(merchant)
                        .distributor(merchant.getDistributor())
                        .build());

        merchantEmbedding.setEmbedding(embeddingVector);
        merchantEmbedding.setFeatureSummary(featureSummary);
        merchantEmbedding.setModelVersion(EMBEDDING_MODEL_VERSION);
        merchantEmbedding.setUpdatedAt(LocalDateTime.now());

        MerchantEmbedding saved = embeddingRepository.save(merchantEmbedding);
        log.info("Successfully embedded merchant {} with {} dimensions", merchantId, embedding.dimension());

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MerchantEmbedding> findSimilarMerchants(UUID merchantId, int limit) {
        log.debug("Finding {} similar merchants to {}", limit, merchantId);

        // Get query merchant's embedding
        MerchantEmbedding queryEmbedding = embeddingRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Merchant not yet embedded: " + merchantId + ". Call embedMerchant() first."));

        // Find similar merchants using pgvector cosine similarity
        List<MerchantEmbedding> similar = embeddingRepository.findSimilarMerchants(
                queryEmbedding.getEmbedding(),
                merchantId,
                queryEmbedding.getDistributor().getId(),
                limit
        );

        log.debug("Found {} similar merchants for {}", similar.size(), merchantId);
        return similar;
    }

    @Override
    @Transactional
    public int embedAllMerchants(UUID distributorId) {
        log.info("Batch embedding all merchants for distributor {}", distributorId);

        List<Merchant> merchants = merchantRepository.findByDistributorId(distributorId);
        int embedded = 0;

        for (Merchant merchant : merchants) {
            try {
                embedMerchant(merchant.getId());
                embedded++;
            } catch (Exception e) {
                log.error("Failed to embed merchant {}: {}", merchant.getId(), e.getMessage(), e);
            }
        }

        log.info("Successfully embedded {}/{} merchants for distributor {}",
                embedded, merchants.size(), distributorId);
        return embedded;
    }

    @Override
    @Transactional
    public int refreshStaleEmbeddings(int daysOld) {
        log.info("Refreshing embeddings older than {} days", daysOld);

        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        List<MerchantEmbedding> staleEmbeddings = embeddingRepository.findAll().stream()
                .filter(e -> e.getUpdatedAt().isBefore(cutoff))
                .toList();

        int refreshed = 0;
        for (MerchantEmbedding embedding : staleEmbeddings) {
            try {
                embedMerchant(embedding.getMerchant().getId());
                refreshed++;
            } catch (Exception e) {
                log.error("Failed to refresh embedding for merchant {}: {}",
                        embedding.getMerchant().getId(), e.getMessage(), e);
            }
        }

        log.info("Refreshed {}/{} stale embeddings", refreshed, staleEmbeddings.size());
        return refreshed;
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantEmbedding getEmbedding(UUID merchantId) {
        return embeddingRepository.findByMerchantId(merchantId).orElse(null);
    }

    @Override
    @Transactional
    public void deleteEmbedding(UUID merchantId) {
        log.info("Deleting embedding for merchant {}", merchantId);
        embeddingRepository.deleteByMerchantId(merchantId);
    }

    // ==================== Helper Methods ====================

    /**
     * Build human-readable feature summary for embedding.
     *
     * Converts structured merchant features into natural language text
     * optimized for semantic embedding.
     */
    private String buildFeatureSummary(Merchant merchant, MerchantFeatures features) {
        StringBuilder summary = new StringBuilder();

        summary.append("Merchant Profile: ").append(merchant.getBusinessName()).append(". ");
        summary.append("Category: ").append(features.businessCategoryEncoded()).append(". ");
        summary.append("Relationship: ").append(features.relationshipTenureDays()).append(" days. ");

        // Order behavior
        summary.append("Orders: ").append(features.totalOrders()).append(" total, ");
        summary.append(String.format("%.1f", features.orderFrequencyPerWeek())).append(" per week. ");
        summary.append("Average order value: KES ").append(features.avgOrderValue()).append(". ");
        summary.append("Order trend: ").append(determineOrderTrend(features.orderValueTrendSlope12w())).append(". ");

        // Payment behavior
        summary.append("Payment history: ").append(String.format("%.0f%%", features.onTimePaymentPct() * 100))
                .append(" on-time rate, ");
        summary.append("average ").append(String.format("%.1f", features.avgDaysToPay())).append(" days to pay. ");
        summary.append("Consecutive on-time streak: ").append(features.consecutiveOnTimeStreak()).append(". ");

        // Credit utilization
        if (features.currentCreditLimit() != null && features.currentCreditLimit().intValue() > 0) {
            summary.append("Credit limit: KES ").append(features.currentCreditLimit()).append(", ");
            summary.append("utilization: ").append(String.format("%.0f%%", features.currentUtilizationRatio() * 100)).append(". ");
        }

        // Risk factors
        if (features.totalOverdueAmount() != null && features.totalOverdueAmount().intValue() > 0) {
            summary.append("Overdue amount: KES ").append(features.totalOverdueAmount()).append(". ");
        }

        return summary.toString().trim();
    }

    private String determineOrderTrend(Double slope) {
        if (slope == null) return "stable";
        if (slope > 0.1) return "growing";
        if (slope < -0.1) return "declining";
        return "stable";
    }

    /**
     * Convert embedding float array to pgvector string format: "[0.1, 0.2, ...]"
     */
    private String formatEmbeddingAsString(List<Float> vector) {
        return vector.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
