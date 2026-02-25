package com.zuqi.api.controller;

import com.zuqi.ai.prediction.PredictionAlertService;
import com.zuqi.ai.prediction.RepPerformancePredictor;
import com.zuqi.ai.prediction.StockoutPredictor;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.repository.StockRepository;
import com.zuqi.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for AI-powered predictions: stockout risk and rep performance.
 *
 * Authorization via Casbin policy.csv.
 *
 * Blueprint reference: implementation_plan.md Phase 4, Step 8
 */
@RestController
@RequestMapping("/v1/ai/prediction")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI - Predictions", description = "Stockout risk and sales rep performance predictions")
public class AiPredictionController {

    private final StockoutPredictor        stockoutPredictor;
    private final RepPerformancePredictor  repPerformancePredictor;
    private final PredictionAlertService   predictionAlertService;
    private final StockRepository          stockRepository;
    private final UserRepository           userRepository;

    // ── GET /stockout/{warehouseId} ───────────────────────────────────────

    @GetMapping("/stockout/{warehouseId}")
    @Operation(
            summary = "Batch stockout predictions for a warehouse",
            description = "Runs stockout prediction for every active product in the warehouse and raises alerts for high-risk items"
    )
    public ResponseEntity<ApiResponse<StockoutBatchResponse>> getStockoutPredictions(
            @PathVariable UUID warehouseId,
            @Parameter(required = true) @RequestParam UUID distributorId) {

        log.info("GET /v1/ai/prediction/stockout/{} distributor={}", warehouseId, distributorId);

        try {
            // Fetch all active product-stock entries for this warehouse
            List<UUID> productIds = stockRepository
                    .findByWarehouseId(warehouseId, PageRequest.of(0, 500))
                    .stream()
                    .map(s -> s.getProduct().getId())
                    .toList();

            List<StockoutPredictor.StockoutResult> results = productIds.stream()
                    .map(productId -> {
                        StockoutPredictor.StockoutResult r = stockoutPredictor.predict(warehouseId, productId);
                        // Raise alert if above threshold
                        predictionAlertService.evaluateStockoutAndAlert(r, distributorId);
                        return r;
                    })
                    .toList();

            long atRisk = results.stream()
                    .filter(r -> r.stockoutProbability() >= 0.5)
                    .count();

            StockoutBatchResponse response = new StockoutBatchResponse(
                    warehouseId, results.size(), (int) atRisk, results);

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Stockout batch prediction failed for warehouse={}: {}", warehouseId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to run stockout predictions: " + e.getMessage()));
        }
    }

    // ── GET /rep-performance ──────────────────────────────────────────────

    @GetMapping("/rep-performance")
    @Operation(
            summary = "Performance predictions for all sales reps in a distributor",
            description = "Runs performance prediction for every active SALES_REP and raises alerts for at-risk reps"
    )
    public ResponseEntity<ApiResponse<RepBatchResponse>> getAllRepPerformance(
            @Parameter(required = true) @RequestParam UUID distributorId) {

        log.info("GET /v1/ai/prediction/rep-performance distributor={}", distributorId);

        try {
            List<RepPerformancePredictor.RepPerformanceResult> results =
                    userRepository.findByDistributorIdAndActiveTrue(distributorId)
                            .stream()
                            .filter(u -> u.getRoles().stream()
                                    .anyMatch(r -> "SALES_REP".equals(r.getName())))
                            .map(u -> {
                                RepPerformancePredictor.RepPerformanceResult r =
                                        repPerformancePredictor.predict(u.getId());
                                predictionAlertService.evaluateRepPerformanceAndAlert(r, distributorId);
                                return r;
                            })
                            .toList();

            long atRisk = results.stream()
                    .filter(r -> "AT_RISK".equals(r.performanceTier())
                            || "CRITICAL".equals(r.performanceTier()))
                    .count();

            RepBatchResponse response = new RepBatchResponse(results.size(), (int) atRisk, results);
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Batch rep performance prediction failed for distributor={}: {}",
                    distributorId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to run rep performance predictions: " + e.getMessage()));
        }
    }

    // ── GET /rep-performance/{repId} ──────────────────────────────────────

    @GetMapping("/rep-performance/{repId}")
    @Operation(summary = "Performance prediction for a single sales rep")
    public ResponseEntity<ApiResponse<RepPerformancePredictor.RepPerformanceResult>> getSingleRepPerformance(
            @PathVariable UUID repId,
            @Parameter(required = true) @RequestParam UUID distributorId) {

        log.info("GET /v1/ai/prediction/rep-performance/{}", repId);

        try {
            RepPerformancePredictor.RepPerformanceResult result = repPerformancePredictor.predict(repId);
            predictionAlertService.evaluateRepPerformanceAndAlert(result, distributorId);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Rep performance prediction failed for rep={}: {}", repId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to predict rep performance: " + e.getMessage()));
        }
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────

    public record StockoutBatchResponse(
            UUID warehouseId,
            int  totalProducts,
            int  atRiskCount,
            List<StockoutPredictor.StockoutResult> predictions
    ) {}

    public record RepBatchResponse(
            int  totalReps,
            int  atRiskCount,
            List<RepPerformancePredictor.RepPerformanceResult> predictions
    ) {}
}
