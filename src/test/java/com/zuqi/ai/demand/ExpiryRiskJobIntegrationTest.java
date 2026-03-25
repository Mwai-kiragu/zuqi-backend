package com.zuqi.ai.demand;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.ProductBatch;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.ProductBatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test: ExpiryRiskJob batch job runs within the Spring context.
 *
 * Verifies that:
 * - The job bean wires up correctly
 * - When no distributors exist, job completes without error
 * - When distributors have batches, the predictor is called for each batch
 */
@SpringBootTest
@ActiveProfiles("test")
class ExpiryRiskJobIntegrationTest {

    @Autowired
    private ExpiryRiskJob expiryRiskJob;

    @MockitoBean
    private DistributorRepository distributorRepository;

    @MockitoBean
    private ProductBatchRepository productBatchRepository;

    @MockitoBean
    private ExpiryRiskPredictor expiryRiskPredictor;

    @Test
    void contextLoads_jobBeanIsPresent() {
        assertThat(expiryRiskJob).isNotNull();
    }

    @Test
    void runExpiryRiskScoring_noDistributors_completesWithoutError() {
        when(distributorRepository.findAll()).thenReturn(List.of());

        assertThatNoException().isThrownBy(() -> expiryRiskJob.runExpiryRiskScoring());

        verify(productBatchRepository, never()).findExpiringBatches(any(), any());
        verify(expiryRiskPredictor, never()).predict(any(), any(), any(), any());
    }

    @Test
    void runExpiryRiskScoring_withDistributorNoBatches_completesWithoutError() {
        Distributor distributor = mock(Distributor.class);
        when(distributor.getId()).thenReturn(UUID.randomUUID());
        when(distributorRepository.findAll()).thenReturn(List.of(distributor));
        when(productBatchRepository.findExpiringBatches(any(UUID.class), any(LocalDate.class)))
                .thenReturn(List.of());

        assertThatNoException().isThrownBy(() -> expiryRiskJob.runExpiryRiskScoring());

        verify(expiryRiskPredictor, never()).predict(any(), any(), any(), any());
    }

    @Test
    void runExpiryRiskScoring_withExpiringBatches_callsPredictorForEachBatch() {
        UUID distributorId = UUID.randomUUID();
        Distributor distributor = mock(Distributor.class);
        when(distributor.getId()).thenReturn(distributorId);
        when(distributorRepository.findAll()).thenReturn(List.of(distributor));

        UUID warehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getId()).thenReturn(warehouseId);

        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);

        ProductBatch batch = mock(ProductBatch.class);
        when(batch.getId()).thenReturn(batchId);
        when(batch.getWarehouse()).thenReturn(warehouse);
        when(batch.getProduct()).thenReturn(product);

        when(productBatchRepository.findExpiringBatches(eq(distributorId), any(LocalDate.class)))
                .thenReturn(List.of(batch));

        expiryRiskJob.runExpiryRiskScoring();

        verify(expiryRiskPredictor, times(1))
                .predict(distributorId, warehouseId, productId, batchId);
    }

    @Test
    void runExpiryRiskScoring_predictorThrows_continuesWithOtherBatches() {
        UUID distributorId = UUID.randomUUID();
        Distributor distributor = mock(Distributor.class);
        when(distributor.getId()).thenReturn(distributorId);
        when(distributorRepository.findAll()).thenReturn(List.of(distributor));

        // Two batches — first throws, second should still be processed
        UUID wId = UUID.randomUUID();
        UUID pId1 = UUID.randomUUID();
        UUID pId2 = UUID.randomUUID();
        UUID bId1 = UUID.randomUUID();
        UUID bId2 = UUID.randomUUID();

        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getId()).thenReturn(wId);

        Product product1 = mock(Product.class);
        when(product1.getId()).thenReturn(pId1);
        Product product2 = mock(Product.class);
        when(product2.getId()).thenReturn(pId2);

        ProductBatch batch1 = mock(ProductBatch.class);
        when(batch1.getId()).thenReturn(bId1);
        when(batch1.getWarehouse()).thenReturn(warehouse);
        when(batch1.getProduct()).thenReturn(product1);

        ProductBatch batch2 = mock(ProductBatch.class);
        when(batch2.getId()).thenReturn(bId2);
        when(batch2.getWarehouse()).thenReturn(warehouse);
        when(batch2.getProduct()).thenReturn(product2);

        when(productBatchRepository.findExpiringBatches(eq(distributorId), any()))
                .thenReturn(List.of(batch1, batch2));

        doThrow(new RuntimeException("prediction error"))
                .when(expiryRiskPredictor).predict(distributorId, wId, pId1, bId1);

        assertThatNoException().isThrownBy(() -> expiryRiskJob.runExpiryRiskScoring());

        verify(expiryRiskPredictor, times(1)).predict(distributorId, wId, pId2, bId2);
    }

}
