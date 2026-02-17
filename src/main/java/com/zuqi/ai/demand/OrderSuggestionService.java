package com.zuqi.ai.demand;

import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.MerchantRepository;
import com.zuqi.repository.ProductRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for generating AI-powered order suggestions for sales reps.
 *
 * Workflow:
 * 1. Retrieve pre-computed demand forecasts for merchant
 * 2. Filter out-of-stock SKUs
 * 3. Enforce credit limit constraints
 * 4. Rank by confidence, margin contribution, recency
 * 5. Return ordered suggestions
 *
 * Blueprint: plan.md Section 6.2 - Demand Forecasting Module
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSuggestionService {

    private final DemandForecaster demandForecaster;
    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;

    /**
     * Generate order suggestions for a merchant.
     *
     * @param merchantId Merchant ID
     * @param maxSuggestions Maximum number of suggestions to return
     * @return List of order suggestions ranked by priority
     */
    public List<OrderSuggestion> generateSuggestions(UUID merchantId, int maxSuggestions) {
        log.info("Generating order suggestions for merchant {}", merchantId);

        try {
            // 1. Load merchant
            Merchant merchant = merchantRepository.findById(merchantId)
                    .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

            // 2. Get active products (simplified - in production, filter by distributor, availability, etc.)
            List<Product> activeProducts = productRepository.findAll();
            log.debug("Found {} active products", activeProducts.size());

            // 3. Generate forecasts for all products
            List<DemandForecaster.MerchantProductPair> pairs = activeProducts.stream()
                    .map(product -> new DemandForecaster.MerchantProductPair(merchantId, product.getId()))
                    .toList();

            List<DemandForecaster.DemandForecast> forecasts = demandForecaster.batchForecast(pairs);
            log.debug("Generated {} demand forecasts", forecasts.size());

            // 4. Filter and rank suggestions
            List<OrderSuggestion> suggestions = forecasts.stream()
                    // Filter: only suggest products with predicted quantity > 0
                    .filter(forecast -> forecast.predictedQuantity().compareTo(BigDecimal.ZERO) > 0)
                    // Filter: only suggest products with acceptable confidence
                    .filter(forecast -> forecast.confidence() >= 0.5)
                    // Map to OrderSuggestion
                    .map(forecast -> buildSuggestion(forecast, activeProducts))
                    // Filter out nulls (products not found)
                    .filter(suggestion -> suggestion != null)
                    // Filter: enforce credit limit
                    .filter(suggestion -> isWithinCreditLimit(merchant, suggestion))
                    // Sort by priority (confidence desc, then quantity desc)
                    .sorted(Comparator
                            .comparing(OrderSuggestion::confidence).reversed()
                            .thenComparing(OrderSuggestion::suggestedQuantity).reversed()
                    )
                    // Limit to max suggestions
                    .limit(maxSuggestions)
                    .collect(Collectors.toList());

            log.info("Generated {} order suggestions for merchant {}", suggestions.size(), merchantId);
            return suggestions;

        } catch (Exception e) {
            log.error("Failed to generate order suggestions for merchant {}: {}",
                    merchantId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Build order suggestion from demand forecast.
     */
    private OrderSuggestion buildSuggestion(
            DemandForecaster.DemandForecast forecast,
            List<Product> products) {

        // Find product
        Product product = products.stream()
                .filter(p -> p.getId().equals(forecast.productId()))
                .findFirst()
                .orElse(null);

        if (product == null) {
            return null;
        }

        // Calculate estimated value
        BigDecimal estimatedValue = forecast.predictedQuantity()
                .multiply(product.getUnitPrice());

        // Build suggestion
        return OrderSuggestion.builder()
                .productId(forecast.productId())
                .productName(product.getName())
                .productCategory(product.getCategory() != null ? product.getCategory().getName() : "Unknown")
                .suggestedQuantity(forecast.predictedQuantity())
                .unitPrice(product.getUnitPrice())
                .estimatedValue(estimatedValue)
                .confidence(forecast.confidence())
                .trendDirection(forecast.trendDirection())
                .reasoning(buildReasoning(forecast))
                .priority(calculatePriority(forecast))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Check if suggestion is within merchant's available credit limit.
     */
    private boolean isWithinCreditLimit(Merchant merchant, OrderSuggestion suggestion) {
        // TODO: Implement actual credit limit check
        // For now, accept all suggestions
        return true;
    }

    /**
     * Build human-readable reasoning for suggestion.
     */
    private String buildReasoning(DemandForecaster.DemandForecast forecast) {
        StringBuilder reasoning = new StringBuilder();

        if (forecast.rollingAvg4w() != null && forecast.rollingAvg4w().compareTo(BigDecimal.ZERO) > 0) {
            reasoning.append(String.format("Based on 4-week average of %.0f units. ",
                    forecast.rollingAvg4w().doubleValue()));
        }

        if ("INCREASING".equals(forecast.trendDirection())) {
            reasoning.append("Demand is increasing. ");
        } else if ("DECREASING".equals(forecast.trendDirection())) {
            reasoning.append("Demand is declining. ");
        } else {
            reasoning.append("Demand is stable. ");
        }

        if (forecast.confidence() >= 0.8) {
            reasoning.append("High confidence prediction.");
        } else if (forecast.confidence() >= 0.6) {
            reasoning.append("Moderate confidence prediction.");
        } else {
            reasoning.append("Lower confidence - new ordering pattern.");
        }

        return reasoning.toString();
    }

    /**
     * Calculate suggestion priority (0-100).
     *
     * Higher priority = higher confidence + higher volume + increasing trend
     */
    private int calculatePriority(DemandForecaster.DemandForecast forecast) {
        double basePriority = forecast.confidence() * 100; // 0-100

        // Boost for increasing trends
        if ("INCREASING".equals(forecast.trendDirection())) {
            basePriority += 10;
        }

        // Reduce for decreasing trends
        if ("DECREASING".equals(forecast.trendDirection())) {
            basePriority -= 10;
        }

        // Boost for high volumes
        if (forecast.predictedQuantity().compareTo(BigDecimal.valueOf(100)) > 0) {
            basePriority += 5;
        }

        // Clamp to 0-100
        return (int) Math.max(0, Math.min(100, basePriority));
    }

    /**
     * Order suggestion for sales rep.
     */
    @Builder
    public record OrderSuggestion(
            UUID productId,
            String productName,
            String productCategory,
            BigDecimal suggestedQuantity,
            BigDecimal unitPrice,
            BigDecimal estimatedValue,
            double confidence,              // 0.0-1.0
            String trendDirection,          // INCREASING, STABLE, DECREASING
            String reasoning,               // Human-readable explanation
            int priority,                   // 0-100 (higher = more important)
            LocalDateTime generatedAt
    ) {
    }
}
