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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentDistressClassifier}.
 *
 * Covers: no-model fallback, null-model fallback, feature-service exception fallback,
 * and DistressResult record structure.
 */
@ExtendWith(MockitoExtension.class)
class PaymentDistressClassifierTest {

    @Mock private ModelLoaderService      modelLoader;
    @Mock private PaymentFeatureService   paymentFeatureService;
    @Mock private ModelPhaseService       phaseService;

    @InjectMocks
    private PaymentDistressClassifier classifier;

    // ── safe fallback: no model available ─────────────────────────────────

    @Test
    void classify_whenModelLoaderThrows_returnsSafeDefault() {
        when(modelLoader.loadModel(anyString()))
                .thenThrow(new RuntimeException("Model not found"));

        PaymentDistressClassifier.DistressResult result =
                classifier.classify(UUID.randomUUID());

        assertThat(result.isDistressed()).isFalse();
        assertThat(result.distressProbability()).isEqualTo(0.0);
        assertThat(result.modelVersion()).isEqualTo("fallback");
        verifyNoInteractions(paymentFeatureService);
    }

    @Test
    void classify_whenModelLoaderReturnsNull_returnsSafeDefault() {
        when(modelLoader.loadModel(anyString())).thenReturn(null);

        PaymentDistressClassifier.DistressResult result =
                classifier.classify(UUID.randomUUID());

        assertThat(result.isDistressed()).isFalse();
        assertThat(result.modelVersion()).isEqualTo("fallback");
        verifyNoInteractions(paymentFeatureService);
    }

    // ── safe fallback: feature computation fails ──────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void classify_whenFeatureServiceThrows_returnsSafeDefault() {
        org.tribuo.Model model = mock(org.tribuo.Model.class);
        when(modelLoader.loadModel(anyString())).thenReturn(model);
        when(paymentFeatureService.computeMerchantTrendFeatures(any()))
                .thenThrow(new RuntimeException("Feature query failed"));

        PaymentDistressClassifier.DistressResult result =
                classifier.classify(UUID.randomUUID());

        assertThat(result.isDistressed()).isFalse();
        assertThat(result.modelVersion()).isEqualTo("fallback");
    }

    // ── DistressResult record structure ──────────────────────────────────

    @Test
    void distressResult_recordAccessorsReturnCorrectValues() {
        UUID merchantId = UUID.randomUUID();

        PaymentDistressClassifier.DistressResult result =
                new PaymentDistressClassifier.DistressResult(merchantId, true, 0.82, "payment_distress_classifier");

        assertThat(result.merchantId()).isEqualTo(merchantId);
        assertThat(result.isDistressed()).isTrue();
        assertThat(result.distressProbability()).isEqualTo(0.82);
        assertThat(result.modelVersion()).isEqualTo("payment_distress_classifier");
    }

    @Test
    void distressResult_defaultFallback_hasExpectedValues() {
        UUID merchantId = UUID.randomUUID();

        // Trigger the fallback path by making the loader return null
        when(modelLoader.loadModel(anyString())).thenReturn(null);

        PaymentDistressClassifier.DistressResult result = classifier.classify(merchantId);

        assertThat(result.merchantId()).isEqualTo(merchantId);
        assertThat(result.isDistressed()).isFalse();
        assertThat(result.distressProbability()).isEqualTo(0.0);
        assertThat(result.modelVersion()).isEqualTo("fallback");
    }

}
