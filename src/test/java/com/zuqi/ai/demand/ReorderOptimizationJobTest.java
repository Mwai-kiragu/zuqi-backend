package com.zuqi.ai.demand;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.StockRepository;
import com.zuqi.repository.WarehouseRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReorderOptimizationJobTest {

    @Mock private DistributorRepository distributorRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private StockRepository stockRepository;
    @Mock private ReorderOptimizationService reorderService;

    private ReorderOptimizationJob job;

    @BeforeEach
    void setUp() {
        job = new ReorderOptimizationJob(
                distributorRepository, warehouseRepository,
                stockRepository, reorderService,
                new SimpleMeterRegistry());
    }

    @Test
    void runReorderOptimization_noDistributors_completesWithoutError() {
        when(distributorRepository.findAll()).thenReturn(List.of());

        job.runReorderOptimization();

        verify(reorderService, never()).computeSuggestion(any(), any(), any());
    }

    @Test
    void runReorderOptimization_oneDistributorOneWarehouseOneStock_callsComputeSuggestion() {
        UUID distId = UUID.randomUUID();
        UUID whId   = UUID.randomUUID();
        UUID prodId = UUID.randomUUID();

        Distributor distributor = new Distributor();
        distributor.setId(distId);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(whId);

        Product product = new Product();
        product.setId(prodId);

        Stock stock = new Stock();
        stock.setProduct(product);
        stock.setWarehouse(warehouse);

        when(distributorRepository.findAll()).thenReturn(List.of(distributor));
        when(warehouseRepository.findByDistributorIdAndActiveTrue(distId))
                .thenReturn(List.of(warehouse));
        when(stockRepository.findByWarehouseId(eq(whId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stock)));
        when(reorderService.computeSuggestion(distId, whId, prodId)).thenReturn(null);

        job.runReorderOptimization();

        verify(reorderService).computeSuggestion(distId, whId, prodId);
    }

    @Test
    void runReorderOptimization_distributorThrows_continuesWithOthers() {
        Distributor bad  = new Distributor(); bad.setId(UUID.randomUUID());
        Distributor good = new Distributor(); good.setId(UUID.randomUUID());

        when(distributorRepository.findAll()).thenReturn(List.of(bad, good));
        when(warehouseRepository.findByDistributorIdAndActiveTrue(bad.getId()))
                .thenThrow(new RuntimeException("DB error"));
        when(warehouseRepository.findByDistributorIdAndActiveTrue(good.getId()))
                .thenReturn(List.of());

        // Should not throw
        job.runReorderOptimization();

        verify(warehouseRepository, times(2)).findByDistributorIdAndActiveTrue(any());
    }
}
