package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.PaymentFeatureService;
import com.zuqi.ai.feature.PaymentFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.anomaly.Event;
import org.tribuo.impl.ArrayExample;

import java.util.UUID;

/**
 * Detects payment anomalies using a trained LibSVM one-class anomaly model.
 *
 * Blueprint reference: plan.md Section 6.3 - PaymentAnomalyDetector
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentAnomalyDetector {

    static final String MODEL_NAME = "payment_anomaly_detector";

    private final ModelLoaderService    modelLoader;
    private final PaymentFeatureService paymentFeatureService;
    private final AnomalyFeatureBuilder anomalyFeatureBuilder;

    /**
     * Detect payment anomaly for a specific payment.
     *
     * @param paymentId  Payment to evaluate
     * @param merchantId Merchant who made the payment
     * @return PaymentAnomalyResult with anomaly flag and score
     */
    public PaymentAnomalyResult detect(UUID paymentId, UUID merchantId) {
        try {
            // 1. Load active model
            Model<Event> model = modelLoader.loadModel(MODEL_NAME);
            if (model == null) {
                log.warn("No active model found for {}, returning safe default", MODEL_NAME);
                return defaultResult(paymentId, merchantId);
            }

            // 2. Compute payment features
            PaymentFeatures features = paymentFeatureService.computePaymentFeatures(paymentId);

            // 3. Build Tribuo example
            ArrayExample<Event> example = anomalyFeatureBuilder.buildPaymentExample(features);

            // 4. Predict
            Prediction<Event> prediction = model.predict(example);

            // 5. Extract result
            boolean isAnomaly   = prediction.getOutput().getType() == Event.EventType.ANOMALOUS;
            double  anomalyScore = normaliseScore(prediction.getOutput().getScore());

            log.debug("Payment anomaly check: paymentId={} merchantId={} anomaly={} score={}",
                    paymentId, merchantId, isAnomaly, String.format("%.3f", anomalyScore));

            return PaymentAnomalyResult.builder()
                    .paymentId(paymentId)
                    .merchantId(merchantId)
                    .isAnomaly(isAnomaly)
                    .anomalyScore(anomalyScore)
                    .features(features)
                    .modelVersion(MODEL_NAME)
                    .build();

        } catch (Exception e) {
            log.error("Payment anomaly detection failed for payment={} merchant={}: {}",
                    paymentId, merchantId, e.getMessage(), e);
            return defaultResult(paymentId, merchantId);
        }
    }

    PaymentAnomalyResult defaultResult(UUID paymentId, UUID merchantId) {
        return PaymentAnomalyResult.builder()
                .paymentId(paymentId)
                .merchantId(merchantId)
                .isAnomaly(false)
                .anomalyScore(0.0)
                .modelVersion("fallback")
                .build();
    }

    private double normaliseScore(double rawScore) {
        double clamped = Math.max(-5.0, Math.min(5.0, rawScore));
        return 1.0 / (1.0 + Math.exp(clamped));
    }

    // ── Result record ─────────────────────────────────────────────────────

    @Builder
    public record PaymentAnomalyResult(
            UUID paymentId,
            UUID merchantId,
            boolean isAnomaly,
            double anomalyScore,
            PaymentFeatures features,
            String modelVersion
    ) {}
}
