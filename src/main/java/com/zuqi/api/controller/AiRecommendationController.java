package com.zuqi.api.controller;

import com.zuqi.ai.agent.RecommendationService;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.ai.Recommendation;
import com.zuqi.domain.ai.RecommendationStatus;
import com.zuqi.repository.RecommendationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST API for AI-generated operational recommendations.
 *
 * Authorization is handled by Casbin policy.csv — no {@code @PreAuthorize} needed.
 *
 * Policy entries (from policy.csv):
 * <pre>
 *   p, DISTRIBUTOR_ADMIN, /v1/ai/recommendations/:id,        GET
 *   p, DISTRIBUTOR_ADMIN, /v1/ai/recommendations/:id/accept, PUT
 *   p, DISTRIBUTOR_ADMIN, /v1/ai/recommendations/:id/reject, PUT
 * </pre>
 *
 * Blueprint reference: implementation_plan.md Phase 6 Tasks 6.1-6.2
 */
@SuppressWarnings("DataFlowIssue")  // IDE false-positives on @NonNull UUID parameters
@RestController
@RequestMapping("/v1/ai/recommendations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI - Recommendations", description = "AI-generated operational recommendations")
public class AiRecommendationController {

    private final RecommendationRepository recommendationRepository;
    private final RecommendationService    recommendationService;

    // ── GET /{distributorId} ───────────────────────────────────────────────

    /**
     * Paginated list of recommendations for a distributor with optional status filter.
     *
     * Results are ordered by {@code createdAt} descending (newest first).
     *
     * @param distributorId UUID of the distributor
     * @param status        Optional status filter (PENDING, ACCEPTED, REJECTED, COMPLETED)
     * @param page          Zero-based page index (default 0)
     * @param size          Page size (default 20)
     * @return Paginated recommendation list
     */
    @GetMapping("/{distributorId}")
    @Operation(
            summary = "List recommendations for a distributor",
            description = "Returns a paginated list of AI-generated recommendations. " +
                          "Optionally filter by status: PENDING, ACCEPTED, REJECTED, COMPLETED.")
    public ResponseEntity<ApiResponse<Page<Recommendation>>> listRecommendations(
            @Parameter(required = true, description = "UUID of the distributor")
            @PathVariable UUID distributorId,
            @Parameter(description = "Filter by recommendation status (optional)")
            @RequestParam(required = false) RecommendationStatus status,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of results per page")
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /v1/ai/recommendations/{} status={} page={} size={}",
                distributorId, status, page, size);

        try {
            PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

            Page<Recommendation> recommendations = (status != null)
                    ? recommendationRepository.findByDistributorIdAndStatus(distributorId, status, pageable)
                    : recommendationRepository.findByDistributorId(distributorId, pageable);

            return ResponseEntity.ok(ApiResponse.success(recommendations));

        } catch (Exception e) {
            log.error("Failed to list recommendations for distributor={}: {}",
                    distributorId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch recommendations: " + e.getMessage()));
        }
    }

    // ── POST /{distributorId}/generate ────────────────────────────────────

