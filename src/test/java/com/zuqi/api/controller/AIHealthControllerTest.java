package com.zuqi.api.controller;

import com.zuqi.ai.dto.AIModelListResponse;
import com.zuqi.ai.dto.AIModelPerformanceResponse;
import com.zuqi.ai.dto.AISystemHealthResponse;
import com.zuqi.ai.service.AIHealthService;
import com.zuqi.domain.ai.ModelStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AIHealthController.
 *
 * Verifies that each endpoint delegates to AIHealthService and wraps the result
 * in a 200 OK response. Business logic lives in the service — this layer only
 * tests delegation and HTTP contract.
 *
 * Blueprint reference: implementation_plan.md Task 1.11
 */
@ExtendWith(MockitoExtension.class)
class AIHealthControllerTest {

    @Mock
    private AIHealthService aiHealthService;

    @InjectMocks
    private AIHealthController aiHealthController;

    // -------------------------------------------------------------------------
    // GET /v1/ai/system/health
    // -------------------------------------------------------------------------

    @Test
    void getSystemHealth_shouldReturn200_withServiceResponse() {
        AISystemHealthResponse health = AISystemHealthResponse.builder()
                .status("UP")
                .timestamp(LocalDateTime.of(2026, 6, 15, 12, 0))
                .modelRegistry(AISystemHealthResponse.ModelRegistryHealth.builder()
                        .status("UP")
                        .totalModels(5)
                        .activeModels(3)
                        .databaseConnection("CONNECTED")
                        .build())
                .featureServices(AISystemHealthResponse.FeatureServicesHealth.builder()
                        .status("UP")
                        .services(List.of(
                                AISystemHealthResponse.FeatureServiceStatus.builder()
                                        .serviceName("MerchantFeatureService")
                                        .status("UP")
                                        .cacheStatus("ACTIVE")
                                        .build()
                        ))
                        .build())
                .llmConnectivity(AISystemHealthResponse.LLMConnectivityHealth.builder()
                        .status("DEGRADED")
                        .llm(AISystemHealthResponse.LlmStatus.builder()
                                .status("UNREACHABLE")
                                .baseUrl("https://rbsai.rbrc.io")
                                .model("qwen3-14b")
                                .message("Connection refused")
                                .build())
                        .cloudLLM(AISystemHealthResponse.CloudLLMStatus.builder()
                                .status("NOT_CONFIGURED")
                                .provider("RBS AI (vLLM)")
                                .message("API key not set")
                                .build())
                        .build())
                .cache(AISystemHealthResponse.CacheHealth.builder()
                        .status("UP")
                        .provider("REDIS")
                        .connection("CONNECTED")
                        .build())
                .additionalInfo(Map.of("version", "1.0.0"))
                .build();

        when(aiHealthService.getSystemHealth()).thenReturn(health);

        ResponseEntity<AISystemHealthResponse> response = aiHealthController.getSystemHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("UP");
        assertThat(response.getBody().modelRegistry().activeModels()).isEqualTo(3);
        assertThat(response.getBody().modelRegistry().totalModels()).isEqualTo(5);
        assertThat(response.getBody().cache().provider()).isEqualTo("REDIS");
        verify(aiHealthService, times(1)).getSystemHealth();
        verifyNoMoreInteractions(aiHealthService);
    }

    @Test
    void getSystemHealth_shouldReturn200_whenSystemIsDegraded() {
        AISystemHealthResponse degraded = AISystemHealthResponse.builder()
                .status("DEGRADED")
                .timestamp(LocalDateTime.now())
                .modelRegistry(AISystemHealthResponse.ModelRegistryHealth.builder()
                        .status("UP").totalModels(0).activeModels(0).databaseConnection("CONNECTED")
                        .build())
                .featureServices(AISystemHealthResponse.FeatureServicesHealth.builder()
                        .status("UP").services(List.of()).build())
                .llmConnectivity(AISystemHealthResponse.LLMConnectivityHealth.builder()
                        .status("DOWN").build())
                .cache(AISystemHealthResponse.CacheHealth.builder()
                        .status("UP").provider("REDIS").connection("CONNECTED").build())
                .build();

        when(aiHealthService.getSystemHealth()).thenReturn(degraded);

        ResponseEntity<AISystemHealthResponse> response = aiHealthController.getSystemHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("DEGRADED");
    }

    // -------------------------------------------------------------------------
    // GET /v1/ai/system/models
    // -------------------------------------------------------------------------

