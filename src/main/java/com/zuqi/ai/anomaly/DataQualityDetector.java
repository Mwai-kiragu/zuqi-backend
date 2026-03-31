package com.zuqi.ai.anomaly;

import com.zuqi.ai.event.OrderCreatedEvent;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.domain.ai.AlertSeverity;
import com.zuqi.domain.ai.AlertType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.classification.Label;
import org.tribuo.impl.ArrayExample;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dual-tier data quality detector for incoming orders.
 *
 * Tier-1 (rules engine): fires synchronously, zero-latency, hard violations only.
 *   1. Order must have at least 1 item
 *   2. Each item: unitPrice != null && unitPrice > 0
 *   3. Each item: quantity > 0
 *   4. Each item: quantity <= 10,000 (suspiciously large threshold)
 *   5. merchantId not null
 *   6. totalAmount consistent with sum of (qty × price) within 1% tolerance
 *
 * Tier-2 (XGBoost classifier): fires after Tier-1, catches soft anomalies that
 *   pass the rules engine — e.g. prices technically > 0 but 10× above merchant
 *   history, or unusual hour + value combination. Uses 14 features.
 *   Requires a trained model in the registry; skipped gracefully if absent.
 *
 * A single alert per order is raised, merging findings from both tiers.
 *
 * Blueprint reference: plan.md Section 6.3 - DataQualityDetector
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataQualityDetector {

    static final String MODEL_NAME = DataQualityTrainingPipeline.MODEL_NAME;

    /** Tier-2 anomaly score threshold above which a soft alert is raised. */
    static final double ML_SCORE_THRESHOLD = 0.75;

    private static final int    MAX_ITEM_QUANTITY      = 10_000;
    private static final double TOTAL_AMOUNT_TOLERANCE = 0.01;  // 1%

    private final AlertService              alertService;
    private final DataQualityFeatureBuilder featureBuilder;
    private final ModelLoaderService        modelLoader;
    private final ModelPhaseService         phaseService;

    /**
     * Validate an order for data quality violations and raise an alert if any are found.
     *
     * @param event The OrderCreatedEvent to validate
     * @return List of violations found (empty if the order is clean)
     */
    public List<DataQualityViolation> detect(OrderCreatedEvent event) {
        List<DataQualityViolation> violations = new ArrayList<>();

        // Rule 1: at least one item
        if (event.items() == null || event.items().isEmpty()) {
            violations.add(new DataQualityViolation(
                    "items", "ORDER_MUST_HAVE_ITEMS",
                    "0", AlertSeverity.HIGH));
        }

        // Rule 5: merchantId not null (already validated in event constructor, but belt-and-braces)
        if (event.merchantId() == null) {
            violations.add(new DataQualityViolation(
                    "merchantId", "MERCHANT_ID_REQUIRED",
                    "null", AlertSeverity.HIGH));
        }

        // Per-item rules
        if (event.items() != null) {
            for (int idx = 0; idx < event.items().size(); idx++) {
                OrderCreatedEvent.OrderItem item = event.items().get(idx);
                String itemRef = "items[" + idx + "]";

                // Rule 2: unit price must be positive
                if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    violations.add(new DataQualityViolation(
                            itemRef + ".unitPrice", "POSITIVE_UNIT_PRICE_REQUIRED",
                            String.valueOf(item.unitPrice()), AlertSeverity.MEDIUM));
                }

                // Rule 3: quantity must be positive (already validated in OrderItem constructor, extra check)
                if (item.quantity() == null || item.quantity() <= 0) {
                    violations.add(new DataQualityViolation(
                            itemRef + ".quantity", "POSITIVE_QUANTITY_REQUIRED",
                            String.valueOf(item.quantity()), AlertSeverity.MEDIUM));
                }

                // Rule 4: quantity suspiciously large
                if (item.quantity() != null && item.quantity() > MAX_ITEM_QUANTITY) {
                    violations.add(new DataQualityViolation(
                            itemRef + ".quantity", "QUANTITY_EXCEEDS_THRESHOLD",
                            String.valueOf(item.quantity()), AlertSeverity.MEDIUM));
                }
            }
        }

        // Rule 6: total amount consistency
        if (event.items() != null && !event.items().isEmpty() && event.totalAmount() != null) {
            BigDecimal computedTotal = event.items().stream()
                    .filter(i -> i.unitPrice() != null && i.quantity() != null && i.quantity() > 0)
                    .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (computedTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diff = event.totalAmount().subtract(computedTotal).abs();
                BigDecimal tolerance = computedTotal.multiply(BigDecimal.valueOf(TOTAL_AMOUNT_TOLERANCE));
                if (diff.compareTo(tolerance) > 0) {
                    violations.add(new DataQualityViolation(
                            "totalAmount", "TOTAL_AMOUNT_INCONSISTENT",
                            "expected=" + computedTotal.setScale(2, RoundingMode.HALF_UP)
                                    + " actual=" + event.totalAmount(),
                            AlertSeverity.MEDIUM));
                }
            }
        }

        // ── Tier-2: XGBoost ML classifier ─────────────────────────────────
        double mlScore = runTier2(event);
        if (mlScore >= ML_SCORE_THRESHOLD) {
            violations.add(new DataQualityViolation(
                    "order", "ML_ANOMALY_DETECTED",
                    String.format("score=%.3f threshold=%.2f", mlScore, ML_SCORE_THRESHOLD),
                    AlertSeverity.MEDIUM));
        }

        // Create a single alert if violations found
        if (!violations.isEmpty()) {
            AlertSeverity maxSeverity = violations.stream()
                    .map(DataQualityViolation::severity)
                    .max(Enum::compareTo)
                    .orElse(AlertSeverity.MEDIUM);

            Map<String, Object> context = buildContext(violations);

            alertService.createAlert(
                    AlertType.DATA_QUALITY,
                    maxSeverity,
                    "ORDER",
                    event.orderId(),
                    event.distributorId(),
                    (double) violations.size(),
                    "Data quality violations detected on order " + event.orderId()
                            + ": " + violations.size() + " violation(s)",
                    context
            );

            log.warn("Data quality violations for order {}: {} violation(s)", event.orderId(), violations.size());
        } else {
            log.debug("Order {} passed data quality checks", event.orderId());
        }

        return violations;
    }

    /**
     * Run Tier-2 ML classifier. Returns anomaly score in [0,1] where 1 = most anomalous.
     * Returns 0.0 safely if no model is available.
     */
    private double runTier2(OrderCreatedEvent event) {
        try {
            @SuppressWarnings("unchecked")
            Model<Label> model = (Model<Label>) modelLoader.loadModel(MODEL_NAME);
            if (model == null) return 0.0;

            ArrayExample<Label> example = featureBuilder.buildInferenceExample(event);
            Prediction<Label> prediction = model.predict(example);

            // Extract probability of ANOMALOUS label
            Map<String, Label> scores = prediction.getOutputScores();
            Label anomalousScore = scores.get(DataQualityFeatureBuilder.ANOMALOUS.getLabel());
            double rawScore = anomalousScore != null ? anomalousScore.getScore() : 0.0;

            return phaseService.applyModifier(rawScore, MODEL_NAME);
        } catch (Exception e) {
            log.warn("Tier-2 data quality check skipped for order {}: {}",
                    event.orderId(), e.getMessage());
            return 0.0;
        } catch (Error e) {
            log.error("Fatal error in data quality check for order {} (native library issue?): {}", event.orderId(), e.getMessage(), e);
            return 0.0;
        }
    }

    private Map<String, Object> buildContext(List<DataQualityViolation> violations) {
        Map<String, Object> ctx = new HashMap<>();
        List<Map<String, String>> details = new ArrayList<>();
        for (DataQualityViolation v : violations) {
            details.add(Map.of(
                    "field",        v.field(),
                    "rule",         v.rule(),
                    "actualValue",  v.actualValue() != null ? v.actualValue() : "null",
                    "severity",     v.severity().name()
            ));
        }
        ctx.put("violations", details);
        ctx.put("violationCount", violations.size());
        return ctx;
    }

    // ── Violation record ──────────────────────────────────────────────────

    public record DataQualityViolation(
            String        field,
            String        rule,
            String        actualValue,
            AlertSeverity severity
    ) {}
}
