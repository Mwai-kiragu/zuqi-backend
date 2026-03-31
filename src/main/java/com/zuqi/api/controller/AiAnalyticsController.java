package com.zuqi.api.controller;

import com.zuqi.ai.crm.CustomerHealthScoreService;
import com.zuqi.ai.demand.AutoPurchaseOrderService;
import com.zuqi.ai.demand.CashFlowForecastService;
import com.zuqi.ai.demand.ExpiryRiskJob;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.CashFlowForecastEntry;
import com.zuqi.api.dto.ai.ReorderSuggestionResponse;
import com.zuqi.domain.ai.ChurnPrediction;
import com.zuqi.domain.ai.CustomerHealthScore;
import com.zuqi.domain.ai.CustomerSegment;
import com.zuqi.domain.ai.ExpiryRiskScore;
import com.zuqi.domain.ai.PriceTrend;
import com.zuqi.domain.ai.PricingRecommendation;
import com.zuqi.domain.ai.ProductRecommendation;
import com.zuqi.domain.ai.ReorderSuggestion;
import com.zuqi.domain.ai.SupplierRiskScore;
import com.zuqi.domain.ai.VisitRecommendation;
import com.zuqi.domain.procurement.PurchaseRequisition;
import com.zuqi.repository.ChurnPredictionRepository;
import com.zuqi.repository.CustomerHealthScoreRepository;
import com.zuqi.repository.CustomerSegmentRepository;
import com.zuqi.repository.ExpiryRiskScoreRepository;
import com.zuqi.repository.PriceTrendRepository;
import com.zuqi.repository.PricingRecommendationRepository;
import com.zuqi.repository.ProductRecommendationRepository;
import com.zuqi.repository.ReorderSuggestionRepository;
import com.zuqi.repository.SupplierRiskScoreRepository;
import com.zuqi.repository.VisitRecommendationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Phase 7 integration controller.
 *
 * <p>Exposes pre-computed AI analytics results for all modules:
 * Inventory (reorder, expiry), CRM (segments, churn, recommendations, visit plans),
 * Procurement (supplier risk, price trends), Sales (pricing recommendations).
 *
 * <p>All endpoints read from pre-computed AI tables — no live model inference here.
 * Batch jobs (ReorderOptimizationJob, ExpiryRiskJob, etc.) populate the tables nightly.
 *
 * <p>Authorization via Casbin policy.csv.
 */
@RestController
@RequestMapping("/v1/ai/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI - Analytics", description = "Pre-computed AI intelligence results across all modules")
public class AiAnalyticsController {

    private final ReorderSuggestionRepository      reorderSuggestionRepository;
    private final ExpiryRiskScoreRepository        expiryRiskScoreRepository;
    private final CustomerSegmentRepository        customerSegmentRepository;
    private final ChurnPredictionRepository        churnPredictionRepository;
    private final ProductRecommendationRepository  productRecommendationRepository;
    private final VisitRecommendationRepository    visitRecommendationRepository;
    private final SupplierRiskScoreRepository      supplierRiskScoreRepository;
    private final PriceTrendRepository             priceTrendRepository;
    private final PricingRecommendationRepository  pricingRecommendationRepository;
    private final AutoPurchaseOrderService         autoPurchaseOrderService;
    private final CashFlowForecastService          cashFlowForecastService;
    private final ExpiryRiskJob                    expiryRiskJob;
    private final CustomerHealthScoreRepository    customerHealthScoreRepository;
    private final CustomerHealthScoreService       customerHealthScoreService;

    // ── CASH FLOW FORECAST ────────────────────────────────────────────────────

    @GetMapping("/cashflow/forecast/{distributorId}")
    @Operation(summary = "Get AI cash flow forecast (daily projected inflows, outflows, net)")
    public ResponseEntity<ApiResponse<List<CashFlowForecastEntry>>> getCashFlowForecast(
            @PathVariable UUID distributorId,
            @Parameter(description = "Forecast horizon in days (7, 30, or 90)")
            @RequestParam(defaultValue = "30") int days) {

        log.info("GET /analytics/cashflow/forecast/{} days={}", distributorId, days);
        int horizon = Math.min(Math.max(days, 7), 90);
        List<CashFlowForecastEntry> forecast = cashFlowForecastService.forecast(distributorId, horizon);
        return ResponseEntity.ok(ApiResponse.success(forecast));
    }

