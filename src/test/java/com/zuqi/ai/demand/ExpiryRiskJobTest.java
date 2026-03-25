package com.zuqi.ai.demand;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.ProductBatch;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.ProductBatchRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpiryRiskJobTest {

    @Mock private DistributorRepository distributorRepository;
    @Mock private ProductBatchRepository productBatchRepository;
    @Mock private ExpiryRiskPredictor expiryRiskPredictor;

    private ExpiryRiskJob job;

    @BeforeEach
    void setUp() {
        job = new ExpiryRiskJob(
                distributorRepository, productBatchRepository,
                expiryRiskPredictor, new SimpleMeterRegistry());
    }

    @Test
    void runExpiryRiskScoring_noDistributors_completesWithoutError() {
        when(distributorRepository.findAll()).thenReturn(List.of());

        job.runExpiryRiskScoring();

        verify(expiryRiskPredictor, never()).predict(any(), any(), any(), any());
    }

    @Test
    void runExpiryRiskScoring_oneDistributorWithExpiringSoonBatch_callsPredict() {
        UUID distId  = UUID.randomUUID();
        UUID whId    = UUID.randomUUID();
        UUID prodId  = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        Distributor distributor = new Distributor();
        distributor.setId(distId);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(whId);

        Product product = new Product();
        product.setId(prodId);

        ProductBatch batch = new ProductBatch();
        batch.setId(batchId);
        batch.setWarehouse(warehouse);
        batch.setProduct(product);
        batch.setExpiryDate(LocalDate.now().plusDays(30)); // within 90-day window

        when(distributorRepository.findAll()).thenReturn(List.of(distributor));
        when(productBatchRepository.findExpiringBatches(eq(distId), any(LocalDate.class)))
                .thenReturn(List.of(batch));
        when(expiryRiskPredictor.predict(distId, whId, prodId, batchId))
                .thenReturn(null);

        job.runExpiryRiskScoring();

        verify(expiryRiskPredictor).predict(distId, whId, prodId, batchId);
    }

    @Test
    void runExpiryRiskScoring_predictorThrowsForOneBatch_continuesWithRest() {
        UUID distId = UUID.randomUUID();

        Distributor distributor = new Distributor();
        distributor.setId(distId);

        Warehouse wh1 = new Warehouse(); wh1.setId(UUID.randomUUID());
        Warehouse wh2 = new Warehouse(); wh2.setId(UUID.randomUUID());
        Product p1 = new Product(); p1.setId(UUID.randomUUID());
        Product p2 = new Product(); p2.setId(UUID.randomUUID());

        ProductBatch batch1 = new ProductBatch();
        batch1.setId(UUID.randomUUID());
        batch1.setWarehouse(wh1);
        batch1.setProduct(p1);
        batch1.setExpiryDate(LocalDate.now().plusDays(10));

        ProductBatch batch2 = new ProductBatch();
        batch2.setId(UUID.randomUUID());
        batch2.setWarehouse(wh2);
        batch2.setProduct(p2);
        batch2.setExpiryDate(LocalDate.now().plusDays(20));

        when(distributorRepository.findAll()).thenReturn(List.of(distributor));
        when(productBatchRepository.findExpiringBatches(eq(distId), any(LocalDate.class)))
                .thenReturn(List.of(batch1, batch2));

        when(expiryRiskPredictor.predict(distId, wh1.getId(), p1.getId(), batch1.getId()))
                .thenThrow(new RuntimeException("Model error"));
        when(expiryRiskPredictor.predict(distId, wh2.getId(), p2.getId(), batch2.getId()))
                .thenReturn(null);

        // Should complete without throwing
        job.runExpiryRiskScoring();

        // Both batches were attempted
        verify(expiryRiskPredictor, times(2)).predict(any(), any(), any(), any());
    }
}
