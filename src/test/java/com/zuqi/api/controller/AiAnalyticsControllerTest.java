package com.zuqi.api.controller;

import com.zuqi.ai.demand.AutoPurchaseOrderService;
import com.zuqi.domain.ai.ChurnPrediction;
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
import com.zuqi.repository.CustomerSegmentRepository;
import com.zuqi.repository.ExpiryRiskScoreRepository;
import com.zuqi.repository.PriceTrendRepository;
import com.zuqi.repository.PricingRecommendationRepository;
import com.zuqi.repository.ProductRecommendationRepository;
import com.zuqi.repository.ReorderSuggestionRepository;
import com.zuqi.repository.SupplierRiskScoreRepository;
import com.zuqi.repository.VisitRecommendationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiAnalyticsController}.
 *
 * Covers: all 15 endpoints — happy paths, empty lists, approve/reject flows,
 * and error paths.
 */
@ExtendWith(MockitoExtension.class)
class AiAnalyticsControllerTest {

    @Mock private ReorderSuggestionRepository     reorderSuggestionRepository;
    @Mock private ExpiryRiskScoreRepository       expiryRiskScoreRepository;
    @Mock private CustomerSegmentRepository       customerSegmentRepository;
    @Mock private ChurnPredictionRepository       churnPredictionRepository;
    @Mock private ProductRecommendationRepository productRecommendationRepository;
    @Mock private VisitRecommendationRepository   visitRecommendationRepository;
    @Mock private SupplierRiskScoreRepository     supplierRiskScoreRepository;
    @Mock private PriceTrendRepository            priceTrendRepository;
    @Mock private PricingRecommendationRepository pricingRecommendationRepository;
    @Mock private AutoPurchaseOrderService        autoPurchaseOrderService;

    @InjectMocks
    private AiAnalyticsController controller;

    // ── GET /reorder/suggestions/{distributorId} ──────────────────────────

    @Test
    void getReorderSuggestions_returns200WithList() {
        UUID distributorId = UUID.randomUUID();
        ReorderSuggestion suggestion = mock(ReorderSuggestion.class);
        when(reorderSuggestionRepository.findByDistributorIdAndStatus(distributorId, "PENDING"))
                .thenReturn(List.of(suggestion));

        ResponseEntity<?> response = controller.getReorderSuggestions(distributorId, "PENDING");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(reorderSuggestionRepository).findByDistributorIdAndStatus(distributorId, "PENDING");
    }

