package com.zuqi.ai.demand;

import com.zuqi.ai.feature.ExpiryFeatureServiceImpl;
import com.zuqi.ai.feature.ExpiryFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.ExpiryRiskScore;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.ProductBatch;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.ExpiryRiskScoreRepository;
import com.zuqi.repository.ProductBatchRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.regression.Regressor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Predicts sell-through probability for perishable batches using XGBoost.
 *
 * Risk tiers:
 * - NORMAL:   risk < 0.3
 * - MODERATE: risk 0.3–0.6
 * - HIGH:     risk 0.6–0.8
 * - CRITICAL: risk > 0.8
 *
 * Blueprint: phase2-plan.md Section 2.2
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpiryRiskPredictor {

    private final ExpiryFeatureServiceImpl featureService;
    private final ExpiryRiskFeatureBuilder featureBuilder;
    private final ModelLoaderService modelLoader;
    private final ModelPhaseService phaseService;
    private final DataPhaseTracker phaseTracker;
    private final ModelRegistry modelRegistry;
    private final ExpiryRiskScoreRepository expiryRiskScoreRepository;
    private final DistributorRepository distributorRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final ProductBatchRepository productBatchRepository;

    static final String MODEL_NAME = ExpiryRiskTrainingPipeline.MODEL_NAME;

    public ExpiryRiskScore predict(UUID distributorId, UUID warehouseId,
                                    UUID productId, UUID batchId) {
        try {
            ExpiryFeatures features = featureService.computeFeatures(
                    distributorId, warehouseId, productId, batchId);

            double sellThroughProb = predictSellThrough(features);
            double riskScore = 1.0 - sellThroughProb;
            double confidence = phaseService.applyModifier(0.75, MODEL_NAME);

            String riskTier = classifyRisk(riskScore);
            String action = recommendAction(riskTier, features.daysToExpiry());
            double discountPct = suggestDiscount(riskScore, features.daysToExpiry());

            Distributor distributor = distributorRepository.findById(distributorId)
                    .orElseThrow(() -> new IllegalArgumentException("Distributor not found"));
            Warehouse warehouse = warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> new IllegalArgumentException("Warehouse not found"));
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            ProductBatch batch = productBatchRepository.findById(batchId)
                    .orElseThrow(() -> new IllegalArgumentException("Batch not found"));

            ExpiryRiskScore score = ExpiryRiskScore.builder()
                    .distributor(distributor)
                    .warehouse(warehouse)
                    .product(product)
                    .batch(batch)
                    .batchNumber(features.batchNumber())
                    .expiryDate(features.expiryDate())
                    .daysToExpiry(features.daysToExpiry())
                    .currentStockQty(features.currentStockQty())
                    .avgDailySalesRate(features.avgDailySalesRate())
                    .projectedDaysToSell(features.projectedDaysToSell())
                    .sellThroughProbability(sellThroughProb)
                    .riskScore(riskScore)
                    .riskTier(riskTier)
                    .recommendedAction(action)
                    .discountSuggestionPct(discountPct)
                    .confidenceScore(confidence)
                    .dataPhase(phaseTracker.getPhase(MODEL_NAME, distributorId).name())
                    .computedAt(LocalDateTime.now())
                    .build();

            ExpiryRiskScore saved = expiryRiskScoreRepository.save(score);
            log.info("Expiry risk: batch {} product {} — risk={} tier={} action={}",
                    features.batchNumber(), productId,
                    String.format("%.2f", riskScore), riskTier, action);
            return saved;

        } catch (Exception e) {
            log.error("Expiry risk prediction failed for batch {}: {}", batchId, e.getMessage(), e);
            throw new RuntimeException("Expiry risk prediction failed", e);
        }
    }

    private double predictSellThrough(ExpiryFeatures features) {
        Model<Regressor> model = modelLoader.loadModel(MODEL_NAME);
        if (model == null) {
            // Fallback: heuristic based on projected sell time vs days to expiry
            double projDays = features.projectedDaysToSell();
            double daysLeft = features.daysToExpiry();
            return projDays <= 0 ? 1.0 : Math.min(1.0, daysLeft / projDays);
        }

        Example<Regressor> example = featureBuilder.buildExample(features);
        double raw = model.predict(example).getOutput().getValues()[0];

        // Apply residual-based bounds
        double[] residuals = loadResidualPercentiles();
        if (residuals != null) {
            raw = Math.max(residuals[0], Math.min(residuals[1], raw + residuals[0]));
        }

        return Math.min(1.0, Math.max(0.0, raw));
    }

    private double[] loadResidualPercentiles() {
        return modelRegistry.getActiveModel(MODEL_NAME)
                .map(reg -> {
                    Map<String, Object> m = reg.getPerformanceMetrics();
                    if (m == null || !m.containsKey("lower_residual") || !m.containsKey("upper_residual")) {
                        return null;
                    }
                    return new double[]{
                            ((Number) m.get("lower_residual")).doubleValue(),
                            ((Number) m.get("upper_residual")).doubleValue()
                    };
                })
                .orElse(null);
    }

    private String classifyRisk(double riskScore) {
        if (riskScore < 0.3) return "NORMAL";
        if (riskScore < 0.6) return "MODERATE";
        if (riskScore < 0.8) return "HIGH";
        return "CRITICAL";
    }

    private String recommendAction(String riskTier, int daysToExpiry) {
        return switch (riskTier) {
            case "NORMAL" -> "NORMAL";
            case "MODERATE" -> daysToExpiry <= 14 ? "DISCOUNT" : "NORMAL";
            case "HIGH" -> daysToExpiry <= 30 ? "DISCOUNT" : "REDISTRIBUTE";
            case "CRITICAL" -> daysToExpiry <= 7 ? "QUARANTINE" : "DISCOUNT";
            default -> "NORMAL";
        };
    }

    private double suggestDiscount(double riskScore, int daysToExpiry) {
        if (riskScore < 0.3) return 0.0;
        // Scale: 5% at risk=0.3, 40% at risk=0.9, adjusted for urgency
        double baseDiscount = (riskScore - 0.3) / 0.6 * 35 + 5;
        double urgencyMultiplier = daysToExpiry <= 7 ? 1.5 : daysToExpiry <= 14 ? 1.2 : 1.0;
        return Math.min(50.0, baseDiscount * urgencyMultiplier);
    }
}
