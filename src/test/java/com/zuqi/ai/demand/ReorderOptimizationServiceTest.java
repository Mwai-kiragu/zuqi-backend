package com.zuqi.ai.demand;

import com.zuqi.ai.feature.ReorderFeatureServiceImpl;
import com.zuqi.ai.feature.ReorderFeatures;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.DataPhase;
import com.zuqi.domain.ai.ReorderSuggestion;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.ReorderSuggestionRepository;
import com.zuqi.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReorderOptimizationServiceTest {

    @Mock private ReorderFeatureServiceImpl featureService;
    @Mock private ModelPhaseService phaseService;
    @Mock private DataPhaseTracker phaseTracker;
    @Mock private ReorderSuggestionRepository reorderSuggestionRepository;
    @Mock private DistributorRepository distributorRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ProductRepository productRepository;

    @InjectMocks
    private ReorderOptimizationService service;

    // ── EOQ formula ─────────────────────────────────────────────────────────

    @Test
    void computeEoq_withKnownInputs_returnsCorrectValue() {
        // EOQ = sqrt(2 * 1000 * 500 / (0.25 * 100)) = sqrt(40000) = 200
        double eoq = service.computeEoq(1000, 500, 0.25, 100);
        assertThat(eoq).isEqualTo(200.0);
    }

    @Test
    void computeEoq_withZeroHoldingCost_returnsFallbackMonthlySupply() {
        double eoq = service.computeEoq(1200, 500, 0.0, 100);
        assertThat(eoq).isEqualTo(100.0); // 1200 / 12 = 100
    }

    @Test
    void computeEoq_withZeroDemand_returnsFallbackMinOne() {
        double eoq = service.computeEoq(0, 500, 0.25, 100);
        assertThat(eoq).isEqualTo(1.0); // max(0/12, 1) = 1
    }

    // ── Safety stock formula ─────────────────────────────────────────────────

    @Test
    void computeSafetyStock_withKnownInputs_returnsPositiveValue() {
        // avgDailyDemand=10, cv=0.2, leadTime=7, stddevLead=2
        // sigma_d = 2, SS = 1.645 * sqrt(7*4 + 100*4) = 1.645 * sqrt(428) ≈ 34.05 → ceil = 35
        double ss = service.computeSafetyStock(10, 0.2, 7, 2);
        assertThat(ss).isGreaterThan(0);
        assertThat(ss).isLessThan(200); // sanity upper bound
    }

    @Test
    void computeSafetyStock_withZeroDemand_returnsZero() {
        double ss = service.computeSafetyStock(0, 0.2, 7, 2);
        assertThat(ss).isEqualTo(0.0);
    }

    // ── Reorder point trigger logic ─────────────────────────────────────────

    @Test
    void computeSuggestion_whenStockAboveReorderPoint_returnsNull() {
        UUID distributorId = UUID.randomUUID();
        UUID warehouseId   = UUID.randomUUID();
        UUID productId     = UUID.randomUUID();

        // Stock=500, demand=1/day, leadTime=7 → ROP≈7+ss, 500 >> 7
        ReorderFeatures features = new ReorderFeatures(
                distributorId, warehouseId, productId,
                1.0,   // avgDailyDemand
                0.2,   // cv
                7.0,   // leadTimeAvg
                2.0,   // leadTimeStddev
                0.25,  // carryingCostRate
                500.0, // orderingCostFixed
                30.0,  // stockoutCost
                500.0, // currentStockLevel — well above reorder point
                0.0,   // pendingOrders
                500.0, // daysOfSupply
                100.0  // unitCost
        );

        when(featureService.computeFeatures(distributorId, warehouseId, productId))
                .thenReturn(features);

        ReorderSuggestion result = service.computeSuggestion(distributorId, warehouseId, productId);

        assertThat(result).isNull();
        verify(reorderSuggestionRepository, never()).save(any());
    }

    @Test
    void computeSuggestion_whenStockBelowReorderPoint_savesAndReturnsSuggestion() {
        UUID distributorId = UUID.randomUUID();
        UUID warehouseId   = UUID.randomUUID();
        UUID productId     = UUID.randomUUID();

        // Stock=2, demand=10/day, leadTime=7 → ROP ≈ 70+ss >> 2
        ReorderFeatures features = new ReorderFeatures(
                distributorId, warehouseId, productId,
                10.0,  // avgDailyDemand — high demand
                0.2, 7.0, 2.0, 0.25, 500.0, 300.0,
                2.0,   // currentStockLevel — well below reorder point
                0.0, 0.2, 100.0
        );

        when(featureService.computeFeatures(distributorId, warehouseId, productId))
                .thenReturn(features);
        when(phaseService.applyModifier(0.8, "reorder_optimizer")).thenReturn(0.48);
        when(phaseTracker.getPhase("reorder_optimizer", distributorId)).thenReturn(DataPhase.SYNTHETIC);

        Distributor distributor = new Distributor();
        Warehouse   warehouse   = new Warehouse();
        Product     product     = new Product();

        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        ReorderSuggestion saved = ReorderSuggestion.builder()
                .suggestedQty(200.0)
                .status("PENDING")
                .build();
        when(reorderSuggestionRepository.save(any())).thenReturn(saved);

        ReorderSuggestion result = service.computeSuggestion(distributorId, warehouseId, productId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(reorderSuggestionRepository).save(any());
    }
}