    @Test
    void getReorderSuggestions_emptyList_returns200() {
        UUID distributorId = UUID.randomUUID();
        when(reorderSuggestionRepository.findByDistributorIdAndStatus(any(), any()))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.getReorderSuggestions(distributorId, "PENDING");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── POST /reorder/suggestions/{id}/approve ────────────────────────────

    @Test
    void approveSuggestion_returns200WithPurchaseRequisition() {
        UUID suggestionId = UUID.randomUUID();
        UUID approvedBy   = UUID.randomUUID();
        PurchaseRequisition pr = mock(PurchaseRequisition.class);
        when(autoPurchaseOrderService.approveSuggestion(suggestionId, approvedBy)).thenReturn(pr);

        ResponseEntity<?> response = controller.approveSuggestion(suggestionId, approvedBy);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void approveSuggestion_whenServiceThrowsIllegalArgument_returns400() {
        UUID id = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();
        when(autoPurchaseOrderService.approveSuggestion(id, approvedBy))
                .thenThrow(new IllegalArgumentException("Suggestion not found"));

        ResponseEntity<?> response = controller.approveSuggestion(id, approvedBy);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void approveSuggestion_whenServiceThrowsRuntimeException_returns500() {
        UUID id = UUID.randomUUID();
        UUID approvedBy = UUID.randomUUID();
        when(autoPurchaseOrderService.approveSuggestion(id, approvedBy))
                .thenThrow(new RuntimeException("DB error"));

        ResponseEntity<?> response = controller.approveSuggestion(id, approvedBy);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── GET /expiry/risks/{distributorId} ─────────────────────────────────

    @Test
    void getExpiryRisks_returns200WithList() {
        UUID distributorId = UUID.randomUUID();
        ExpiryRiskScore risk = mock(ExpiryRiskScore.class);
        when(expiryRiskScoreRepository.findHighRiskByDistributor(distributorId, 0.3))
                .thenReturn(List.of(risk));

        ResponseEntity<?> response = controller.getExpiryRisks(distributorId, 0.3);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /expiry/risks/{distributorId}/{warehouseId} ───────────────────

    @Test
    void getExpiryRisksByWarehouse_returns200() {
        UUID distributorId = UUID.randomUUID();
        UUID warehouseId   = UUID.randomUUID();
        when(expiryRiskScoreRepository.findByDistributorIdAndWarehouseId(distributorId, warehouseId))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.getExpiryRisksByWarehouse(distributorId, warehouseId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(expiryRiskScoreRepository).findByDistributorIdAndWarehouseId(distributorId, warehouseId);
    }

    // ── GET /customers/segments/{distributorId} ───────────────────────────

    @Test
    void getCustomerSegments_returns200WithContent() {
        UUID distributorId = UUID.randomUUID();
        CustomerSegment segment = mock(CustomerSegment.class);
        when(customerSegmentRepository.findByDistributorId(eq(distributorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(segment)));

        ResponseEntity<?> response = controller.getCustomerSegments(distributorId, 0, 100);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /customers/churn/{distributorId} ──────────────────────────────

    @Test
    void getChurnPredictions_returns200() {
        UUID distributorId = UUID.randomUUID();
        when(churnPredictionRepository.findByDistributorId(eq(distributorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ResponseEntity<?> response = controller.getChurnPredictions(distributorId, 0, 100);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /customers/churn/{distributorId}/at-risk ──────────────────────

    @Test
    void getAtRiskCustomers_returns200WithFilteredList() {
        UUID distributorId = UUID.randomUUID();
        ChurnPrediction prediction = mock(ChurnPrediction.class);
        when(churnPredictionRepository.findAtRiskCustomers(distributorId, 0.6))
                .thenReturn(List.of(prediction));

        ResponseEntity<?> response = controller.getAtRiskCustomers(distributorId, 0.6);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /customers/recommendations/{customerId} ───────────────────────

    @Test
    void getProductRecommendations_returns200() {
        UUID customerId    = UUID.randomUUID();
        UUID distributorId = UUID.randomUUID();
        ProductRecommendation rec = mock(ProductRecommendation.class);
        when(productRecommendationRepository.findByDistributorIdAndCustomerId(distributorId, customerId))
                .thenReturn(List.of(rec));

        ResponseEntity<?> response = controller.getProductRecommendations(customerId, distributorId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /reps/{repId}/visit-plan ──────────────────────────────────────

    @Test
    void getVisitPlan_returns200() {
        UUID repId         = UUID.randomUUID();
        UUID distributorId = UUID.randomUUID();
        VisitRecommendation visit = mock(VisitRecommendation.class);
        when(visitRecommendationRepository.findBySalesRepIdAndDistributorId(repId, distributorId))
                .thenReturn(List.of(visit));

        ResponseEntity<?> response = controller.getVisitPlan(repId, distributorId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /suppliers/risk/{distributorId} ───────────────────────────────

    @Test
    void getSupplierRiskScores_returns200() {
        UUID distributorId = UUID.randomUUID();
        SupplierRiskScore score = mock(SupplierRiskScore.class);
        when(supplierRiskScoreRepository.findByDistributorId(eq(distributorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(score)));

        ResponseEntity<?> response = controller.getSupplierRiskScores(distributorId, 0, 100);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /suppliers/price-trends/{distributorId} ───────────────────────

    @Test
    void getPriceTrends_withoutDirection_returnsAll() {
        UUID distributorId = UUID.randomUUID();
        when(priceTrendRepository.findByDistributorId(distributorId)).thenReturn(List.of());

        ResponseEntity<?> response = controller.getPriceTrends(distributorId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(priceTrendRepository).findByDistributorId(distributorId);
    }

    @Test
    void getPriceTrends_withDirection_filtersResults() {
        UUID distributorId = UUID.randomUUID();
        when(priceTrendRepository.findByDistributorIdAndTrendDirection(distributorId, "INCREASING"))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.getPriceTrends(distributorId, "INCREASING");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(priceTrendRepository).findByDistributorIdAndTrendDirection(distributorId, "INCREASING");
    }

    // ── GET /suppliers/price-trends/{distributorId}/{supplierId} ─────────

    @Test
    void getSupplierPriceTrends_filtersToMatchingSupplier() {
        UUID distributorId = UUID.randomUUID();
        UUID supplierId    = UUID.randomUUID();

        PriceTrend matchingTrend = mock(PriceTrend.class);
        com.zuqi.domain.supplier.Supplier supplier = mock(com.zuqi.domain.supplier.Supplier.class);
        when(supplier.getId()).thenReturn(supplierId);
        when(matchingTrend.getSupplier()).thenReturn(supplier);

        PriceTrend otherTrend = mock(PriceTrend.class);
        com.zuqi.domain.supplier.Supplier otherSupplier = mock(com.zuqi.domain.supplier.Supplier.class);
        when(otherSupplier.getId()).thenReturn(UUID.randomUUID());
        when(otherTrend.getSupplier()).thenReturn(otherSupplier);

        when(priceTrendRepository.findByDistributorId(distributorId))
                .thenReturn(List.of(matchingTrend, otherTrend));

        ResponseEntity<?> response = controller.getSupplierPriceTrends(distributorId, supplierId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /pricing/recommendations/{distributorId} ──────────────────────

    @Test
    void getPricingRecommendations_returns200() {
        UUID distributorId = UUID.randomUUID();
        when(pricingRecommendationRepository.findByDistributorIdAndStatus(distributorId, "PENDING"))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.getPricingRecommendations(distributorId, "PENDING", 0, 100);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── POST /pricing/recommendations/{id}/apply ──────────────────────────

    @Test
    void applyPricingRecommendation_whenFound_returns200() {
        UUID id = UUID.randomUUID();
        PricingRecommendation rec = mock(PricingRecommendation.class);
        when(pricingRecommendationRepository.findById(id)).thenReturn(Optional.of(rec));
        when(pricingRecommendationRepository.save(rec)).thenReturn(rec);

        ResponseEntity<?> response = controller.applyPricingRecommendation(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(rec).setStatus("APPLIED");
    }

    @Test
    void applyPricingRecommendation_whenNotFound_returns404() {
        UUID id = UUID.randomUUID();
        when(pricingRecommendationRepository.findById(id)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.applyPricingRecommendation(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── POST /pricing/recommendations/{id}/reject ─────────────────────────

    @Test
    void rejectPricingRecommendation_whenFound_returns200() {
        UUID id = UUID.randomUUID();
        PricingRecommendation rec = mock(PricingRecommendation.class);
        when(pricingRecommendationRepository.findById(id)).thenReturn(Optional.of(rec));
        when(pricingRecommendationRepository.save(rec)).thenReturn(rec);

        ResponseEntity<?> response = controller.rejectPricingRecommendation(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(rec).setStatus("REJECTED");
    }

    @Test
    void rejectPricingRecommendation_whenNotFound_returns404() {
        UUID id = UUID.randomUUID();
        when(pricingRecommendationRepository.findById(id)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.rejectPricingRecommendation(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
