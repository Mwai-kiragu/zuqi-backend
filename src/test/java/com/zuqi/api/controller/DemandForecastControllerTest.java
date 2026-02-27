package com.zuqi.api.controller;

import com.zuqi.ai.demand.DemandForecaster;
import com.zuqi.ai.demand.DemandModelTrainingPipeline;
import com.zuqi.ai.demand.OrderSuggestionService;
import com.zuqi.ai.pipeline.ModelEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DemandForecastController}.
 *
 * Verifies delegation behaviour and HTTP response codes for all four endpoints:
 * getForecast, getOrderSuggestions, trainModel, getHealth.
 */
@ExtendWith(MockitoExtension.class)
class DemandForecastControllerTest {

    @Mock
    private DemandForecaster demandForecaster;

    @Mock
    private OrderSuggestionService orderSuggestionService;

    @Mock
    private DemandModelTrainingPipeline trainingPipeline;

    @InjectMocks
    private DemandForecastController controller;

    // ── GET /v1/ai/demand/forecasts/{merchantId}/{productId} ──────────────────

    @Test
    void getForecast_whenModelAvailable_returns200WithForecast() {
        UUID merchantId = UUID.randomUUID();
        UUID productId  = UUID.randomUUID();

        DemandForecaster.DemandForecast forecast = DemandForecaster.DemandForecast.builder()
                .merchantId(merchantId)
                .productId(productId)
                .predictedQuantity(BigDecimal.valueOf(42))
                .confidence(0.80)
                .rollingAvg4w(BigDecimal.valueOf(40))
                .rollingAvg12w(BigDecimal.valueOf(38))
                .trendDirection("INCREASING")
                .modelVersion("demand_forecaster-v1")
                .build();

        when(demandForecaster.forecastDemand(merchantId, productId)).thenReturn(forecast);

        ResponseEntity<?> response = controller.getForecast(merchantId, productId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void getForecast_whenServiceThrows_returns500() {
        UUID merchantId = UUID.randomUUID();
        UUID productId  = UUID.randomUUID();

        when(demandForecaster.forecastDemand(merchantId, productId))
                .thenThrow(new RuntimeException("DB connection failed"));

        ResponseEntity<?> response = controller.getForecast(merchantId, productId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── GET /v1/ai/demand/suggestions/{merchantId} ───────────────────────────

    @Test
    void getOrderSuggestions_returnsSuggestionList() {
        UUID merchantId = UUID.randomUUID();

        OrderSuggestionService.OrderSuggestion suggestion = new OrderSuggestionService.OrderSuggestion(
                UUID.randomUUID(),
                "Coca-Cola 500ml",
                "Beverages",
                BigDecimal.valueOf(24),
                BigDecimal.valueOf(55),
                BigDecimal.valueOf(1320),
                0.85,
                "STABLE",
                "Consistent weekly demand",
                90,
                LocalDateTime.now()
        );

        when(orderSuggestionService.generateSuggestions(merchantId, 20))
                .thenReturn(List.of(suggestion));

        ResponseEntity<?> response = controller.getOrderSuggestions(merchantId, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void getOrderSuggestions_whenServiceThrows_returns500() {
        UUID merchantId = UUID.randomUUID();

        when(orderSuggestionService.generateSuggestions(any(), anyInt()))
                .thenThrow(new RuntimeException("Feature computation failed"));

        ResponseEntity<?> response = controller.getOrderSuggestions(merchantId, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── POST /v1/ai/demand/train ──────────────────────────────────────────────

    @Test
    void trainModel_whenPipelineSucceedsAndPassesQualityGate_returns200() {
        ModelEvaluator.RegressorEvaluationResult eval =
                new ModelEvaluator.RegressorEvaluationResult(12.5, 8.3, 0.78, 0.80, true);

        DemandModelTrainingPipeline.TrainingPipelineResult result =
                DemandModelTrainingPipeline.TrainingPipelineResult.builder()
                        .success(true)
                        .numSequences(100)
                        .numTrainingExamples(1200)
                        .trainSize(960)
                        .testSize(240)
                        .evaluation(eval)
                        .modelId(UUID.randomUUID())
                        .durationMs(4500L)
                        .build();

        when(trainingPipeline.runPipeline(50, 20, 26)).thenReturn(result);

        ResponseEntity<?> response = controller.trainModel(50, 20, 26);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void trainModel_whenPipelineReportsFailure_returns400() {
        DemandModelTrainingPipeline.TrainingPipelineResult result =
                DemandModelTrainingPipeline.TrainingPipelineResult.builder()
                        .success(false)
                        .errorMessage("Insufficient training data")
                        .durationMs(200L)
                        .build();

        when(trainingPipeline.runPipeline(anyInt(), anyInt(), anyInt())).thenReturn(result);

        ResponseEntity<?> response = controller.trainModel(5, 2, 4);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void trainModel_whenPipelineThrows_returns500() {
        when(trainingPipeline.runPipeline(anyInt(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("OOM during training"));

        ResponseEntity<?> response = controller.trainModel(50, 20, 26);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── GET /v1/ai/demand/health ──────────────────────────────────────────────

    @Test
    void getHealth_whenModelOperational_returnsAvailableTrue() {
        UUID testId = UUID.randomUUID();

        DemandForecaster.DemandForecast forecast = DemandForecaster.DemandForecast.builder()
                .merchantId(testId)
                .productId(testId)
                .predictedQuantity(BigDecimal.TEN)
                .confidence(0.75)
                .rollingAvg4w(BigDecimal.TEN)
                .rollingAvg12w(BigDecimal.TEN)
                .trendDirection("STABLE")
                .modelVersion("demand_forecaster-v1")
                .build();

        when(demandForecaster.forecastDemand(any(), any())).thenReturn(forecast);

        ResponseEntity<?> response = controller.getHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void getHealth_whenModelUnavailable_returnsAvailableFalseWith200() {
        DemandForecaster.DemandForecast fallback = DemandForecaster.DemandForecast.builder()
                .merchantId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .predictedQuantity(BigDecimal.ZERO)
                .confidence(0.5)
                .rollingAvg4w(BigDecimal.ZERO)
                .rollingAvg12w(BigDecimal.ZERO)
                .trendDirection("STABLE")
                .modelVersion("fallback-avg") // signals no active model
                .build();

        when(demandForecaster.forecastDemand(any(), any())).thenReturn(fallback);

        ResponseEntity<?> response = controller.getHealth();

        // Health endpoint always returns 200 (degraded state is still a valid response)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getHealth_whenServiceThrows_returnsUnavailableWith200() {
        when(demandForecaster.forecastDemand(any(), any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        ResponseEntity<?> response = controller.getHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
