package com.zuqi.ai.crm;

import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.ChurnPrediction;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.ChurnPredictionRepository;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.DistributorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.classification.Label;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Predicts churn probability for a customer and maps to a risk tier with recommended action.
 *
 * <p>Falls back to a heuristic when no trained model exists:
 * {@code daysSinceLastOrder > 30} → churnProbability = 0.5 (MODERATE).
 *
 * <p>Risk tiers:
 * <ul>
 *   <li>LOW — churnProbability &lt; 0.3</li>
 *   <li>MODERATE — 0.3 ≤ p &lt; 0.5</li>
 *   <li>HIGH — 0.5 ≤ p &lt; 0.7</li>
 *   <li>CRITICAL — p ≥ 0.7</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChurnPredictor {

    private final CustomerAnalyticsFeatureServiceImpl featureService;
    private final ChurnFeatureBuilder churnFeatureBuilder;
    private final ModelLoaderService modelLoader;
    private final ModelPhaseService phaseService;
    private final ChurnPredictionRepository churnRepository;
    private final CustomerRepository customerRepository;
    private final DistributorRepository distributorRepository;

    /**
     * Predict and persist churn probability for a customer.
     *
     * @param customerId    customer to predict for
     * @param distributorId distributor context
     * @return saved ChurnPrediction entity
     */
    @Transactional
    public ChurnPrediction predict(UUID customerId, UUID distributorId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        CustomerAnalyticsFeatures features = featureService.computeFeatures(customerId, distributorId);

        double churnProbability;
        double confidence;

        Model<Label> model = loadModel();
        if (model != null) {
            org.tribuo.Example<Label> example = churnFeatureBuilder.buildExample(features);
            Prediction<Label> prediction = model.predict(example);
            // Get probability of CHURNED label
            churnProbability = prediction.getOutputScores().getOrDefault(
                    new Label(ChurnFeatureBuilder.LABEL_CHURNED, 0.0),
                    new Label(ChurnFeatureBuilder.LABEL_CHURNED, 0.0)).getScore();
            churnProbability = phaseService.applyModifier(churnProbability, DataPhaseTracker.MODEL_CHURN_PREDICTOR);
            confidence = phaseService.applyModifier(0.8, DataPhaseTracker.MODEL_CHURN_PREDICTOR);
        } else {
            // Heuristic fallback
            churnProbability = features.daysSinceLastOrder() > 30 ? 0.5 : 0.2;
            confidence = 0.4;
        }

        churnProbability = Math.max(0.0, Math.min(1.0, churnProbability));

        String riskTier = computeRiskTier(churnProbability);
        String topChurnFactor = computeTopChurnFactor(features);
        String recommendedAction = computeRecommendedAction(riskTier, features);
        String dataPhase = phaseService.isSyntheticPhase(DataPhaseTracker.MODEL_CHURN_PREDICTOR)
                ? "SYNTHETIC" : "REAL";

        Optional<ChurnPrediction> existing =
                churnRepository.findByDistributorIdAndCustomerId(distributorId, customerId);

        ChurnPrediction entity = existing.orElseGet(() -> ChurnPrediction.builder()
                .distributor(distributor)
                .customer(customer)
                .build());

        entity.setChurnProbability(churnProbability);
        entity.setRiskTier(riskTier);
        entity.setDaysSinceLastOrder(features.daysSinceLastOrder() == Integer.MAX_VALUE
                ? null : features.daysSinceLastOrder());
        entity.setTopChurnFactor(topChurnFactor);
        entity.setRecommendedAction(recommendedAction);
        entity.setConfidenceScore(confidence);
        entity.setDataPhase(dataPhase);
        entity.setComputedAt(LocalDateTime.now());

        return churnRepository.save(entity);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Model<Label> loadModel() {
        try {
            return modelLoader.loadModel(DataPhaseTracker.MODEL_CHURN_PREDICTOR);
        } catch (Exception e) {
            log.warn("[Churn] No active model, using heuristic: {}", e.getMessage());
            return null;
        }
    }

    private String computeRiskTier(double p) {
        if (p < 0.3) return "LOW";
        if (p < 0.5) return "MODERATE";
        if (p < 0.7) return "HIGH";
        return "CRITICAL";
    }

    private String computeTopChurnFactor(CustomerAnalyticsFeatures f) {
        if (f.daysSinceLastOrder() > 30) return "days_since_last_order";
        if (f.revenueTrendSlope() < 0) return "revenue_decline";
        if (f.paymentTimelinessScore() < 50) return "payment_issues";
        return "low_order_frequency";
    }

    private String computeRecommendedAction(String riskTier, CustomerAnalyticsFeatures f) {
        return switch (riskTier) {
            case "CRITICAL" -> "Immediate outreach required. Schedule priority visit within 48 hours. "
                    + "Consider special discount or credit incentive to re-engage.";
            case "HIGH" -> "Schedule a visit this week. Review payment terms and offer targeted promotion.";
            case "MODERATE" -> "Flag for next route visit. Check-in call recommended.";
            default -> "No immediate action required. Continue standard visit schedule.";
        };
    }
}
