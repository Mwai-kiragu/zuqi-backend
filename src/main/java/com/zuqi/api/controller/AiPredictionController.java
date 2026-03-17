package com.zuqi.api.controller;

import com.zuqi.ai.prediction.PredictionAlertService;
import com.zuqi.ai.prediction.RepPerformancePredictor;
import com.zuqi.ai.prediction.StockoutPredictor;
import com.zuqi.ai.prediction.StockoutTrainingPipeline;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.inventory.Stock;
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
    private final StockoutTrainingPipeline stockoutTrainingPipeline;
    private final DataPhaseTracker         dataPhaseTracker;

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
            List<Stock> stocks = stockRepository
                    .findByWarehouseId(warehouseId, PageRequest.of(0, 500))
                    .getContent();

            String dataPhase = dataPhaseTracker
                    .getPhase(StockoutPredictor.MODEL_NAME, distributorId)
                    .name();

            List<StockoutDisplayItem> results = stocks.stream()
                    .map(stock -> {
                        UUID productId = stock.getProduct().getId();
                        StockoutPredictor.StockoutResult r = stockoutPredictor.predict(warehouseId, productId);
                        predictionAlertService.evaluateStockoutAndAlert(r, distributorId);

                        String trendStr = formatTrend(r.trendPct());

                        return new StockoutDisplayItem(
                                productId,
                                stock.getProduct().getName(),
                                warehouseId,
                                stock.getWarehouse().getName(),
                                r.riskScore(),
                                r.daysUntilStockout(),
                                (int) r.currentStock(),
                                stock.getReorderLevel() != null ? stock.getReorderLevel().intValue() : 0,
                                (int) r.demand7d(),
                                r.consumptionTrend(),
                                trendStr
                        );
                    })
                    .sorted(java.util.Comparator.comparingDouble(StockoutDisplayItem::riskScore).reversed())
                    .toList();

            long atRisk = results.stream()
                    .filter(r -> r.riskScore() >= 0.5)
                    .count();

            StockoutBatchResponse response = new StockoutBatchResponse(
                    warehouseId, results.size(), (int) atRisk, dataPhase, results);

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

    // ── POST /stockout/train ──────────────────────────────────────────────

    @PostMapping("/stockout/train")
    @Operation(
            summary = "Retrain the stockout predictor model",
            description = "Runs the full training pipeline synchronously. Promotes the new model if AUC ≥ 0.75."
    )
    public ResponseEntity<ApiResponse<StockoutTrainingPipeline.TrainingPipelineResult>> trainStockoutModel() {
        log.info("POST /v1/ai/prediction/stockout/train — retraining stockout predictor");
        try {
            StockoutTrainingPipeline.TrainingPipelineResult result = stockoutTrainingPipeline.runPipeline();
            if (result.success()) {
                return ResponseEntity.ok(ApiResponse.success(result));
            } else {
                return ResponseEntity.internalServerError()
                        .body(ApiResponse.error("Training failed: " + result.errorMessage()));
            }
        } catch (Exception e) {
            log.error("Stockout training endpoint error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Training error: " + e.getMessage()));
        }
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────

    /** All fields the frontend StockoutPrediction interface expects. */
    public record StockoutDisplayItem(
            UUID   productId,
            String productName,
            UUID   warehouseId,
            String warehouseName,
            double riskScore,
            double daysUntilStockout,
            int    currentQuantity,
            int    reorderLevel,
            int    demand7d,
            String consumptionTrend,
            String trend
    ) {}

    public record StockoutBatchResponse(
            UUID warehouseId,
            int  totalProducts,
            int  atRiskCount,
            String dataPhase,
            List<StockoutDisplayItem> predictions
    ) {}

    private String formatTrend(double pct) {
        if (Math.abs(pct) < 0.5) return "~0%";
        return (pct > 0 ? "+" : "") + String.format("%.0f%%", pct);
    }

    public record RepBatchResponse(
            int  totalReps,
            int  atRiskCount,
            List<RepPerformancePredictor.RepPerformanceResult> predictions
    ) {}
}
