package com.zuqi.ai.pricing;

import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.PricingRecommendation;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.PricingRecommendationRepository;
import com.zuqi.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.regression.Regressor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Smart pricing recommender using XGBoost regression (Model #17).
 *
 * <p>For each product, it predicts demand at multiple price points and selects
 * the price that maximises estimated revenue. The recommendation is saved to
 * {@code ai_pricing_recommendations}.
 *
 * <p>Price points evaluated: −10%, −5%, current, +5%, +10%, +15%.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmartPricingRecommender {

    private final PricingFeatureServiceImpl featureService;
    private final PricingFeatureBuilder featureBuilder;
    private final ModelLoaderService modelLoader;
    private final ModelPhaseService phaseService;
    private final ModelRegistry modelRegistry;
    private final DataPhaseTracker phaseTracker;
    private final PricingReasoningService reasoningService;
    private final PricingRecommendationRepository recommendationRepository;
    private final ProductRepository productRepository;
    private final DistributorRepository distributorRepository;

    private static final String MODEL_NAME = PricingTrainingPipeline.MODEL_NAME;

    /** Price multipliers to evaluate against current price. */
    private static final double[] PRICE_FACTORS = {0.90, 0.95, 1.00, 1.05, 1.10, 1.15};

    /**
     * Generate a pricing recommendation for a product.
     *
     * @return saved PricingRecommendation or null if model unavailable
     */
    public PricingRecommendation recommend(UUID productId, UUID distributorId) {
        PricingFeatures features = featureService.computeFeatures(productId, distributorId);

        Model<Regressor> model;
        try {
            model = modelLoader.loadModel(MODEL_NAME);
        } catch (Exception e) {
            log.warn("[SmartPricing] Model not available for product={}: {}", productId, e.getMessage());
            return null;
        } catch (Error e) {
            log.error("[SmartPricing] Fatal error loading model (native library issue?): {}", e.getMessage(), e);
            return null;
        }

        double currentPrice = features.currentUnitPrice();
        if (currentPrice <= 0) {
            log.debug("[SmartPricing] Product {} has no valid price, skipping", productId);
            return null;
        }

        // Evaluate demand and revenue at each price point
        Map<Double, Double> priceToRevenue = new LinkedHashMap<>();
        Map<Double, Double> priceToDemand  = new LinkedHashMap<>();

        for (double factor : PRICE_FACTORS) {
            double candidatePrice = currentPrice * factor;
            Example<Regressor> example = featureBuilder.buildInferenceExample(features, candidatePrice);
            double predictedDemand = Math.max(0.0,
                    model.predict(example).getOutput().getValues()[0]);
            double estimatedRevenue = candidatePrice * predictedDemand;
            priceToRevenue.put(candidatePrice, estimatedRevenue);
            priceToDemand.put(candidatePrice,  predictedDemand);
        }

        // Pick price with maximum revenue
        double recommendedPrice = priceToRevenue.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(currentPrice);

        double currentRevenue     = priceToRevenue.getOrDefault(currentPrice, 0.0);
        double recommendedRevenue = priceToRevenue.get(recommendedPrice);
        double revenueImpact      = recommendedRevenue - currentRevenue;

        double demandAtCurrent     = priceToDemand.getOrDefault(currentPrice, 0.0);
        double demandAtRecommended = priceToDemand.get(recommendedPrice);
        double priceChangePct      = (recommendedPrice - currentPrice) / currentPrice * 100.0;

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        String templateReason = buildReason(currentPrice, recommendedPrice, priceChangePct,
                demandAtCurrent, demandAtRecommended, revenueImpact);

        // LLM enhancement — falls back to template on failure
        String reason;
        try {
            String llmContext = buildLlmContext(product, currentPrice, recommendedPrice,
                    priceChangePct, demandAtCurrent, demandAtRecommended, revenueImpact,
                    features.similarProductAvgPrice());
            String llmReason = reasoningService.generateReason(llmContext);
            reason = (llmReason != null && !llmReason.isBlank()) ? llmReason : templateReason;
        } catch (Exception e) {
            log.debug("[SmartPricing] LLM reason failed for product={}: {}", productId, e.getMessage());
            reason = templateReason;
        }

        double confidence = phaseService.applyModifier(0.75, MODEL_NAME);

        Integer modelVersion = modelRegistry.getActiveModel(MODEL_NAME)
                .map(com.zuqi.domain.ai.AIModelRegistry::getModelVersion)
                .orElse(null);

        PricingRecommendation rec = PricingRecommendation.builder()
                .distributor(distributor)
                .product(product)
                .currentPrice(currentPrice)
                .recommendedPrice(recommendedPrice)
                .priceChangePct(priceChangePct)
                .predictedDemandAtCurrent(demandAtCurrent)
                .predictedDemandAtRecommended(demandAtRecommended)
                .estimatedRevenueImpactKes(revenueImpact)
                .reason(reason)
                .confidenceScore(confidence)
                .dataPhase(phaseTracker.getPhase(MODEL_NAME, distributorId).name())
                .status("PENDING")
                .modelVersion(modelVersion)
                .build();

        PricingRecommendation saved = recommendationRepository.save(rec);
        log.info("[SmartPricing] product={} currentPrice={} recommendedPrice={} impact=+{} KES",
                productId,
                String.format("%.0f", currentPrice),
                String.format("%.0f", recommendedPrice),
                String.format("%.0f", revenueImpact));
        return saved;
    }

    private String buildLlmContext(Product product,
                                    double currentPrice, double recommendedPrice,
                                    double priceChangePct, double demandAtCurrent,
                                    double demandAtRecommended, double revenueImpact,
                                    double marketAvgPrice) {
        String direction = priceChangePct > 0 ? "increase" : "decrease";
        double demandChangePct = demandAtCurrent > 0
                ? (demandAtRecommended - demandAtCurrent) / demandAtCurrent * 100.0 : 0.0;
        String productName = product.getName() != null ? product.getName() : "this product";
        return String.format("""
                Product: %s
                Current price: KES %.0f
                Recommended price: KES %.0f (%.1f%% %s)
                Market average price for similar products: KES %.0f
                Predicted demand at current price: %.1f units/week
                Predicted demand at recommended price: %.1f units/week (%.1f%% change)
                Estimated weekly revenue impact: KES +%.0f
                """,
                productName, currentPrice, recommendedPrice,
                Math.abs(priceChangePct), direction,
                marketAvgPrice,
                demandAtCurrent, demandAtRecommended, demandChangePct,
                revenueImpact);
    }

    private String buildReason(double currentPrice, double recommendedPrice,
                                double priceChangePct, double demandAtCurrent,
                                double demandAtRecommended, double revenueImpact) {
        if (Math.abs(priceChangePct) < 0.5) {
            return "Current price is already optimal — no change recommended.";
        }

        double demandChangePct = demandAtCurrent > 0
                ? (demandAtRecommended - demandAtCurrent) / demandAtCurrent * 100.0
                : 0.0;

        String direction = priceChangePct > 0 ? "increase" : "decrease";
        return String.format(
                "A %.1f%% price %s (KES %.0f → %.0f) reduces demand by %.1f%% " +
                "but increases estimated weekly revenue by KES %.0f.",
                Math.abs(priceChangePct), direction,
                currentPrice, recommendedPrice,
                Math.abs(demandChangePct), revenueImpact);
    }
}
