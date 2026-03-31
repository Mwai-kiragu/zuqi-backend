package com.zuqi.api.controller;

import com.zuqi.ai.demand.DemandForecastJob;
import com.zuqi.ai.demand.DemandForecaster;
import com.zuqi.ai.demand.DemandModelTrainingPipeline;
import com.zuqi.ai.demand.OrderSuggestionService;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.ai.DemandForecast;
import com.zuqi.repository.DemandForecastRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API controller for demand forecasting and order suggestions.
 *
 * Endpoints:
 * - GET /v1/ai/demand/forecasts/{merchantId} - Get all forecasts for merchant
 * - GET /v1/ai/demand/forecasts/{merchantId}/{productId} - Get single forecast
 * - GET /v1/ai/demand/suggestions/{merchantId} - Get order suggestions
 * - POST /v1/ai/demand/train - Trigger model retraining (admin only)
 *
 * Blueprint: plan.md Section 6.2 - Demand Forecasting Module
 */
@RestController
@RequestMapping("/v1/ai/demand")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI - Demand Forecasting", description = "AI-powered demand forecasting and order suggestions")
public class DemandForecastController {

    private final DemandForecaster demandForecaster;
    private final OrderSuggestionService orderSuggestionService;
    private final DemandModelTrainingPipeline trainingPipeline;
    private final DemandForecastRepository demandForecastRepository;
    private final DemandForecastJob demandForecastJob;

    /**
     * List all stored demand forecasts for a distributor (paginated).
     */
    @GetMapping("/forecasts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SALES_REP', 'DISTRIBUTOR_ADMIN')")
    @Operation(
            summary = "List demand forecasts",
            description = "Paginated list of stored demand forecasts for the distributor"
    )
    public ResponseEntity<ApiResponse<Page<DemandForecast>>> listForecasts(
            @Parameter(required = true) @RequestParam UUID distributorId,
            @Parameter(description = "Warehouse filter (not stored on forecast — accepted for API compatibility)")
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /v1/ai/demand/forecasts distributor={} warehouseId={} page={} size={}",
                distributorId, warehouseId, page, size);

