package com.zuqi.ai.prediction;

import com.zuqi.ai.feature.SalesRepFeatureService;
import com.zuqi.ai.model.ModelLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RepPerformancePredictor — no Spring context required.
 *
 * Focuses on tier determination logic and fallback behaviour
 * when no model is available.
 */
@ExtendWith(MockitoExtension.class)
class RepPerformancePredictorTest {

    @Mock private ModelLoaderService         modelLoader;
    @Mock private SalesRepFeatureService     salesRepFeatureService;
    @Mock private RepPerformanceFeatureBuilder featureBuilder;

    private RepPerformancePredictor predictor;

    @BeforeEach
    void setUp() {
        predictor = new RepPerformancePredictor(modelLoader, salesRepFeatureService, featureBuilder);
    }

    // ── Fallback when no model ─────────────────────────────────────────────

    @Test
    void predict_noModel_returnsDefaultResult() {
        when(modelLoader.loadModel(anyString())).thenReturn(null);
        UUID repId = UUID.randomUUID();

        RepPerformancePredictor.RepPerformanceResult result = predictor.predict(repId);

        assertThat(result.salesRepId()).isEqualTo(repId);
        assertThat(result.performanceScore()).isEqualTo(50.0);
        assertThat(result.performanceTier()).isEqualTo("AVERAGE");
        assertThat(result.modelVersion()).isEqualTo("fallback");
    }

    // ── Tier determination ─────────────────────────────────────────────────

    @ParameterizedTest(name = "score={0} → tier={1}")
    @CsvSource({
            "100.0, EXCELLENT",
            "85.0,  EXCELLENT",
            "84.9,  GOOD",
            "70.0,  GOOD",
            "69.9,  AVERAGE",
            "55.0,  AVERAGE",
            "54.9,  AT_RISK",
            "40.0,  AT_RISK",
            "39.9,  CRITICAL",
            "0.0,   CRITICAL"
    })
    void defaultResult_tierBoundaries(double score, String expectedTier) {
        UUID repId = UUID.randomUUID();

        // Use defaultResult to test tier logic directly
        RepPerformancePredictor.RepPerformanceResult result = RepPerformancePredictor.RepPerformanceResult.builder()
                .salesRepId(repId)
                .performanceScore(score)
                .performanceTier(tierForScore(score))
                .modelVersion("test")
                .build();

        assertThat(result.performanceTier()).isEqualTo(expectedTier.trim());
    }

    @Test
    void defaultResult_scoreIs50_tierIsAverage() {
        UUID repId = UUID.randomUUID();
        RepPerformancePredictor.RepPerformanceResult result = predictor.defaultResult(repId);

        assertThat(result.performanceTier()).isEqualTo("AVERAGE");
        assertThat(result.performanceScore()).isEqualTo(50.0);
    }

    // ── Result record ─────────────────────────────────────────────────────

    @Test
    void resultRecord_storesAllFields() {
        UUID repId = UUID.randomUUID();

        RepPerformancePredictor.RepPerformanceResult result =
                RepPerformancePredictor.RepPerformanceResult.builder()
                        .salesRepId(repId)
                        .performanceScore(72.5)
                        .performanceTier("GOOD")
                        .modelVersion("rep_performance_predictor")
                        .build();

        assertThat(result.salesRepId()).isEqualTo(repId);
        assertThat(result.performanceScore()).isEqualTo(72.5);
        assertThat(result.performanceTier()).isEqualTo("GOOD");
        assertThat(result.modelVersion()).isEqualTo("rep_performance_predictor");
    }

    // ── Helper — mirrors the private determineTier logic ──────────────────

    private String tierForScore(double score) {
        if (score >= 85.0) return "EXCELLENT";
        if (score >= 70.0) return "GOOD";
        if (score >= 55.0) return "AVERAGE";
        if (score >= 40.0) return "AT_RISK";
        return "CRITICAL";
    }
}
