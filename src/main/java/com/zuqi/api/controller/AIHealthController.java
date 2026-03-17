package com.zuqi.api.controller;

import com.zuqi.ai.dto.AIModelListResponse;
import com.zuqi.ai.dto.AIModelPerformanceResponse;
import com.zuqi.ai.dto.AISystemHealthResponse;
import com.zuqi.ai.service.AIHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for AI system health monitoring.
 *
 * Provides endpoints for:
 * - Overall AI system health status
 * - Model registry inspection
 * - Model performance metrics
 *
 * Access: SUPER_ADMIN, ADMIN only (configured via Casbin)
 *
 * Blueprint reference: implementation_plan.md Task 1.11
 */
@RestController
@RequestMapping("/v1/ai/system")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI System", description = "AI system health and monitoring endpoints")
public class AIHealthController {

    private final AIHealthService aiHealthService;

    @GetMapping("/health")
    @Operation(
            summary = "Get AI system health status",
            description = "Returns comprehensive health status of all AI components including model registry, feature services, LLM connectivity, and cache",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<AISystemHealthResponse> getSystemHealth() {
        log.debug("AI system health check requested");
        AISystemHealthResponse health = aiHealthService.getSystemHealth();
        return ResponseEntity.ok(health);
    }

    @GetMapping("/models")
    @Operation(
            summary = "List active AI models",
            description = "Returns list of all active models in the registry with version and performance information",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<AIModelListResponse> getActiveModels() {
        log.debug("Active models list requested");
        AIModelListResponse models = aiHealthService.getActiveModels();
        return ResponseEntity.ok(models);
    }

    @GetMapping("/models/{modelName}/performance")
    @Operation(
            summary = "Get model performance metrics",
            description = "Returns detailed performance metrics for a specific model including accuracy, precision, recall, and historical performance",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<AIModelPerformanceResponse> getModelPerformance(
            @PathVariable String modelName) {
        log.debug("Performance metrics requested for model: {}", modelName);
        AIModelPerformanceResponse performance = aiHealthService.getModelPerformance(modelName);
        return ResponseEntity.ok(performance);
    }

    @PostMapping("/models/retrain-all")
    @Operation(
            summary = "Trigger async retraining of all AI models",
            description = "Launches an async synthetic data generation and model training run for all models. Returns immediately.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Map<String, String>> retrainAll() {
        log.info("Retrain-all endpoint invoked");
        aiHealthService.triggerRetrainAll();
        return ResponseEntity.accepted().body(Map.of("message", "Retraining triggered for all models"));
    }

    @PostMapping("/models/{modelName}/retrain")
    @Operation(
            summary = "Trigger retraining of a specific model",
            description = "Enqueues an async retraining run for the specified model. In the current synthetic phase, all models are co-trained together.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Map<String, String>> retrainModel(@PathVariable String modelName) {
        log.info("Retrain endpoint invoked for model: {}", modelName);
        aiHealthService.triggerRetrainModel(modelName);
        return ResponseEntity.accepted().body(Map.of("message", "Retraining triggered for " + modelName));
    }

    @PutMapping("/models/{modelName}/retire")
    @Operation(
            summary = "Retire the active version of a model",
            description = "Sets the active model version to RETIRED status. Throws 400 if no active version exists.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Map<String, String>> retireModel(@PathVariable String modelName) {
        log.info("Retire endpoint invoked for model: {}", modelName);
        aiHealthService.retireModel(modelName);
        return ResponseEntity.ok(Map.of("message", modelName + " retired successfully"));
    }
}
