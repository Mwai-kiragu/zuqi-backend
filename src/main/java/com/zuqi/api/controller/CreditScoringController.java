package com.zuqi.api.controller;

import com.zuqi.ai.credit.CreditEvaluation;
import com.zuqi.ai.credit.CreditScoringOrchestrator;
import com.zuqi.ai.monitoring.PredictionLogger;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.ai.AIPrediction;
import com.zuqi.domain.ai.EntityType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST endpoints for AI-powered credit scoring.
 *
 * Endpoints:
 * - POST /v1/ai/credit/evaluate/{merchantId} - Evaluate merchant creditworthiness
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.6
 */
@RestController
@RequestMapping("/v1/ai/credit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Credit Scoring", description = "LLM-powered merchant credit evaluation")
public class CreditScoringController {

    private final CreditScoringOrchestrator creditScoringOrchestrator;
    private final PredictionLogger predictionLogger;

    /**
     * Evaluate merchant credit risk using AI.
     *
     * Process:
     * 1. Computes merchant features
     * 2. Finds similar merchants (RAG)
     * 3. Invokes LLM for evaluation
     * 4. Applies business rules
     * 5. Returns structured credit evaluation
     *
     * @param merchantId Merchant to evaluate
     * @return Credit evaluation with score, recommended limit, and reasoning
     */
    @PostMapping("/evaluate/{merchantId}")
    @Operation(
            summary = "Evaluate merchant credit risk",
            description = "Uses LLM (Ollama Qwen 2.5 32B) to evaluate merchant creditworthiness based on " +
                    "payment history, order patterns, and peer comparison. Returns credit score (0-100), " +
                    "recommended credit limit, risk category, and detailed reasoning."
    )
    public ResponseEntity<ApiResponse<CreditEvaluation>> evaluateMerchant(
            @PathVariable UUID merchantId
    ) {
        log.info("Credit evaluation requested for merchant {}", merchantId);

        try {
            CreditEvaluation evaluation = creditScoringOrchestrator.evaluateMerchant(merchantId);

            log.info("Credit evaluation completed for merchant {}: score={}, recommendation={}",
                    merchantId, evaluation.creditScore(), evaluation.recommendation());

            return ResponseEntity.ok(ApiResponse.success(evaluation));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid merchant ID for credit evaluation: {}", merchantId);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("Credit evaluation failed for merchant {}: {}", merchantId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Credit evaluation failed: " + e.getMessage()));
        }
    }

    /**
     * Get credit evaluation history for a merchant.
     *
     * Returns all historical credit evaluations from the prediction log.
     *
     * @param merchantId Merchant ID
     * @param limit Maximum number of evaluations to return (default: 10)
     * @return List of historical credit evaluations
     */
    @GetMapping("/evaluations/{merchantId}")
    @Operation(
            summary = "Get credit evaluation history",
            description = "Returns historical credit evaluations for a merchant from the AI prediction log. " +
                    "Useful for tracking credit score changes over time."
    )
    public ResponseEntity<ApiResponse<List<AIPrediction>>> getEvaluationHistory(
            @PathVariable UUID merchantId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        log.info("Fetching credit evaluation history for merchant {}, limit={}", merchantId, limit);

        try {
            List<AIPrediction> history = predictionLogger.getPredictionHistory(
                    EntityType.MERCHANT,
                    merchantId,
                    limit
            );

            log.info("Found {} historical evaluations for merchant {}", history.size(), merchantId);
            return ResponseEntity.ok(ApiResponse.success(history));

        } catch (Exception e) {
            log.error("Failed to fetch evaluation history for merchant {}: {}", merchantId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch evaluation history: " + e.getMessage()));
        }
    }

    /**
     * Get the latest credit score for a merchant.
     *
     * Returns the most recent credit evaluation from the prediction log.
     *
     * @param merchantId Merchant ID
     * @return Latest credit evaluation or 404 if none exists
     */
    @GetMapping("/score/{merchantId}")
    @Operation(
            summary = "Get current credit score",
            description = "Returns the most recent credit evaluation for a merchant. " +
                    "Returns 404 if merchant has never been evaluated."
    )
    public ResponseEntity<ApiResponse<AIPrediction>> getCurrentScore(
            @PathVariable UUID merchantId
    ) {
        log.info("Fetching current credit score for merchant {}", merchantId);

        try {
            Optional<AIPrediction> latestScore = predictionLogger.getLatestPrediction(
                    EntityType.MERCHANT,
                    merchantId
            );

            if (latestScore.isEmpty()) {
                log.warn("No credit score found for merchant {}", merchantId);
                return ResponseEntity.status(404)
                        .body(ApiResponse.error("No credit evaluation found for merchant"));
            }

            log.info("Found credit score for merchant {}", merchantId);
            return ResponseEntity.ok(ApiResponse.success(latestScore.get()));

        } catch (Exception e) {
            log.error("Failed to fetch current score for merchant {}: {}", merchantId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch current score: " + e.getMessage()));
        }
    }
}
