package com.zuqi.ai.demand;

import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.DataPhase;
import com.zuqi.domain.ai.ReorderSuggestion;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.procurement.PurchaseRequisition;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.user.User;
import com.zuqi.repository.PurchaseRequisitionRepository;
import com.zuqi.repository.ReorderSuggestionRepository;
import com.zuqi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoPurchaseOrderServiceTest {

    @Mock private ReorderSuggestionRepository reorderSuggestionRepository;
    @Mock private PurchaseRequisitionRepository purchaseRequisitionRepository;
    @Mock private DataPhaseTracker phaseTracker;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private AutoPurchaseOrderService service;

    // ── processApprovedSuggestions ───────────────────────────────────────────

    @Test
    void processApprovedSuggestions_whenSyntheticPhase_createsNoPRs() {
        UUID distributorId = UUID.randomUUID();
        when(phaseTracker.getPhase("reorder_optimizer", distributorId))
                .thenReturn(DataPhase.SYNTHETIC);

        int created = service.processApprovedSuggestions(distributorId);

        assertThat(created).isEqualTo(0);
        verify(reorderSuggestionRepository, never()).findPendingByDistributorAndPhase(any(), any());
        verify(purchaseRequisitionRepository, never()).save(any());
    }

    @Test
    void processApprovedSuggestions_whenRealPhaseHighConfidence_createsPR() {
        UUID distributorId = UUID.randomUUID();
        when(phaseTracker.getPhase("reorder_optimizer", distributorId))
                .thenReturn(DataPhase.REAL);

        Distributor dist = new Distributor();
        Warehouse   wh   = new Warehouse();
        Product     prod = new Product();
        prod.setId(UUID.randomUUID());
        prod.setName("Test Product");

        ReorderSuggestion suggestion = new ReorderSuggestion();
        suggestion.setId(UUID.randomUUID());
        suggestion.setDistributor(dist);
        suggestion.setWarehouse(wh);
        suggestion.setProduct(prod);
        suggestion.setSuggestedQty(200.0);
        suggestion.setEconomicOrderQty(200.0);
        suggestion.setSafetyStock(20.0);
        suggestion.setReorderPoint(50.0);
        suggestion.setCurrentStock(10.0);
        suggestion.setDaysOfSupplyRemaining(1.0);
        suggestion.setLeadTimeDays(7.0);
        suggestion.setConfidenceScore(0.90);
        suggestion.setDataPhase("REAL");
        suggestion.setStatus("PENDING");

        when(reorderSuggestionRepository.findPendingByDistributorAndPhase(distributorId, "REAL"))
                .thenReturn(List.of(suggestion));

        PurchaseRequisition savedPr = PurchaseRequisition.builder()
                .prNumber("PR-AI-123")
                .build();
        when(purchaseRequisitionRepository.save(any())).thenReturn(savedPr);
        when(reorderSuggestionRepository.save(any())).thenReturn(suggestion);

        int created = service.processApprovedSuggestions(distributorId);

        assertThat(created).isEqualTo(1);
        verify(purchaseRequisitionRepository).save(any());
    }

    @Test
    void processApprovedSuggestions_whenRealPhaseLowConfidence_createsNoPR() {
        UUID distributorId = UUID.randomUUID();
        when(phaseTracker.getPhase("reorder_optimizer", distributorId))
                .thenReturn(DataPhase.REAL);

        ReorderSuggestion suggestion = ReorderSuggestion.builder()
                .confidenceScore(0.70) // below 0.85 threshold
                .status("PENDING")
                .build();

        when(reorderSuggestionRepository.findPendingByDistributorAndPhase(distributorId, "REAL"))
                .thenReturn(List.of(suggestion));

        int created = service.processApprovedSuggestions(distributorId);

        assertThat(created).isEqualTo(0);
        verify(purchaseRequisitionRepository, never()).save(any());
    }

    // ── approveSuggestion ────────────────────────────────────────────────────

    @Test
    void approveSuggestion_withValidPendingSuggestion_createsPR() {
        UUID suggestionId = UUID.randomUUID();
        UUID approvedById = UUID.randomUUID();

        Distributor dist = new Distributor();
        Product prod = new Product();
        prod.setId(UUID.randomUUID());
        prod.setName("Widget");

        ReorderSuggestion suggestion = new ReorderSuggestion();
        suggestion.setId(UUID.randomUUID());
        suggestion.setDistributor(dist);
        suggestion.setWarehouse(new Warehouse());
        suggestion.setProduct(prod);
        suggestion.setSuggestedQty(100.0);
        suggestion.setEconomicOrderQty(100.0);
        suggestion.setSafetyStock(10.0);
        suggestion.setReorderPoint(30.0);
        suggestion.setCurrentStock(5.0);
        suggestion.setDaysOfSupplyRemaining(0.5);
        suggestion.setLeadTimeDays(7.0);
        suggestion.setStatus("PENDING");

        when(reorderSuggestionRepository.findById(suggestionId))
                .thenReturn(Optional.of(suggestion));
        when(userRepository.findById(approvedById))
                .thenReturn(Optional.of(new User()));

        PurchaseRequisition savedPr = PurchaseRequisition.builder()
                .prNumber("PR-AI-MANUAL-1")
                .build();
        when(purchaseRequisitionRepository.save(any())).thenReturn(savedPr);
        when(reorderSuggestionRepository.save(any())).thenReturn(suggestion);

        PurchaseRequisition result = service.approveSuggestion(suggestionId, approvedById);

        assertThat(result).isNotNull();
        assertThat(result.getPrNumber()).isEqualTo("PR-AI-MANUAL-1");
    }

    @Test
    void approveSuggestion_whenSuggestionNotPending_throwsIllegalStateException() {
        UUID suggestionId = UUID.randomUUID();

        ReorderSuggestion converted = ReorderSuggestion.builder()
                .status("CONVERTED")
                .build();
        when(reorderSuggestionRepository.findById(suggestionId))
                .thenReturn(Optional.of(converted));

        assertThatThrownBy(() -> service.approveSuggestion(suggestionId, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approveSuggestion_whenSuggestionNotFound_throwsIllegalArgumentException() {
        UUID suggestionId = UUID.randomUUID();
        when(reorderSuggestionRepository.findById(suggestionId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveSuggestion(suggestionId, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