    // ── INVENTORY: Reorder ────────────────────────────────────────────────────

    @GetMapping("/reorder/suggestions/{distributorId}")
    @Operation(summary = "Get pending reorder suggestions for a distributor")
    public ResponseEntity<ApiResponse<List<ReorderSuggestionResponse>>> getReorderSuggestions(
            @PathVariable UUID distributorId,
            @Parameter(description = "Filter by status (default: PENDING). Use ALL to return every status.")
            @RequestParam(defaultValue = "PENDING") String status) {

        log.info("GET /analytics/reorder/suggestions/{} status={}", distributorId, status);
        List<ReorderSuggestionResponse> suggestions;
        if ("ALL".equalsIgnoreCase(status)) {
            suggestions = reorderSuggestionRepository
                    .findAllByDistributorIdFetched(distributorId)
                    .stream().map(ReorderSuggestionResponse::fromEntity).toList();
        } else {
            suggestions = reorderSuggestionRepository
                    .findByDistributorIdAndStatus(distributorId, status)
                    .stream().map(ReorderSuggestionResponse::fromEntity).toList();
        }
        return ResponseEntity.ok(ApiResponse.success(suggestions));
    }

    @PostMapping("/reorder/suggestions/{id}/approve")
    @Operation(summary = "Manually approve a reorder suggestion and create a Purchase Requisition")
    public ResponseEntity<ApiResponse<PurchaseRequisition>> approveSuggestion(
            @PathVariable UUID id,
            @Parameter(required = true) @RequestParam UUID approvedByUserId) {

        log.info("POST /analytics/reorder/suggestions/{}/approve approvedBy={}", id, approvedByUserId);
        try {
            PurchaseRequisition pr = autoPurchaseOrderService.approveSuggestion(id, approvedByUserId);
            return ResponseEntity.ok(ApiResponse.success(pr));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to approve suggestion {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to approve suggestion: " + e.getMessage()));
        }
    }

    // ── INVENTORY: Expiry ─────────────────────────────────────────────────────

    @GetMapping("/expiry/risks/{distributorId}")
    @Operation(summary = "Get expiry risk scores for all batches in a distributor")
    public ResponseEntity<ApiResponse<List<ExpiryRiskScore>>> getExpiryRisks(
            @PathVariable UUID distributorId,
            @Parameter(description = "Minimum risk score filter (0.0–1.0, default 0.3)")
            @RequestParam(defaultValue = "0.3") double minRisk) {

        log.info("GET /analytics/expiry/risks/{} minRisk={}", distributorId, minRisk);
        List<ExpiryRiskScore> risks =
                expiryRiskScoreRepository.findHighRiskByDistributor(distributorId, minRisk);
        return ResponseEntity.ok(ApiResponse.success(risks));
    }

    @GetMapping("/expiry/risks/{distributorId}/{warehouseId}")
    @Operation(summary = "Get expiry risk scores filtered by warehouse")
    public ResponseEntity<ApiResponse<List<ExpiryRiskScore>>> getExpiryRisksByWarehouse(
            @PathVariable UUID distributorId,
            @PathVariable UUID warehouseId) {

        log.info("GET /analytics/expiry/risks/{}/{}", distributorId, warehouseId);
        List<ExpiryRiskScore> risks =
                expiryRiskScoreRepository.findByDistributorIdAndWarehouseId(distributorId, warehouseId);
        return ResponseEntity.ok(ApiResponse.success(risks));
    }

    /**
     * Manually trigger the expiry risk scoring job without waiting for the 5:30 AM cron.
     */
    @PostMapping("/expiry/run")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Trigger expiry risk scoring job now",
            description = "Runs the nightly expiry risk job immediately for all distributors. Admin only."
    )
    public ResponseEntity<ApiResponse<Void>> runExpiryRiskJob() {
        log.info("POST /v1/ai/analytics/expiry/run — manual trigger");
        try {
            expiryRiskJob.runExpiryRiskScoring();
            long count = expiryRiskScoreRepository.count();
            return ResponseEntity.ok(ApiResponse.success(
                    "Expiry risk job completed. Total scores in DB: " + count));
        } catch (Exception e) {
            log.error("Manual expiry risk job failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Expiry risk job failed: " + e.getMessage()));
        }
    }

    // ── CRM: Segments ─────────────────────────────────────────────────────────

    @GetMapping("/customers/segments/{distributorId}")
    @Operation(summary = "Get all customer segment assignments for a distributor")
    public ResponseEntity<ApiResponse<List<CustomerSegment>>> getCustomerSegments(
            @PathVariable UUID distributorId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "100") int size) {

        log.info("GET /analytics/customers/segments/{}", distributorId);
        List<CustomerSegment> segments = customerSegmentRepository
                .findByDistributorId(distributorId, PageRequest.of(page, size))
                .getContent();
        return ResponseEntity.ok(ApiResponse.success(segments));
    }

    // ── CRM: Churn ────────────────────────────────────────────────────────────

    @GetMapping("/customers/churn/{distributorId}")
    @Operation(summary = "Get all churn predictions for a distributor, highest risk first")
    public ResponseEntity<ApiResponse<List<ChurnPrediction>>> getChurnPredictions(
            @PathVariable UUID distributorId,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "100")  int size) {

        log.info("GET /analytics/customers/churn/{}", distributorId);
        List<ChurnPrediction> predictions = churnPredictionRepository
                .findByDistributorId(distributorId, PageRequest.of(page, size))
                .getContent();
        return ResponseEntity.ok(ApiResponse.success(predictions));
    }

    @GetMapping("/customers/churn/{distributorId}/at-risk")
    @Operation(summary = "Get customers with churn probability above threshold")
    public ResponseEntity<ApiResponse<List<ChurnPrediction>>> getAtRiskCustomers(
            @PathVariable UUID distributorId,
            @Parameter(description = "Churn probability threshold (default 0.6)")
            @RequestParam(defaultValue = "0.6") double threshold) {

        log.info("GET /analytics/customers/churn/{}/at-risk threshold={}", distributorId, threshold);
        List<ChurnPrediction> atRisk =
                churnPredictionRepository.findAtRiskCustomers(distributorId, threshold);
        return ResponseEntity.ok(ApiResponse.success(atRisk));
    }

    // ── CRM: Product Recommendations ──────────────────────────────────────────

    @GetMapping("/customers/recommendations/{customerId}")
    @Operation(summary = "Get product recommendations for a customer")
    public ResponseEntity<ApiResponse<List<ProductRecommendation>>> getProductRecommendations(
            @PathVariable UUID customerId,
            @Parameter(required = true) @RequestParam UUID distributorId) {

        log.info("GET /analytics/customers/recommendations/{} distributor={}", customerId, distributorId);
        List<ProductRecommendation> recs =
                productRecommendationRepository.findByDistributorIdAndCustomerId(distributorId, customerId);
        return ResponseEntity.ok(ApiResponse.success(recs));
    }

    @GetMapping("/customers/product-recs/{distributorId}")
    @Operation(summary = "Get all product recommendations for a distributor, best scored first")
    public ResponseEntity<ApiResponse<List<ProductRecommendation>>> getProductRecsByDistributor(
            @PathVariable UUID distributorId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("GET /analytics/customers/product-recs/{}", distributorId);
        List<ProductRecommendation> recs =
                productRecommendationRepository.findByDistributorIdOrderByRecommendationScoreDesc(distributorId);
        return ResponseEntity.ok(ApiResponse.success(recs));
    }

    // ── CRM: Visit Plans ──────────────────────────────────────────────────────

    @GetMapping("/reps/{repId}/visit-plan")
    @Operation(summary = "Get the visit schedule for a sales rep")
    public ResponseEntity<ApiResponse<List<VisitRecommendation>>> getVisitPlan(
            @PathVariable UUID repId,
            @Parameter(required = true) @RequestParam UUID distributorId) {

        log.info("GET /analytics/reps/{}/visit-plan distributor={}", repId, distributorId);
        List<VisitRecommendation> plan =
                visitRecommendationRepository.findBySalesRepIdAndDistributorId(repId, distributorId);
        return ResponseEntity.ok(ApiResponse.success(plan));
    }

    // ── PROCUREMENT: Supplier Risk ────────────────────────────────────────────

    @GetMapping("/suppliers/risk/{distributorId}")
    @Operation(summary = "Get supplier risk scores for a distributor, highest risk first")
    public ResponseEntity<ApiResponse<List<SupplierRiskScore>>> getSupplierRiskScores(
            @PathVariable UUID distributorId,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "100")  int size) {

        log.info("GET /analytics/suppliers/risk/{}", distributorId);
        List<SupplierRiskScore> scores = supplierRiskScoreRepository
                .findByDistributorId(distributorId, PageRequest.of(page, size))
                .getContent();
        return ResponseEntity.ok(ApiResponse.success(scores));
    }

    // ── PROCUREMENT: Price Trends ─────────────────────────────────────────────

    @GetMapping("/suppliers/price-trends/{distributorId}")
    @Operation(summary = "Get all price trends for a distributor")
    public ResponseEntity<ApiResponse<List<PriceTrend>>> getPriceTrends(
            @PathVariable UUID distributorId,
            @Parameter(description = "Filter by direction: INCREASING, DECREASING, STABLE")
            @RequestParam(required = false) String direction) {

        log.info("GET /analytics/suppliers/price-trends/{} direction={}", distributorId, direction);
        List<PriceTrend> trends = direction != null
                ? priceTrendRepository.findByDistributorIdAndTrendDirection(distributorId, direction)
                : priceTrendRepository.findByDistributorId(distributorId);
        return ResponseEntity.ok(ApiResponse.success(trends));
    }

    @GetMapping("/suppliers/price-trends/{distributorId}/{supplierId}")
    @Operation(summary = "Get price trends for a specific supplier")
    public ResponseEntity<ApiResponse<List<PriceTrend>>> getSupplierPriceTrends(
            @PathVariable UUID distributorId,
            @PathVariable UUID supplierId) {

        log.info("GET /analytics/suppliers/price-trends/{}/{}", distributorId, supplierId);
        // Filter all distributor trends to those belonging to this supplier
        List<PriceTrend> trends = priceTrendRepository.findByDistributorId(distributorId)
                .stream()
                .filter(t -> t.getSupplier() != null
                        && supplierId.equals(t.getSupplier().getId()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(trends));
    }

    // ── SALES: Pricing Recommendations ───────────────────────────────────────

    @GetMapping("/pricing/recommendations/{distributorId}")
    @Operation(summary = "Get pricing recommendations for a distributor, highest impact first")
    public ResponseEntity<ApiResponse<List<PricingRecommendation>>> getPricingRecommendations(
            @PathVariable UUID distributorId,
            @Parameter(description = "Filter by status: PENDING, APPLIED, REJECTED")
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "100")  int size) {

        log.info("GET /analytics/pricing/recommendations/{} status={}", distributorId, status);
        List<PricingRecommendation> recs =
                pricingRecommendationRepository.findByDistributorIdAndStatus(distributorId, status);
        return ResponseEntity.ok(ApiResponse.success(recs));
    }

    @PostMapping("/pricing/recommendations/{id}/apply")
    @Operation(summary = "Mark a pricing recommendation as applied")
    public ResponseEntity<ApiResponse<PricingRecommendation>> applyPricingRecommendation(
            @PathVariable UUID id) {

        log.info("POST /analytics/pricing/recommendations/{}/apply", id);
        return updatePricingStatus(id, "APPLIED");
    }

    @PostMapping("/pricing/recommendations/{id}/reject")
    @Operation(summary = "Mark a pricing recommendation as rejected")
    public ResponseEntity<ApiResponse<PricingRecommendation>> rejectPricingRecommendation(
            @PathVariable UUID id) {

        log.info("POST /analytics/pricing/recommendations/{}/reject", id);
        return updatePricingStatus(id, "REJECTED");
    }

    // ── CRM: Customer Health Scores ───────────────────────────────────────────

    @GetMapping("/customers/health/{distributorId}")
    @Operation(
            summary = "Get customer health scores for a distributor",
            description = "Returns pre-computed composite health scores for all customers. " +
                          "Scores aggregate order frequency, payment timeliness, revenue trend, " +
                          "engagement, and credit health. Batch job refreshes scores nightly.")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<CustomerHealthScore>>> getCustomerHealthScores(
            @PathVariable UUID distributorId,
            @Parameter(description = "Filter by health tier: THRIVING, HEALTHY, NEEDS_ATTENTION, AT_RISK, CRITICAL (optional)")
            @RequestParam(required = false) String tier,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("GET /analytics/customers/health/{} tier={} page={} size={}",
                distributorId, tier, page, size);
        org.springframework.data.domain.PageRequest pageable =
                org.springframework.data.domain.PageRequest.of(page, size);

        if (tier != null && !tier.isBlank()) {
            // Return tier-filtered list wrapped as a Page
            java.util.List<CustomerHealthScore> byTier =
                    customerHealthScoreRepository.findByDistributorIdAndHealthTier(distributorId, tier.toUpperCase());
            java.util.List<CustomerHealthScore> tieredPage = byTier.stream()
                    .skip((long) page * size).limit(size).toList();
            org.springframework.data.domain.Page<CustomerHealthScore> paged =
                    new org.springframework.data.domain.PageImpl<>(tieredPage, pageable, byTier.size());
            return ResponseEntity.ok(ApiResponse.success(paged));
        }
        return ResponseEntity.ok(ApiResponse.success(
                customerHealthScoreRepository.findByDistributorId(distributorId, pageable)));
    }

    @GetMapping("/customers/health/{distributorId}/{customerId}")
    @Operation(
            summary = "Get health score for a single customer",
            description = "Returns the latest health score for one customer. " +
                          "Returns HTTP 404 if no score has been computed yet.")
    public ResponseEntity<ApiResponse<CustomerHealthScore>> getCustomerHealthScore(
            @PathVariable UUID distributorId,
            @PathVariable UUID customerId) {

        log.info("GET /analytics/customers/health/{}/{}", distributorId, customerId);
        return customerHealthScoreRepository
                .findByDistributorIdAndCustomerId(distributorId, customerId)
                .map(score -> ResponseEntity.ok(ApiResponse.success(score)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.error("No health score found — run the nightly job or POST /compute")));
    }

    @PostMapping("/customers/health/{distributorId}/{customerId}/compute")
    @Operation(
            summary = "Compute health score for a single customer on-demand",
            description = "Immediately computes and persists a health score for the given customer. " +
                          "Useful when real-time scoring is needed outside the nightly batch window.")
    public ResponseEntity<ApiResponse<CustomerHealthScore>> computeCustomerHealthScore(
            @PathVariable UUID distributorId,
            @PathVariable UUID customerId) {

        log.info("POST /analytics/customers/health/{}/{}/compute", distributorId, customerId);
        try {
            CustomerHealthScore score = customerHealthScoreService.computeScore(customerId, distributorId);
            return ResponseEntity.ok(ApiResponse.success(score));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Health score computation failed for customer={}: {}", customerId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Health score computation failed: " + e.getMessage()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<ApiResponse<PricingRecommendation>> updatePricingStatus(
            UUID id, String status) {
        if (id == null) return ResponseEntity.badRequest().build();
        return pricingRecommendationRepository.findById(id)
                .map(rec -> {
                    rec.setStatus(status);
                    PricingRecommendation saved = pricingRecommendationRepository.save(rec);
                    return ResponseEntity.ok(ApiResponse.success(saved));
                })
                .orElseGet(() -> ResponseEntity.notFound()
                        .<ApiResponse<PricingRecommendation>>build());
    }
}
