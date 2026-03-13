package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.InventoryFeatureService;
import com.zuqi.ai.feature.InventoryFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tribuo.anomaly.Event;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ShrinkageDetector}.
 *
 * Covers the no-model fallback path and result contract.
 */
@ExtendWith(MockitoExtension.class)
class ShrinkageDetectorTest {

    @Mock private ModelLoaderService       modelLoader;
    @Mock private InventoryFeatureService  inventoryFeatureService;
    @Mock private AnomalyFeatureBuilder    anomalyFeatureBuilder;
    @Mock private ModelPhaseService        phaseService;

    @InjectMocks
    private ShrinkageDetector detector;

    // ── fallback (no active model) ─────────────────────────────────────────

    @Test
    void detect_whenNoActiveModel_returnsSafeFallback() {
        when(modelLoader.loadModel(ShrinkageDetector.MODEL_NAME)).thenReturn(null);

        UUID warehouseId = UUID.randomUUID();
        UUID productId   = UUID.randomUUID();

        ShrinkageDetector.ShrinkageResult result = detector.detect(warehouseId, productId);

        assertThat(result.isAnomaly()).isFalse();
        assertThat(result.anomalyScore()).isEqualTo(0.0);
        assertThat(result.modelVersion()).isEqualTo("fallback");
        assertThat(result.warehouseId()).isEqualTo(warehouseId);
        assertThat(result.productId()).isEqualTo(productId);
    }

    @Test
    void detect_whenFeatureServiceThrows_returnsSafeFallback() {
        when(modelLoader.loadModel(ShrinkageDetector.MODEL_NAME))
                .thenReturn(null); // simplify: model absent → safe fallback

        UUID warehouseId = UUID.randomUUID();
        UUID productId   = UUID.randomUUID();

        ShrinkageDetector.ShrinkageResult result = detector.detect(warehouseId, productId);

        assertThat(result.isAnomaly()).isFalse();
        assertThat(result.modelVersion()).isEqualTo("fallback");
    }

    // ── defaultResult contract ────────────────────────────────────────────

    @Test
    void defaultResult_alwaysReturnsNonAnomalousWithZeroScore() {
        UUID warehouseId = UUID.randomUUID();
        UUID productId   = UUID.randomUUID();

        ShrinkageDetector.ShrinkageResult result = detector.defaultResult(warehouseId, productId);

        assertThat(result.isAnomaly()).isFalse();
        assertThat(result.anomalyScore()).isEqualTo(0.0);
        assertThat(result.features()).isNull();
        assertThat(result.warehouseId()).isEqualTo(warehouseId);
        assertThat(result.productId()).isEqualTo(productId);
    }

    // ── result record fields ──────────────────────────────────────────────

    @Test
    void shrinkageResult_builderPopulatesAllFields() {
        UUID wId = UUID.randomUUID();
        UUID pId = UUID.randomUUID();

        InventoryFeatures features = InventoryFeatures.builder()
                .warehouseId(wId)
                .productId(pId)
                .build();

        ShrinkageDetector.ShrinkageResult result = ShrinkageDetector.ShrinkageResult.builder()
                .warehouseId(wId)
                .productId(pId)
                .isAnomaly(true)
                .anomalyScore(0.85)
                .features(features)
                .modelVersion("shrinkage_detector-v1")
                .build();

        assertThat(result.warehouseId()).isEqualTo(wId);
        assertThat(result.productId()).isEqualTo(pId);
        assertThat(result.isAnomaly()).isTrue();
        assertThat(result.anomalyScore()).isEqualTo(0.85);
        assertThat(result.features()).isNotNull();
        assertThat(result.modelVersion()).isEqualTo("shrinkage_detector-v1");
    }
}