    /**
     * Trigger on-demand recommendation generation for a distributor.
     *
     * Invokes the LangChain4j recommendation agent which uses tool-calling to
     * analyse current operational data and generate actionable recommendations.
     *
     * @param distributorId UUID of the distributor
     * @return List of newly generated and persisted recommendations
     */
    @PostMapping("/{distributorId}/generate")
    @Operation(
            summary = "Trigger recommendation generation for a distributor",
            description = "Runs the AI recommendation agent immediately for the specified distributor " +
                          "and persists the generated recommendations. Returns the new recommendations.")
    public ResponseEntity<ApiResponse<List<Recommendation>>> generateRecommendations(
            @Parameter(required = true, description = "UUID of the distributor")
            @PathVariable UUID distributorId) {

        log.info("POST /v1/ai/recommendations/{}/generate", distributorId);

        try {
            List<Recommendation> recommendations = recommendationService.generateAndSave(distributorId);
            log.info("Generated {} recommendations for distributor={}", recommendations.size(), distributorId);
            return ResponseEntity.ok(
                    ApiResponse.success("Generated " + recommendations.size() + " recommendations",
                            recommendations));

        } catch (IllegalArgumentException e) {
            log.warn("Recommendation generation rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Recommendation generation failed for distributor={}: {}",
                    distributorId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Recommendation generation failed: " + e.getMessage()));
        }
    }

    // ── GET /single/{id} ──────────────────────────────────────────────────

    /**
     * Retrieve a single recommendation by its UUID.
     *
     * Returns HTTP 404 if no recommendation exists with the given ID.
     *
     * @param id UUID of the recommendation
     * @return Single recommendation or 404
     */
    @GetMapping("/single/{id}")
    @Operation(
            summary = "Get a single recommendation by ID",
            description = "Returns the full recommendation detail for the given UUID. " +
                          "Returns HTTP 404 if the recommendation does not exist.")
    public ResponseEntity<ApiResponse<Recommendation>> getRecommendation(
            @Parameter(required = true, description = "UUID of the recommendation")
            @PathVariable UUID id) {

        log.info("GET /v1/ai/recommendations/single/{}", id);

        return recommendationRepository.findById(id)
                .map(rec -> ResponseEntity.ok(ApiResponse.success(rec)))
                .orElseGet(() -> {
                    log.warn("Recommendation not found: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    // ── PUT /{id}/accept ──────────────────────────────────────────────────

    /**
     * Accept a recommendation, transitioning its status to ACCEPTED.
     *
     * Also records the {@code actedOnAt} timestamp.
     * Returns HTTP 404 if the recommendation does not exist.
     *
     * @param id UUID of the recommendation to accept
     * @return Updated recommendation with status ACCEPTED
     */
    @PutMapping("/{id}/accept")
    @Operation(
            summary = "Accept a recommendation",
            description = "Transitions the recommendation status to ACCEPTED and records the action timestamp.")
    public ResponseEntity<ApiResponse<Recommendation>> acceptRecommendation(
            @Parameter(required = true, description = "UUID of the recommendation to accept")
            @PathVariable UUID id) {

        log.info("PUT /v1/ai/recommendations/{}/accept", id);

        return recommendationRepository.findById(id)
                .map(rec -> {
                    rec.setStatus(RecommendationStatus.ACCEPTED);
                    rec.setActedOnAt(LocalDateTime.now());
                    Recommendation saved = recommendationRepository.save(rec);
                    log.info("Recommendation {} accepted", id);
                    return ResponseEntity.ok(ApiResponse.success("Recommendation accepted", saved));
                })
                .orElseGet(() -> {
                    log.warn("Accept failed — recommendation not found: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    // ── PUT /{id}/reject ──────────────────────────────────────────────────

    /**
     * Reject a recommendation, transitioning its status to REJECTED.
     *
     * Also records the {@code actedOnAt} timestamp.
     * Returns HTTP 404 if the recommendation does not exist.
     *
     * @param id UUID of the recommendation to reject
     * @return Updated recommendation with status REJECTED
     */
    @PutMapping("/{id}/reject")
    @Operation(
            summary = "Reject a recommendation",
            description = "Transitions the recommendation status to REJECTED and records the action timestamp.")
    public ResponseEntity<ApiResponse<Recommendation>> rejectRecommendation(
            @Parameter(required = true, description = "UUID of the recommendation to reject")
            @PathVariable UUID id) {

        log.info("PUT /v1/ai/recommendations/{}/reject", id);

        return recommendationRepository.findById(id)
                .map(rec -> {
                    rec.setStatus(RecommendationStatus.REJECTED);
                    rec.setActedOnAt(LocalDateTime.now());
                    Recommendation saved = recommendationRepository.save(rec);
                    log.info("Recommendation {} rejected", id);
                    return ResponseEntity.ok(ApiResponse.success("Recommendation rejected", saved));
                })
                .orElseGet(() -> {
                    log.warn("Reject failed — recommendation not found: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }
}
