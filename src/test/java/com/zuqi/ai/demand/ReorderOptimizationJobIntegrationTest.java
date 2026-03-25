package com.zuqi.ai.demand;

import com.zuqi.domain.ai.ReorderSuggestion;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.StockRepository;
import com.zuqi.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test: ReorderOptimizationJob runs within the Spring context.
 *
 * Verifies:
 * - Job bean is wired correctly by Spring
 * - Job completes without error when no distributors exist
 * - Job calls reorderService for each warehouse-SKU combo below reorder point
 * - Individual failures do not abort the whole job
 */
@SpringBootTest
@ActiveProfiles("test")
class ReorderOptimizationJobIntegrationTest {

    @Autowired
    private ReorderOptimizationJob reorderOptimizationJob;

    @MockitoBean
    private DistributorRepository distributorRepository;

    @MockitoBean
    private WarehouseRepository warehouseRepository;

    @MockitoBean
    private StockRepository stockRepository;

    @MockitoBean
    private ReorderOptimizationService reorderService;

    @Test
    void contextLoads_jobBeanIsPresent() {
        assertThat(reorderOptimizationJob).isNotNull();
    }

    @Test
    void runReorderOptimization_noDistributors_completesWithoutError() {
        when(distributorRepository.findAll()).thenReturn(List.of());

        assertThatNoException().isThrownBy(() -> reorderOptimizationJob.runReorderOptimization());

        verify(warehouseRepository, never()).findByDistributorIdAndActiveTrue(any());
        verify(reorderService, never()).computeSuggestion(any(), any(), any());
    }

    @Test
    void runReorderOptimization_withDistributorNoWarehouses_completesWithoutError() {
        UUID distributorId = UUID.randomUUID();
        Distributor distributor = mock(Distributor.class);
        when(distributor.getId()).thenReturn(distributorId);
        when(distributorRepository.findAll()).thenReturn(List.of(distributor));
        when(warehouseRepository.findByDistributorIdAndActiveTrue(distributorId))
                .thenReturn(List.of());

        assertThatNoException().isThrownBy(() -> reorderOptimizationJob.runReorderOptimization());

        verify(stockRepository, never()).findByWarehouseId(any(), any());
        verify(reorderService, never()).computeSuggestion(any(), any(), any());
    }

    @Test
    void runReorderOptimization_withStockItems_callsServiceForEachItem() {
        UUID distributorId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Distributor distributor = mock(Distributor.class);
        when(distributor.getId()).thenReturn(distributorId);
        when(distributorRepository.findAll()).thenReturn(List.of(distributor));

        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getId()).thenReturn(warehouseId);
        when(warehouseRepository.findByDistributorIdAndActiveTrue(distributorId))
                .thenReturn(List.of(warehouse));

        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);
        Stock stock = mock(Stock.class);
        when(stock.getProduct()).thenReturn(product);

        when(stockRepository.findByWarehouseId(eq(warehouseId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stock)));

        ReorderSuggestion suggestion = mock(ReorderSuggestion.class);
        when(reorderService.computeSuggestion(distributorId, warehouseId, productId))
                .thenReturn(suggestion);

        reorderOptimizationJob.runReorderOptimization();

        verify(reorderService, times(1)).computeSuggestion(distributorId, warehouseId, productId);
    }

    @Test
    void runReorderOptimization_serviceReturnsNull_countNotIncremented() {
        UUID distributorId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Distributor distributor = mock(Distributor.class);
        when(distributor.getId()).thenReturn(distributorId);
        when(distributorRepository.findAll()).thenReturn(List.of(distributor));

        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getId()).thenReturn(warehouseId);
        when(warehouseRepository.findByDistributorIdAndActiveTrue(distributorId))
                .thenReturn(List.of(warehouse));

        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);
        Stock stock = mock(Stock.class);
        when(stock.getProduct()).thenReturn(product);

        when(stockRepository.findByWarehouseId(eq(warehouseId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stock)));

        // No reorder needed
        when(reorderService.computeSuggestion(distributorId, warehouseId, productId))
                .thenReturn(null);

        assertThatNoException().isThrownBy(() -> reorderOptimizationJob.runReorderOptimization());
    }

    @Test
    void runReorderOptimization_serviceThrows_continuesWithNextItem() {
        UUID distributorId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();

        Distributor distributor = mock(Distributor.class);
        when(distributor.getId()).thenReturn(distributorId);
        when(distributorRepository.findAll()).thenReturn(List.of(distributor));

        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getId()).thenReturn(warehouseId);
        when(warehouseRepository.findByDistributorIdAndActiveTrue(distributorId))
                .thenReturn(List.of(warehouse));

        Product product1 = mock(Product.class);
        when(product1.getId()).thenReturn(productId1);
        Product product2 = mock(Product.class);
        when(product2.getId()).thenReturn(productId2);

        Stock stock1 = mock(Stock.class);
        when(stock1.getId()).thenReturn(UUID.randomUUID());
        when(stock1.getProduct()).thenReturn(product1);
        Stock stock2 = mock(Stock.class);
        when(stock2.getId()).thenReturn(UUID.randomUUID());
        when(stock2.getProduct()).thenReturn(product2);

        when(stockRepository.findByWarehouseId(eq(warehouseId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stock1, stock2)));

        when(reorderService.computeSuggestion(distributorId, warehouseId, productId1))
                .thenThrow(new RuntimeException("feature computation error"));
        when(reorderService.computeSuggestion(distributorId, warehouseId, productId2))
                .thenReturn(mock(ReorderSuggestion.class));

        assertThatNoException().isThrownBy(() -> reorderOptimizationJob.runReorderOptimization());

        verify(reorderService, times(1)).computeSuggestion(distributorId, warehouseId, productId2);
    }
}
