package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.MerchantPaymentTrendFeatures;
import com.zuqi.ai.feature.PaymentFeatureService;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.impl.ArrayExample;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Classifies merchants as financially distressed or healthy using a trained
 * XGBoost classification model.
 *
 * <p>The model is trained on {@link MerchantPaymentTrendFeatures} — 3-month
 * payment-behaviour trends — and outputs a probability that the merchant is
 * heading toward payment default ("DISTRESS").
 *
 * <p>When no model is available (first boot, model training in progress, etc.)
 * the classifier returns a safe {@code isDistressed = false} fallback so that
 * upstream callers are never blocked.
 *
 * <p>Blueprint reference: implementation_plan.md Phase 6 — Payment Distress
 * Classification; plan.md Section 6.3 - PaymentDistressClassifier
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentDistressClassifier {

    private static final String MODEL_NAME = "payment_distress_classifier";

    /** Tribuo Label for the positive (distressed) class. */
    private static final String LABEL_DISTRESS    = "DISTRESS";
    private static final String LABEL_NO_DISTRESS = "NO_DISTRESS";

    private static final LabelFactory LABEL_FACTORY = new LabelFactory();

    private final ModelLoaderService    modelLoader;
    private final PaymentFeatureService paymentFeatureService;
    private final ModelPhaseService     phaseService;

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Classify a merchant's payment-distress risk.
     *
     * @param merchantId Merchant to evaluate
     * @return {@link DistressResult} containing the distress flag and probability
     */
    public DistressResult classify(UUID merchantId) {
        // 1. Load active classification model
        Model<Label> model;
        try {
            model = modelLoader.loadModel(MODEL_NAME);
        } catch (Exception e) {
            log.warn("No active model found for '{}' ({}), returning safe default",
                    MODEL_NAME, e.getMessage());
            return defaultResult(merchantId);
        } catch (Error e) {
            log.error("Fatal error loading model '{}' (native library issue?): {}", MODEL_NAME, e.getMessage(), e);
            return defaultResult(merchantId);
        }

        if (model == null) {
            log.warn("Model loader returned null for '{}', returning safe default", MODEL_NAME);
            return defaultResult(merchantId);
        }

        // 2. Compute merchant-level payment trend features
        MerchantPaymentTrendFeatures features;
        try {
            features = paymentFeatureService.computeMerchantTrendFeatures(merchantId);
        } catch (Exception e) {
            log.warn("Failed to compute trend features for merchant [{}]: {}",
                    merchantId, e.getMessage());
            return defaultResult(merchantId);
        }

        // 3. Build Tribuo classification example from trend features
        Example<Label> example;
        try {
            example = buildExample(features);
        } catch (Exception e) {
            log.warn("Failed to build Tribuo example for merchant [{}]: {}",
                    merchantId, e.getMessage());
            return defaultResult(merchantId);
        }

        // 4. Run inference
        try {
            Prediction<Label> prediction = model.predict(example);

            // Extract DISTRESS probability from output scores map
            Map<String, Label> scores = prediction.getOutputScores();
            double distressProb = 0.0;

            if (scores.containsKey(LABEL_DISTRESS)) {
                distressProb = scores.get(LABEL_DISTRESS).getScore();
            }

            boolean isDistressed = LABEL_DISTRESS.equals(prediction.getOutput().getLabel());
            double adjustedDistressProb = phaseService.applyModifier(distressProb, MODEL_NAME);

            log.debug("Payment distress classification: merchant={} distressed={} prob={}",
                    merchantId, isDistressed, String.format("%.3f", adjustedDistressProb));

            return new DistressResult(merchantId, isDistressed, adjustedDistressProb, MODEL_NAME);

        } catch (Exception e) {
            log.error("Prediction failed for merchant [{}]: {}", merchantId, e.getMessage(), e);
            return defaultResult(merchantId);
        }
    }

    // ── Feature vector construction ───────────────────────────────────────

    /**
     * Build a Tribuo {@link ArrayExample} from {@link MerchantPaymentTrendFeatures}.
     *
     * <p>Feature list (20 features covering payment timing, utilisation, partial
     * payment behaviour, order-value trends, and overall financial health):
     * <ol>
     *   <li>days_to_pay_trend_3m</li>
     *   <li>days_to_pay_stddev_3m</li>
     *   <li>late_payment_rate_3m</li>
     *   <li>late_payment_rate_trend_3m</li>
     *   <li>order_frequency_3m</li>
     *   <li>order_frequency_trend_3m</li>
     *   <li>consecutive_missed_orders</li>
     *   <li>credit_utilization_3m</li>
     *   <li>credit_utilization_trajectory</li>
     *   <li>peak_utilization_3m</li>
     *   <li>hit_credit_limit_3m</li>
     *   <li>partial_payment_freq_3m</li>
     *   <li>partial_payment_freq_trend_3m</li>
     *   <li>consecutive_partial_payments</li>
     *   <li>avg_order_value_3m</li>
     *   <li>avg_order_value_trend_3m</li>
     *   <li>order_value_volatility_3m</li>
     *   <li>outstanding_trend_3m</li>
     *   <li>days_overdue_max</li>
     *   <li>payment_to_order_ratio_3m</li>
     * </ol>
     */
    private Example<Label> buildExample(MerchantPaymentTrendFeatures f) {
        List<Feature> featureList = new ArrayList<>();

        // Payment timing trends
        featureList.add(new Feature("days_to_pay_trend_3m",
                safeDouble(f.daysToPayTrend3m())));
        featureList.add(new Feature("days_to_pay_stddev_3m",
                safeDouble(f.daysToPayStddev3m())));
        featureList.add(new Feature("late_payment_rate_3m",
                safeDouble(f.latePaymentRate3m())));
        featureList.add(new Feature("late_payment_rate_trend_3m",
                safeDouble(f.latePaymentRateTrend3m())));

        // Order frequency trends
        featureList.add(new Feature("order_frequency_3m",
                safeDouble(f.orderFrequency3m())));
        featureList.add(new Feature("order_frequency_trend_3m",
                safeDouble(f.orderFrequencyTrend3m())));
        featureList.add(new Feature("consecutive_missed_orders",
                safeDoubleFromInt(f.consecutiveMissedOrders())));

        // Credit utilisation trends
        featureList.add(new Feature("credit_utilization_3m",
                safeDouble(f.creditUtilization3m())));
        featureList.add(new Feature("credit_utilization_trajectory",
                safeDouble(f.creditUtilizationTrajectory())));
        featureList.add(new Feature("peak_utilization_3m",
                safeDouble(f.peakUtilization3m())));
        featureList.add(new Feature("hit_credit_limit_3m",
                Boolean.TRUE.equals(f.hitCreditLimit3m()) ? 1.0 : 0.0));

        // Partial payment trends
        featureList.add(new Feature("partial_payment_freq_3m",
                safeDouble(f.partialPaymentFreq3m())));
        featureList.add(new Feature("partial_payment_freq_trend_3m",
                safeDouble(f.partialPaymentFreqTrend3m())));
        featureList.add(new Feature("consecutive_partial_payments",
                safeDoubleFromInt(f.consecutivePartialPayments())));

        // Order value trends
        featureList.add(new Feature("avg_order_value_3m",
                safeDouble(f.avgOrderValue3m())));
        featureList.add(new Feature("avg_order_value_trend_3m",
                safeDouble(f.avgOrderValueTrend3m())));
        featureList.add(new Feature("order_value_volatility_3m",
                safeDouble(f.orderValueVolatility3m())));

        // Overall financial health
        featureList.add(new Feature("outstanding_trend_3m",
                safeDouble(f.outstandingTrend3m())));
        featureList.add(new Feature("days_overdue_max",
                safeDoubleFromInt(f.daysOverdueMax())));
        featureList.add(new Feature("payment_to_order_ratio_3m",
                safeDouble(f.paymentToOrderRatio3m())));

        // Construct the example with a dummy NO_DISTRESS label (label not used during inference)
        return new ArrayExample<>(new Label(LABEL_NO_DISTRESS), featureList);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Returns 0.0 when a nullable Double field is null. */
    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    /** Returns 0.0 when a nullable Integer field is null. */
    private double safeDoubleFromInt(Integer value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    /** Safe fallback result when the model is unavailable or inference fails. */
    private DistressResult defaultResult(UUID merchantId) {
        return new DistressResult(merchantId, false, 0.0, "fallback");
    }

    // ── Result record ─────────────────────────────────────────────────────

    /**
     * Outcome of a payment-distress classification run.
     *
     * @param merchantId         Merchant that was evaluated
     * @param isDistressed       {@code true} if the model predicts payment distress
     * @param distressProbability Probability of the DISTRESS class (0.0 – 1.0)
     * @param modelVersion       Name/version of the model that produced this result,
     *                           or {@code "fallback"} when no model was available
     */
    @Builder
    public record DistressResult(
            UUID    merchantId,
            boolean isDistressed,
            double  distressProbability,
            String  modelVersion
    ) {}
}