    @Test
    void getActiveModels_shouldReturn200_withModelList() {
        AIModelListResponse modelList = AIModelListResponse.builder()
                .models(List.of(
                        AIModelListResponse.ModelSummary.builder()
                                .id(UUID.randomUUID())
                                .modelName("credit_classifier")
                                .version("2")
                                .status(ModelStatus.ACTIVE)
                                .modelType("CLASSIFICATION")
                                .accuracy(0.87)
                                .promotedAt(LocalDateTime.of(2026, 6, 1, 0, 0))
                                .build(),
                        AIModelListResponse.ModelSummary.builder()
                                .id(UUID.randomUUID())
                                .modelName("demand_forecaster")
                                .version("3")
                                .status(ModelStatus.ACTIVE)
                                .modelType("REGRESSION")
                                .accuracy(0.76)
                                .promotedAt(LocalDateTime.of(2026, 6, 5, 0, 0))
                                .build()
                ))
                .totalCount(2)
                .retrievedAt(LocalDateTime.of(2026, 6, 15, 12, 0))
                .build();

        when(aiHealthService.getActiveModels()).thenReturn(modelList);

        ResponseEntity<AIModelListResponse> response = aiHealthController.getActiveModels();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalCount()).isEqualTo(2);
        assertThat(response.getBody().models()).hasSize(2);
        assertThat(response.getBody().models().get(0).modelName()).isEqualTo("credit_classifier");
        assertThat(response.getBody().models().get(1).modelName()).isEqualTo("demand_forecaster");
        verify(aiHealthService, times(1)).getActiveModels();
        verifyNoMoreInteractions(aiHealthService);
    }

    @Test
    void getActiveModels_shouldReturn200_withEmptyList_whenNoActiveModels() {
        AIModelListResponse empty = AIModelListResponse.builder()
                .models(List.of())
                .totalCount(0)
                .retrievedAt(LocalDateTime.now())
                .build();

        when(aiHealthService.getActiveModels()).thenReturn(empty);

        ResponseEntity<AIModelListResponse> response = aiHealthController.getActiveModels();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalCount()).isZero();
        assertThat(response.getBody().models()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // GET /v1/ai/system/models/{modelName}/performance
    // -------------------------------------------------------------------------

    @Test
    void getModelPerformance_shouldReturn200_withMetricsForRequestedModel() {
        AIModelPerformanceResponse performance = AIModelPerformanceResponse.builder()
                .modelName("demand_forecaster")
                .version("3")
                .status("ACTIVE")
                .currentMetrics(AIModelPerformanceResponse.PerformanceMetrics.builder()
                        .accuracy(0.82)
                        .precision(0.79)
                        .recall(0.85)
                        .f1Score(0.82)
                        .mae(0.15)
                        .rmse(0.22)
                        .customMetrics(Map.of("mape", 12.5))
                        .build())
                .history(List.of(
                        AIModelPerformanceResponse.PerformanceHistory.builder()
                                .recordedAt(LocalDateTime.of(2026, 6, 8, 0, 0))
                                .accuracy(0.80)
                                .precision(0.77)
                                .recall(0.83)
                                .f1Score(0.80)
                                .build()
                ))
                .retrievedAt(LocalDateTime.of(2026, 6, 15, 12, 0))
                .build();

        when(aiHealthService.getModelPerformance("demand_forecaster")).thenReturn(performance);

        ResponseEntity<AIModelPerformanceResponse> response =
                aiHealthController.getModelPerformance("demand_forecaster");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().modelName()).isEqualTo("demand_forecaster");
        assertThat(response.getBody().version()).isEqualTo("3");
        assertThat(response.getBody().currentMetrics().accuracy()).isEqualTo(0.82);
        assertThat(response.getBody().history()).hasSize(1);
        verify(aiHealthService, times(1)).getModelPerformance("demand_forecaster");
        verifyNoMoreInteractions(aiHealthService);
    }

    @Test
    void getModelPerformance_shouldPassModelNameExactlyToService() {
        // Verifies the @PathVariable is forwarded without modification
        String modelName = "shrinkage_detector";
        AIModelPerformanceResponse stub = AIModelPerformanceResponse.builder()
                .modelName(modelName)
                .version("1")
                .status("ACTIVE")
                .currentMetrics(AIModelPerformanceResponse.PerformanceMetrics.builder().build())
                .history(List.of())
                .retrievedAt(LocalDateTime.now())
                .build();

        when(aiHealthService.getModelPerformance(modelName)).thenReturn(stub);

        aiHealthController.getModelPerformance(modelName);

        verify(aiHealthService).getModelPerformance(modelName);
        verify(aiHealthService, never()).getModelPerformance(argThat(s -> !s.equals(modelName)));
    }

    @Test
    void getModelPerformance_shouldReturn200_withNullMetrics_whenModelHasNoHistory() {
        AIModelPerformanceResponse noHistory = AIModelPerformanceResponse.builder()
                .modelName("stockout_predictor")
                .version("1")
                .status("ACTIVE")
                .currentMetrics(AIModelPerformanceResponse.PerformanceMetrics.builder()
                        .accuracy(null)
                        .f1Score(null)
                        .build())
                .history(List.of())
                .retrievedAt(LocalDateTime.now())
                .build();

        when(aiHealthService.getModelPerformance("stockout_predictor")).thenReturn(noHistory);

        ResponseEntity<AIModelPerformanceResponse> response =
                aiHealthController.getModelPerformance("stockout_predictor");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().currentMetrics().accuracy()).isNull();
        assertThat(response.getBody().history()).isEmpty();
    }
}
