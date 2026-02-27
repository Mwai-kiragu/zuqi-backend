package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.PaymentFeatureService;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentAnomalyDetector}.
 *
 * Covers the no-model fallback path and result contract.
 */
@ExtendWith(MockitoExtension.class)
class PaymentAnomalyDetectorTest {

    @Mock private ModelLoaderService      modelLoader;
    @Mock private PaymentFeatureService   paymentFeatureService;
    @Mock private AnomalyFeatureBuilder   anomalyFeatureBuilder;
    @Mock private ModelPhaseService       phaseService;

    @InjectMocks
    private PaymentAnomalyDetector detector;

    // ── fallback (no active model) ─────────────────────────────────────────

    @Test
    void detect_whenNoActiveModel_returnsSafeFallback() {
        when(modelLoader.loadModel(PaymentAnomalyDetector.MODEL_NAME)).thenReturn(null);

        UUID paymentId  = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        PaymentAnomalyDetector.PaymentAnomalyResult result =
                detector.detect(paymentId, merchantId);

        assertThat(result.isAnomaly()).isFalse();
        assertThat(result.anomalyScore()).isEqualTo(0.0);
        assertThat(result.modelVersion()).isEqualTo("fallback");
        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.merchantId()).isEqualTo(merchantId);
    }

    // ── defaultResult contract ────────────────────────────────────────────

    @Test
    void defaultResult_alwaysReturnsNonAnomalousWithZeroScore() {
        UUID paymentId  = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        PaymentAnomalyDetector.PaymentAnomalyResult result =
                detector.defaultResult(paymentId, merchantId);

        assertThat(result.isAnomaly()).isFalse();
        assertThat(result.anomalyScore()).isEqualTo(0.0);
        assertThat(result.features()).isNull();
    }

    // ── result record fields ──────────────────────────────────────────────

    @Test
    void paymentAnomalyResult_builderPopulatesAllFields() {
        UUID pId = UUID.randomUUID();
        UUID mId = UUID.randomUUID();

        PaymentAnomalyDetector.PaymentAnomalyResult result =
                PaymentAnomalyDetector.PaymentAnomalyResult.builder()
                        .paymentId(pId)
                        .merchantId(mId)
                        .isAnomaly(true)
                        .anomalyScore(0.78)
                        .features(null)
                        .modelVersion("payment_anomaly_detector-v1")
                        .build();

        assertThat(result.paymentId()).isEqualTo(pId);
        assertThat(result.merchantId()).isEqualTo(mId);
        assertThat(result.isAnomaly()).isTrue();
        assertThat(result.anomalyScore()).isEqualTo(0.78);
        assertThat(result.modelVersion()).isEqualTo("payment_anomaly_detector-v1");
    }
}