        try {
            PageRequest pageable = PageRequest.of(page, size);
            Page<DemandForecast> forecasts = demandForecastRepository.findByDistributorId(distributorId, pageable);
            return ResponseEntity.ok(ApiResponse.success(forecasts));
        } catch (Exception e) {
            log.error("Failed to list forecasts: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to list forecasts: " + e.getMessage()));
        }
    }

    /**
     * Get demand forecast for a specific merchant-product combination.
     */
    @GetMapping("/forecasts/{merchantId}/{productId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SALES_REP', 'DISTRIBUTOR_ADMIN')")
    @Operation(
            summary = "Get demand forecast for merchant-product",
            description = "Returns AI-predicted demand quantity for next week with confidence score"
    )
    public ResponseEntity<ApiResponse<DemandForecastResponse>> getForecast(
            @Parameter(description = "Merchant ID") @PathVariable UUID merchantId,
            @Parameter(description = "Product/SKU ID") @PathVariable UUID productId) {

        log.info("GET /v1/ai/demand/forecasts/{}/{}", merchantId, productId);

        try {
            DemandForecaster.DemandForecast forecast = demandForecaster.forecastDemand(merchantId, productId);

            DemandForecastResponse response = new DemandForecastResponse(
                    forecast.merchantId(),
                    forecast.productId(),
                    forecast.predictedQuantity(),
                    forecast.confidence(),
                    forecast.rollingAvg4w(),
                    forecast.rollingAvg12w(),
                    forecast.trendDirection(),
                    forecast.modelVersion()
            );

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Failed to get forecast for merchant {} product {}: {}",
                    merchantId, productId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to generate forecast: " + e.getMessage()));
        }
    }

    /**
     * Get order suggestions for a merchant (for sales rep).
     */
    @GetMapping("/suggestions/{merchantId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SALES_REP')")
    @Operation(
            summary = "Get AI-powered order suggestions",
            description = "Returns ranked list of suggested products with quantities for sales rep"
    )
    public ResponseEntity<ApiResponse<OrderSuggestionsResponse>> getOrderSuggestions(
            @Parameter(description = "Merchant ID") @PathVariable UUID merchantId,
            @Parameter(description = "Maximum suggestions to return") @RequestParam(defaultValue = "20") int maxSuggestions) {

        log.info("GET /v1/ai/demand/suggestions/{} (max: {})", merchantId, maxSuggestions);

        try {
            List<OrderSuggestionService.OrderSuggestion> suggestions =
                    orderSuggestionService.generateSuggestions(merchantId, maxSuggestions);

            OrderSuggestionsResponse response = new OrderSuggestionsResponse(
                    merchantId,
                    suggestions.size(),
                    suggestions
            );

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Failed to generate order suggestions for merchant {}: {}",
                    merchantId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to generate suggestions: " + e.getMessage()));
        }
    }

    /**
     * Trigger demand forecasting model training (admin only).
     */
    @PostMapping("/train")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Train demand forecasting model",
            description = "Triggers model training pipeline with synthetic or real data. Admin only."
    )
    public ResponseEntity<ApiResponse<TrainingResponse>> trainModel(
            @Parameter(description = "Number of merchants") @RequestParam(defaultValue = "50") int numMerchants,
            @Parameter(description = "Number of products") @RequestParam(defaultValue = "20") int numProducts,
            @Parameter(description = "Number of weeks") @RequestParam(defaultValue = "26") int numWeeks) {

        log.info("POST /v1/ai/demand/train (merchants: {}, products: {}, weeks: {})",
                numMerchants, numProducts, numWeeks);

        try {
            DemandModelTrainingPipeline.TrainingPipelineResult result =
                    trainingPipeline.runPipeline(numMerchants, numProducts, numWeeks);

            if (!result.success()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Training failed: " + result.errorMessage()));
            }

            TrainingResponse response = new TrainingResponse(
                    result.success(),
                    result.numSequences(),
                    result.numTrainingExamples(),
                    result.trainSize(),
                    result.testSize(),
                    result.evaluation().r2(),
                    result.evaluation().mae(),
                    result.evaluation().rmse(),
                    result.evaluation().passedQualityGate(),
                    result.modelId(),
                    result.durationMs()
            );

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Training failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Training failed: " + e.getMessage()));
        }
    }

    /**
     * Get demand forecasting model health status.
     */
    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SALES_REP', 'DISTRIBUTOR_ADMIN')")
    @Operation(
            summary = "Check demand forecasting model health",
            description = "Returns model status and availability"
    )
    public ResponseEntity<ApiResponse<HealthResponse>> getHealth() {
        log.info("GET /v1/ai/demand/health");

        try {
            // Try a simple forecast to check if model is available
            UUID testMerchantId = UUID.randomUUID();
            UUID testProductId = UUID.randomUUID();

            DemandForecaster.DemandForecast forecast =
                    demandForecaster.forecastDemand(testMerchantId, testProductId);

            boolean modelAvailable = !forecast.modelVersion().equals("fallback-avg") &&
                                     !forecast.modelVersion().equals("error");

            HealthResponse response = new HealthResponse(
                    "demand_forecaster",
                    modelAvailable,
                    modelAvailable ? "Model operational" : "Model not available - using fallback",
                    forecast.modelVersion()
            );

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage(), e);

            HealthResponse response = new HealthResponse(
                    "demand_forecaster",
                    false,
                    "Health check failed: " + e.getMessage(),
                    "unknown"
            );

            return ResponseEntity.ok(ApiResponse.success(response));
        }
    }

    /**
     * Manually trigger the demand forecast job without waiting for the 3 AM cron.
     * Useful for populating ai_demand_forecasts on a fresh database.
     */
    @PostMapping("/forecast/run")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Trigger demand forecast job now",
            description = "Runs the nightly demand forecast job immediately for all distributors. Admin only."
    )
    public ResponseEntity<ApiResponse<Void>> runForecastJob() {
        log.info("POST /v1/ai/demand/forecast/run — manual trigger");
        try {
            demandForecastJob.generateForecasts();
            long count = demandForecastRepository.count();
            return ResponseEntity.ok(ApiResponse.success(
                    "Demand forecast job completed. Total forecasts in DB: " + count));
        } catch (Exception e) {
            log.error("Manual forecast job failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Forecast job failed: " + e.getMessage()));
        }
    }

    // ========== DTOs ==========

    /**
     * Demand forecast response DTO.
     */
    public record DemandForecastResponse(
            UUID merchantId,
            UUID productId,
            java.math.BigDecimal predictedQuantity,
            double confidence,
            java.math.BigDecimal rollingAvg4w,
            java.math.BigDecimal rollingAvg12w,
            String trendDirection,
            String modelVersion
    ) {}

    /**
     * Order suggestions response DTO.
     */
    public record OrderSuggestionsResponse(
            UUID merchantId,
            int suggestionCount,
            List<OrderSuggestionService.OrderSuggestion> suggestions
    ) {}

    /**
     * Training response DTO.
     */
    public record TrainingResponse(
            boolean success,
            int numSequences,
            int numTrainingExamples,
            int trainSize,
            int testSize,
            double r2Score,
            double maeScore,
            double rmseScore,
            boolean passedQualityGate,
            UUID modelId,
            long durationMs
    ) {}

    /**
     * Health check response DTO.
     */
    public record HealthResponse(
            String modelName,
            boolean available,
            String status,
            String modelVersion
    ) {}
}
