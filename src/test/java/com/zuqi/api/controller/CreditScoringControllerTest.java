package com.zuqi.api.controller;

import com.zuqi.ai.credit.CreditEvaluation;
import com.zuqi.ai.credit.CreditLimitAdjustmentJob;
import com.zuqi.ai.credit.CreditScoringOrchestrator;
import com.zuqi.ai.monitoring.PredictionLogger;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.ai.AIPrediction;
import com.zuqi.domain.ai.EntityType;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreditScoringController.
 *
 * Verifies HTTP contract only — delegation to services, correct status codes,
 * and error response shape. Business logic lives in the services.
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.6
 */
@ExtendWith(MockitoExtension.class)
class CreditScoringControllerTest {

    @Mock
    private CreditScoringOrchestrator creditScoringOrchestrator;

    @Mock
    private PredictionLogger predictionLogger;

    @Mock
    private CreditLimitAdjustmentJob creditLimitAdjustmentJob;

    @InjectMocks
    private CreditScoringController creditScoringController;

    private static final UUID MERCHANT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    // -------------------------------------------------------------------------
    // POST /v1/ai/credit/evaluate/{merchantId}
    // -------------------------------------------------------------------------

    @Test
    void evaluateMerchant_shouldReturn200_withEvaluation() {
        CreditEvaluation evaluation = sampleEvaluation(75, "APPROVE");
        when(creditScoringOrchestrator.evaluateMerchant(MERCHANT_ID)).thenReturn(evaluation);

        ResponseEntity<ApiResponse<CreditEvaluation>> response =
                creditScoringController.evaluateMerchant(MERCHANT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().creditScore()).isEqualTo(75);
        assertThat(response.getBody().getData().recommendation()).isEqualTo("APPROVE");
        verify(creditScoringOrchestrator).evaluateMerchant(MERCHANT_ID);
    }

    @Test
    void evaluateMerchant_shouldReturn400_onIllegalArgumentException() {
        when(creditScoringOrchestrator.evaluateMerchant(MERCHANT_ID))
                .thenThrow(new IllegalArgumentException("Merchant not found"));

        ResponseEntity<ApiResponse<CreditEvaluation>> response =
                creditScoringController.evaluateMerchant(MERCHANT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("Merchant not found");
    }

    @Test
    void evaluateMerchant_shouldReturn500_onUnexpectedException() {
        when(creditScoringOrchestrator.evaluateMerchant(MERCHANT_ID))
                .thenThrow(new RuntimeException("LLM unreachable"));

        ResponseEntity<ApiResponse<CreditEvaluation>> response =
                creditScoringController.evaluateMerchant(MERCHANT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("LLM unreachable");
    }

    // -------------------------------------------------------------------------
    // GET /v1/ai/credit/evaluations/{merchantId}
    // -------------------------------------------------------------------------

    @Test
    void getEvaluationHistory_shouldReturn200_withList() {
        AIPrediction p1 = new AIPrediction();
        AIPrediction p2 = new AIPrediction();
        when(predictionLogger.getPredictionHistory(EntityType.MERCHANT, MERCHANT_ID, 10))
                .thenReturn(List.of(p1, p2));

        ResponseEntity<ApiResponse<List<AIPrediction>>> response =
                creditScoringController.getEvaluationHistory(MERCHANT_ID, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(2);
        verify(predictionLogger).getPredictionHistory(EntityType.MERCHANT, MERCHANT_ID, 10);
    }

    @Test
    void getEvaluationHistory_shouldReturn200_withEmptyList_whenNoHistory() {
        when(predictionLogger.getPredictionHistory(EntityType.MERCHANT, MERCHANT_ID, 5))
                .thenReturn(List.of());

        ResponseEntity<ApiResponse<List<AIPrediction>>> response =
                creditScoringController.getEvaluationHistory(MERCHANT_ID, 5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // GET /v1/ai/credit/score/{merchantId}
    // -------------------------------------------------------------------------

    @Test
    void getCurrentScore_shouldReturn200_withPrediction() {
        AIPrediction prediction = new AIPrediction();
        when(predictionLogger.getLatestPrediction(EntityType.MERCHANT, MERCHANT_ID))
                .thenReturn(Optional.of(prediction));

        ResponseEntity<ApiResponse<AIPrediction>> response =
                creditScoringController.getCurrentScore(MERCHANT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isSameAs(prediction);
    }

    @Test
    void getCurrentScore_shouldReturn404_whenNoPredictionExists() {
        when(predictionLogger.getLatestPrediction(EntityType.MERCHANT, MERCHANT_ID))
                .thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<AIPrediction>> response =
                creditScoringController.getCurrentScore(MERCHANT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("No credit evaluation found");
    }

    // -------------------------------------------------------------------------
    // POST /v1/ai/credit/adjust/{merchantId}
    // -------------------------------------------------------------------------

    @Test
    void adjustCreditLimit_shouldReturn200_withAdjustmentResult() {
        CreditLimitAdjustmentJob.CreditAdjustmentResult result =
                new CreditLimitAdjustmentJob.CreditAdjustmentResult(
                        MERCHANT_ID, 50_000.0, 60_000.0, 20.0, "INCREASED", false);
        when(creditLimitAdjustmentJob.adjustCreditLimit(MERCHANT_ID)).thenReturn(result);

        ResponseEntity<ApiResponse<CreditLimitAdjustmentJob.CreditAdjustmentResult>> response =
                creditScoringController.adjustCreditLimit(MERCHANT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().action()).isEqualTo("INCREASED");
        assertThat(response.getBody().getData().newLimitKes()).isEqualTo(60_000.0);
        verify(creditLimitAdjustmentJob).adjustCreditLimit(MERCHANT_ID);
    }

    @Test
    void adjustCreditLimit_shouldReturn400_onIllegalArgument() {
        when(creditLimitAdjustmentJob.adjustCreditLimit(MERCHANT_ID))
                .thenThrow(new IllegalArgumentException("Merchant not found"));

        ResponseEntity<ApiResponse<CreditLimitAdjustmentJob.CreditAdjustmentResult>> response =
                creditScoringController.adjustCreditLimit(MERCHANT_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("Merchant not found");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CreditEvaluation sampleEvaluation(int score, String recommendation) {
        return CreditEvaluation.builder()
                .merchantId(MERCHANT_ID.toString())
                .creditScore(score)
                .riskCategory(CreditEvaluation.RiskCategory.LOW)
                .recommendedCreditLimit(BigDecimal.valueOf(200_000))
                .currentCreditLimit(BigDecimal.valueOf(150_000))
                .recommendation(recommendation)
                .reasoning("Good payment history")
                .strengthFactors(List.of("On-time payments"))
                .riskFactors(List.of())
                .recommendations(List.of())
                .evaluatedAt(LocalDateTime.now())
                .modelVersion("credit_scoring-v1")
                .build();
    }
}
