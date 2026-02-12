package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatureService;
import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.service.MerchantEmbeddingService;
import com.zuqi.domain.ai.MerchantEmbedding;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Builds credit evaluation inputs from merchant features.
 *
 * Transforms raw features into LLM-friendly profiles and peer comparison context.
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.4
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditFeatureBuilder {

    private final MerchantFeatureService merchantFeatureService;
    private final MerchantEmbeddingService embeddingService;
    private final MerchantRepository merchantRepository;

    /**
     * Build LLM-optimized merchant credit profile.
     *
     * Converts raw features into structured, narrative-friendly format
     * for LLM consumption.
     *
     * @param merchantId Merchant to profile
     * @return Structured credit profile
     */
    public MerchantCreditProfile buildLlmProfile(UUID merchantId) {
        log.debug("Building LLM credit profile for merchant {}", merchantId);

        // Fetch merchant entity
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

        // Compute features
        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId);

        // Determine geographic risk (placeholder - would come from external service)
        String geographicRisk = determineGeographicRisk(merchant);

        // Transform to LLM profile
        MerchantCreditProfile profile = MerchantCreditProfile.fromFeatures(
                features,
                merchant.getBusinessName(),
                geographicRisk
        );

        log.debug("Built credit profile for {} with {} total orders",
                merchant.getBusinessName(), features.totalOrders());

        return profile;
    }

    /**
     * Build peer comparison context for RAG.
     *
     * Finds similar merchants and summarizes their performance
     * to provide comparative context to the LLM.
     *
     * @param merchantId Query merchant
     * @return Human-readable peer comparison summary
     */
    public String buildPeerContext(UUID merchantId) {
        log.debug("Building peer context for merchant {}", merchantId);

        try {
            // Ensure merchant is embedded
            MerchantEmbedding queryEmbedding = embeddingService.getEmbedding(merchantId);
            if (queryEmbedding == null) {
                log.info("Merchant {} not yet embedded, embedding now", merchantId);
                embeddingService.embedMerchant(merchantId);
            }

            // Find 5 most similar merchants
            List<MerchantEmbedding> similarMerchants = embeddingService.findSimilarMerchants(merchantId, 5);

            if (similarMerchants.isEmpty()) {
                return "No comparable merchants found in database.";
            }

            // Build comparison summary
            StringBuilder context = new StringBuilder();
            context.append("Comparable merchants (").append(similarMerchants.size()).append(" found):\n\n");

            for (int i = 0; i < similarMerchants.size(); i++) {
                MerchantEmbedding similar = similarMerchants.get(i);
                context.append(i + 1).append(". ");
                context.append(similar.getFeatureSummary());
                context.append("\n\n");
            }

            log.debug("Built peer context with {} similar merchants", similarMerchants.size());
            return context.toString();

        } catch (Exception e) {
            log.warn("Failed to build peer context for merchant {}: {}", merchantId, e.getMessage());
            return "Peer comparison unavailable.";
        }
    }

    /**
     * Determine geographic risk category.
     *
     * TODO Phase 2+: Integrate with actual risk scoring based on location data.
     * For now, returns MEDIUM as default.
     */
    private String determineGeographicRisk(Merchant merchant) {
        // Placeholder implementation
        // In production, this would:
        // 1. Geocode merchant location
        // 2. Query regional default rates from credit bureau
        // 3. Classify as LOW/MEDIUM/HIGH based on historical data

        if (merchant.getCity() == null) {
            return "UNKNOWN";
        }

        // Simple heuristic: major cities = lower risk
        String city = merchant.getCity().toLowerCase();
        if (city.contains("nairobi") || city.contains("mombasa") || city.contains("kisumu")) {
            return "LOW";
        } else if (city.contains("nakuru") || city.contains("eldoret") || city.contains("thika")) {
            return "MEDIUM";
        } else {
            return "MEDIUM"; // Default
        }
    }
}
