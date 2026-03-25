package com.zuqi.ai.procurement;

import com.zuqi.domain.ai.PriceTrend;
import com.zuqi.domain.ai.ReorderSuggestion;
import com.zuqi.domain.ai.SupplierRiskScore;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.repository.PriceTrendRepository;
import com.zuqi.repository.ReorderSuggestionRepository;
import com.zuqi.repository.SupplierRiskScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderQuantityOptimizerTest {

    @Mock ReorderSuggestionRepository reorderSuggestionRepository;
    @Mock PriceTrendRepository priceTrendRepository;
    @Mock SupplierRiskScoreRepository supplierRiskScoreRepository;

    @InjectMocks
    OrderQuantityOptimizer optimizer;

    private UUID distributorId;
    private UUID productId;
    private UUID supplierId;
    private ReorderSuggestion suggestion;

    @BeforeEach
    void setUp() {
        distributorId = UUID.randomUUID();
        productId     = UUID.randomUUID();
        supplierId    = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);

        Distributor distributor = new Distributor();
        distributor.setId(distributorId);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());

        suggestion = ReorderSuggestion.builder()
                .distributor(distributor)
                .warehouse(warehouse)
                .product(product)
                .economicOrderQty(100.0)
                .suggestedQty(100.0)
                .build();

        when(reorderSuggestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private SupplierRiskScore riskScore(String tier, double score) {
        Supplier supplier = new Supplier();
        supplier.setId(supplierId);
        Distributor distributor = new Distributor();
        distributor.setId(distributorId);
        SupplierRiskScore s = SupplierRiskScore.builder()
                .supplier(supplier)
                .distributor(distributor)
                .riskTier(tier)
                .riskScore(score)
                .build();
        return s;
    }

    private PriceTrend priceTrend(String direction, double pctChange) {
        PriceTrend t = new PriceTrend();
        t.setTrendDirection(direction);
        t.setPctChange3m(pctChange);
        return t;
    }

    @Test
    void noSupplierData_returnsBaseEoq() {
        when(supplierRiskScoreRepository.findByDistributorId(eq(distributorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        OrderQuantityOptimizer.OptimizedOrderResult result =
                optimizer.optimize(suggestion, distributorId);

        assertThat(result.adjustedQty()).isEqualTo(100.0);
        assertThat(result.selectedSupplierId()).isNull();
    }

    @Test
    void preferredSupplierStableTrend_noAdjustment() {
        when(supplierRiskScoreRepository.findByDistributorId(eq(distributorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(riskScore("PREFERRED", 90.0))));
        when(priceTrendRepository.findByDistributorIdAndSupplierIdAndProductId(
                distributorId, supplierId, productId))
                .thenReturn(Optional.of(priceTrend("STABLE", 0.0)));

        OrderQuantityOptimizer.OptimizedOrderResult result =
                optimizer.optimize(suggestion, distributorId);

        // 100 × 1.0 × 1.0 = 100
        assertThat(result.adjustedQty()).isEqualTo(100.0);
        assertThat(result.supplierFactor()).isEqualTo(1.0);
        assertThat(result.priceTrendFactor()).isEqualTo(1.0);
    }

    @Test
    void preferredSupplierIncreasingTrend_quantityIncreasedBy30Pct() {
        when(supplierRiskScoreRepository.findByDistributorId(eq(distributorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(riskScore("PREFERRED", 85.0))));
        when(priceTrendRepository.findByDistributorIdAndSupplierIdAndProductId(
                distributorId, supplierId, productId))
                .thenReturn(Optional.of(priceTrend("INCREASING", 12.0)));

        OrderQuantityOptimizer.OptimizedOrderResult result =
                optimizer.optimize(suggestion, distributorId);

        // 100 × 1.0 × 1.30 = 130
        assertThat(result.adjustedQty()).isEqualTo(130.0);
    }

    @Test
    void criticalSupplierDecreasingTrend_quantitySignificantlyReduced() {
        when(supplierRiskScoreRepository.findByDistributorId(eq(distributorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(riskScore("CRITICAL", 20.0))));
        when(priceTrendRepository.findByDistributorIdAndSupplierIdAndProductId(
                distributorId, supplierId, productId))
                .thenReturn(Optional.of(priceTrend("DECREASING", -8.0)));

        OrderQuantityOptimizer.OptimizedOrderResult result =
                optimizer.optimize(suggestion, distributorId);

        // 100 × 0.60 × 0.80 = 48
        assertThat(result.adjustedQty()).isEqualTo(48.0);
    }

    @Test
    void atRiskSupplierNoTrendData_appliesSupplierPenaltyOnly() {
        when(supplierRiskScoreRepository.findByDistributorId(eq(distributorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(riskScore("AT_RISK", 30.0))));
        when(priceTrendRepository.findByDistributorIdAndSupplierIdAndProductId(
                distributorId, supplierId, productId))
                .thenReturn(Optional.empty());

        OrderQuantityOptimizer.OptimizedOrderResult result =
                optimizer.optimize(suggestion, distributorId);

        // 100 × 0.75 × 1.0 = 75
        assertThat(result.adjustedQty()).isEqualTo(75.0);
        assertThat(result.supplierFactor()).isEqualTo(0.75);
    }

    @Test
    void selectedSupplierIsSavedOnSuggestion() {
        when(supplierRiskScoreRepository.findByDistributorId(eq(distributorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(riskScore("RELIABLE", 70.0))));
        when(priceTrendRepository.findByDistributorIdAndSupplierIdAndProductId(
                distributorId, supplierId, productId))
                .thenReturn(Optional.empty());

        optimizer.optimize(suggestion, distributorId);

        assertThat(suggestion.getSupplierId()).isEqualTo(supplierId);
        verify(reorderSuggestionRepository).save(suggestion);
    }

    @Test
    void resultContainsCorrectProductAndSupplierIds() {
        when(supplierRiskScoreRepository.findByDistributorId(eq(distributorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(riskScore("PREFERRED", 92.0))));
        when(priceTrendRepository.findByDistributorIdAndSupplierIdAndProductId(
                distributorId, supplierId, productId))
                .thenReturn(Optional.empty());

        OrderQuantityOptimizer.OptimizedOrderResult result =
                optimizer.optimize(suggestion, distributorId);

        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.selectedSupplierId()).isEqualTo(supplierId);
        assertThat(result.baseEoq()).isEqualTo(100.0);
    }
}
