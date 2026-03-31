package com.zuqi.ai.prediction;

import com.zuqi.ai.feature.SalesRepFeatureService;
import com.zuqi.ai.feature.SalesRepFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.impl.ArrayExample;
import org.tribuo.regression.Regressor;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * XGBoost regressor that predicts a sales rep's performance score (0–100).
 *
 * Blueprint reference: implementation_plan.md Phase 4, Step 7b
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RepPerformancePredictor {

    static final String MODEL_NAME = "rep_performance_predictor";

    private final ModelLoaderService          modelLoader;
    private final SalesRepFeatureService      salesRepFeatureService;
    private final RepPerformanceFeatureBuilder featureBuilder;
    private final ModelPhaseService           phaseService;

    /**
     * Predict performance score for a sales rep over the current month.
     */
    public RepPerformanceResult predict(UUID salesRepId) {
        try {
            Model<Regressor> model = modelLoader.loadModel(MODEL_NAME);
            if (model == null) {
                log.warn("No active model for {}, returning safe default", MODEL_NAME);
                return defaultResult(salesRepId);
            }

            // Use trailing 30 days to avoid instability on the 1st of each month
            LocalDateTime periodStart = LocalDateTime.now().minusDays(30)
                    .withHour(0).withMinute(0).withSecond(0);
            LocalDateTime periodEnd   = LocalDateTime.now();

            SalesRepFeatures features = salesRepFeatureService.computeFeatures(
                    salesRepId, periodStart, periodEnd);
            ArrayExample<Regressor> example = featureBuilder.buildExample(features);

            Prediction<Regressor> prediction = model.predict(example);
            double rawScore = prediction.getOutput().getValues()[0];

            // Clamp to [0, 100], then apply SYNTHETIC-phase modifier
            double score = phaseService.applyModifier(
                    Math.max(0.0, Math.min(100.0, rawScore)), MODEL_NAME);
            String tier  = determineTier(score);

            log.debug("Rep performance: salesRepId={} score={} tier={}",
                    salesRepId, String.format("%.1f", score), tier);

            return RepPerformanceResult.builder()
                    .salesRepId(salesRepId)
                    .performanceScore(score)
                    .performanceTier(tier)
                    .modelVersion(MODEL_NAME)
                    .build();

        } catch (Exception e) {
            log.error("Rep performance prediction failed for rep={}: {}", salesRepId, e.getMessage(), e);
            return defaultResult(salesRepId);
        } catch (Error e) {
            log.error("Fatal error in rep performance prediction for rep={} (native library issue?): {}", salesRepId, e.getMessage(), e);
            return defaultResult(salesRepId);
        }
    }

    RepPerformanceResult defaultResult(UUID salesRepId) {
        return RepPerformanceResult.builder()
                .salesRepId(salesRepId)
                .performanceScore(50.0)
                .performanceTier("AVERAGE")
                .modelVersion("fallback")
                .build();
    }

    private String determineTier(double score) {
        if (score >= 85.0) return "EXCELLENT";
        if (score >= 70.0) return "GOOD";
        if (score >= 55.0) return "AVERAGE";
        if (score >= 40.0) return "AT_RISK";
        return "CRITICAL";
    }

    // ── Result record ─────────────────────────────────────────────────────

    @Builder
    public record RepPerformanceResult(
            UUID   salesRepId,
            double performanceScore,
            String performanceTier,
            String modelVersion
    ) {}
}
