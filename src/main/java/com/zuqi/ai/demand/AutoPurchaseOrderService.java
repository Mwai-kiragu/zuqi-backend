package com.zuqi.ai.demand;

import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.ReorderSuggestion;
import com.zuqi.domain.procurement.PrStatus;
import com.zuqi.domain.procurement.PurchaseRequisition;
import com.zuqi.domain.user.User;
import com.zuqi.repository.PurchaseRequisitionRepository;
import com.zuqi.repository.ReorderSuggestionRepository;
import com.zuqi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts approved AI reorder suggestions into Purchase Requisitions.
 *
 * Rules:
 * - SYNTHETIC/HYBRID phase: manual approval only (no auto-generation)
 * - REAL phase + confidence > threshold: auto-creates PR
 * - All phases: individual suggestion approval via approveSuggestion()
 *
 * Blueprint: phase2-plan.md Section 2.3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoPurchaseOrderService {

    private final ReorderSuggestionRepository reorderSuggestionRepository;
    private final PurchaseRequisitionRepository purchaseRequisitionRepository;
    private final DataPhaseTracker phaseTracker;
    private final UserRepository userRepository;

    @Value("${zuqi.ai.inventory.auto-po-confidence-threshold:0.85}")
    private double autoPOConfidenceThreshold;

    /**
     * Process all PENDING suggestions for a distributor.
     * Only auto-creates PRs in REAL data phase with high confidence.
     *
     * @return number of PRs created
     */
    @Transactional
    public int processApprovedSuggestions(UUID distributorId) {
        String currentPhase = phaseTracker.getPhase("reorder_optimizer", distributorId).name();

        if (!"REAL".equals(currentPhase)) {
            log.info("Skipping auto-PO for distributor {} — data phase is {} (requires REAL)",
                    distributorId, currentPhase);
            return 0;
        }

        List<ReorderSuggestion> pendingSuggestions = reorderSuggestionRepository
                .findPendingByDistributorAndPhase(distributorId, "REAL");

        int created = 0;
        for (ReorderSuggestion suggestion : pendingSuggestions) {
            if (suggestion.getConfidenceScore() != null
                    && suggestion.getConfidenceScore() >= autoPOConfidenceThreshold) {
                try {
                    createPurchaseRequisition(suggestion);
                    suggestion.setStatus("CONVERTED");
                    reorderSuggestionRepository.save(suggestion);
                    created++;
                } catch (Exception e) {
                    log.error("Failed to create PR for suggestion {}: {}",
                            suggestion.getId(), e.getMessage(), e);
                }
            }
        }

        log.info("Auto-PO: created {} purchase requisitions for distributor {}", created, distributorId);
        return created;
    }

    /**
     * Manually approve a single suggestion and create a PR.
     * Available in all data phases.
     */
    @Transactional
    public PurchaseRequisition approveSuggestion(UUID suggestionId, UUID approvedByUserId) {
        ReorderSuggestion suggestion = reorderSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + suggestionId));

        if (!"PENDING".equals(suggestion.getStatus())) {
            throw new IllegalStateException("Suggestion " + suggestionId + " is not PENDING");
        }

        PurchaseRequisition pr = createPurchaseRequisition(suggestion, approvedByUserId);

        suggestion.setStatus("CONVERTED");
        suggestion.setConvertedPrId(pr.getId());
        reorderSuggestionRepository.save(suggestion);

        log.info("Manually approved suggestion {} -> PR {}", suggestionId, pr.getPrNumber());
        return pr;
    }

    private PurchaseRequisition createPurchaseRequisition(ReorderSuggestion suggestion) {
        return createPurchaseRequisition(suggestion, null);
    }

    private PurchaseRequisition createPurchaseRequisition(ReorderSuggestion suggestion,
                                                           UUID requestedByUserId) {
        String prNumber = "PR-AI-" + System.currentTimeMillis();

        // Build item list (JSONB)
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("product_id", suggestion.getProduct().getId().toString());
        item.put("product_name", suggestion.getProduct().getName());
        item.put("quantity", suggestion.getSuggestedQty());
        item.put("unit_cost", 0.0); // Will be filled by procurement team
        item.put("ai_generated", true);
        item.put("reorder_suggestion_id", suggestion.getId().toString());
        items.add(item);

        User requestedBy = null;
        if (requestedByUserId != null) {
            requestedBy = userRepository.findById(requestedByUserId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + requestedByUserId));
        }

        PurchaseRequisition pr = PurchaseRequisition.builder()
                .prNumber(prNumber)
                .distributorId(suggestion.getDistributor().getId())
                .requestedBy(requestedBy)
                .status(PrStatus.DRAFT)
                .description("AI-generated reorder for " + suggestion.getProduct().getName())
                .justification(String.format(
                        "Current stock: %.0f units, Reorder point: %.0f units, " +
                        "Days of supply: %.1f, EOQ: %.0f",
                        suggestion.getCurrentStock(),
                        suggestion.getReorderPoint(),
                        suggestion.getDaysOfSupplyRemaining(),
                        suggestion.getEconomicOrderQty()))
                .expectedDeliveryDate(LocalDate.now().plusDays(
                        suggestion.getLeadTimeDays() != null
                                ? suggestion.getLeadTimeDays().longValue() : 7))
                .items(items)
                .estimatedTotalAmount(BigDecimal.ZERO)
                .build();

        return purchaseRequisitionRepository.save(pr);
    }
}
