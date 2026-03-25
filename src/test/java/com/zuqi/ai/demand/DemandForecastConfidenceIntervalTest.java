package com.zuqi.ai.demand;

import com.zuqi.ai.feature.DemandFeatures;
import com.zuqi.ai.feature.OrderFeatureService;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.domain.ai.AIModelRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.regression.Regressor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests that demand forecast confidence intervals (lowerBound/upperBound) are
 * computed correctly from stored residual percentiles and the ±20% fallback.
 */
@ExtendWith(MockitoExtension.class)
class DemandForecastConfidenceIntervalTest {

    @Mock private ModelLoaderService modelLoader;
    @Mock private OrderFeatureService orderFeatureService;
    @Mock private DemandFeatureBuilder featureBuilder;
    @Mock private ModelPhaseService phaseService;
    @Mock private ModelRegistry modelRegistry;

    @InjectMocks
    private DemandForecaster forecaster;

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Model<Regressor> mockModelReturning(double predictedQty) {
        Model<Regressor> model = mock(Model.class);
        Prediction<Regressor> prediction = mock(Prediction.class);
        Regressor regressor = mock(Regressor.class);
        when(regressor.getValues()).thenReturn(new double[]{predictedQty});
        when(prediction.getOutput()).thenReturn(regressor);
        doReturn(prediction).when(model).predict(ArgumentMatchers.<Example<Regressor>>any());
        return model;
    }

    private DemandFeatures stubFeatures(UUID merchantId, UUID productId) {
        DemandFeatures features = mock(DemandFeatures.class);
        lenient().when(features.rollingAvg4w()).thenReturn(BigDecimal.valueOf(100));
        lenient().when(features.rollingAvg12w()).thenReturn(BigDecimal.valueOf(95));
        lenient().when(features.trendDirection()).thenReturn("STABLE");
        lenient().when(features.merchantTenureDays()).thenReturn(200);
        lenient().when(orderFeatureService.computeFeatures(merchantId, productId)).thenReturn(features);
        return features;
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void forecastDemand_withResidualPercentiles_appliesResidualBounds() {
        UUID merchantId = UUID.randomUUID();
        UUID productId  = UUID.randomUUID();

        Model<Regressor> model = mockModelReturning(200.0);
        when(modelLoader.loadModel("demand_forecaster")).thenReturn(model);

        DemandFeatures features = stubFeatures(merchantId, productId);
        @SuppressWarnings("unchecked")
        Example<Regressor> ex1 = mock(Example.class);
        when(featureBuilder.buildRegressionExample(eq(features), any())).thenReturn(ex1);
        when(phaseService.applyModifier(anyDouble(), anyString())).thenAnswer(inv -> inv.getArgument(0));

        // Registry returns residuals: P10 = -30, P90 = +40
        AIModelRegistry registry = new AIModelRegistry();
        registry.setPerformanceMetrics(Map.of("lower_residual", -30.0, "upper_residual", 40.0));
        when(modelRegistry.getActiveModel("demand_forecaster")).thenReturn(Optional.of(registry));

        DemandForecaster.DemandForecast result = forecaster.forecastDemand(merchantId, productId);

        // lower = max(0, 200 + (-30)) = 170; upper = min(10000, 200 + 40) = 240
        assertThat(result.lowerBound()).isEqualTo(170.0);
        assertThat(result.upperBound()).isEqualTo(240.0);
        assertThat(result.predictedQuantity()).isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    @Test
    void forecastDemand_withoutResiduals_fallsBackToTwentyPctBounds() {
        UUID merchantId = UUID.randomUUID();
        UUID productId  = UUID.randomUUID();

        Model<Regressor> model = mockModelReturning(100.0);
        when(modelLoader.loadModel("demand_forecaster")).thenReturn(model);

        DemandFeatures features = stubFeatures(merchantId, productId);
        @SuppressWarnings("unchecked")
        Example<Regressor> ex2 = mock(Example.class);
        when(featureBuilder.buildRegressionExample(eq(features), any())).thenReturn(ex2);
        when(phaseService.applyModifier(anyDouble(), anyString())).thenAnswer(inv -> inv.getArgument(0));

        // Registry returns no residuals
        when(modelRegistry.getActiveModel("demand_forecaster")).thenReturn(Optional.empty());

        DemandForecaster.DemandForecast result = forecaster.forecastDemand(merchantId, productId);

        // lower = 100 * 0.8 = 80; upper = 100 * 1.2 = 120
        assertThat(result.lowerBound()).isEqualTo(80.0);
        assertThat(result.upperBound()).isEqualTo(120.0);
    }

    @Test
    void forecastDemand_lowerBoundClampedToZero() {
        UUID merchantId = UUID.randomUUID();
        UUID productId  = UUID.randomUUID();

        // Very small prediction + large negative residual → lower could go negative
        Model<Regressor> model = mockModelReturning(5.0);
        when(modelLoader.loadModel("demand_forecaster")).thenReturn(model);

        DemandFeatures features = stubFeatures(merchantId, productId);
        @SuppressWarnings("unchecked")
        Example<Regressor> ex3 = mock(Example.class);
        when(featureBuilder.buildRegressionExample(eq(features), any())).thenReturn(ex3);
        when(phaseService.applyModifier(anyDouble(), anyString())).thenAnswer(inv -> inv.getArgument(0));

        AIModelRegistry registry = new AIModelRegistry();
        registry.setPerformanceMetrics(Map.of("lower_residual", -50.0, "upper_residual", 20.0));
        when(modelRegistry.getActiveModel("demand_forecaster")).thenReturn(Optional.of(registry));

        DemandForecaster.DemandForecast result = forecaster.forecastDemand(merchantId, productId);

        // lower = max(0, 5 + (-50)) = max(0, -45) = 0
        assertThat(result.lowerBound()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void forecastDemand_whenNoModel_defaultForecastHasBounds() {
        UUID merchantId = UUID.randomUUID();
        UUID productId  = UUID.randomUUID();

        when(modelLoader.loadModel("demand_forecaster")).thenReturn(null);
        stubFeatures(merchantId, productId);

        DemandForecaster.DemandForecast result = forecaster.forecastDemand(merchantId, productId);

        // Default forecast uses rolling avg with ±30% bounds (not zero)
        assertThat(result.lowerBound()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.upperBound()).isGreaterThanOrEqualTo(result.lowerBound());
    }
}
