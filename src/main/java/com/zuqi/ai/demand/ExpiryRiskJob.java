package com.zuqi.ai.demand;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.ProductBatch;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.ProductBatchRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Nightly batch job: scores all active batches for expiry risk.
 *
 * Schedule: Daily at 5:30 AM EAT (after ReorderOptimizationJob)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryRiskJob {

    private final DistributorRepository distributorRepository;
    private final ProductBatchRepository productBatchRepository;
    private final ExpiryRiskPredictor expiryRiskPredictor;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${zuqi.ai.inventory.expiry-schedule:0 30 5 * * *}")
    public void runExpiryRiskScoring() {
        log.info("=== Starting nightly expiry risk scoring job ===");
        long start = System.currentTimeMillis();

        int totalScored = 0;
        int totalErrors = 0;

        List<Distributor> distributors = distributorRepository.findAll();
        for (Distributor distributor : distributors) {
            try {
                int count = processDistributor(distributor);
                totalScored += count;
            } catch (Exception e) {
                log.error("Expiry risk job failed for distributor {}: {}",
                        distributor.getId(), e.getMessage(), e);
                totalErrors++;
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== Expiry risk scoring complete: {} batches scored, {} errors, {}ms ===",
                totalScored, totalErrors, duration);

        Counter.builder("zuqi_ai_expiry_risk_batches_scored")
                .tag("job", "expiry_risk")
                .register(meterRegistry)
                .increment(totalScored);
    }

    private int processDistributor(Distributor distributor) {
        // Score batches expiring within 90 days
        LocalDate cutoff = LocalDate.now().plusDays(90);
        List<ProductBatch> batches = productBatchRepository
                .findExpiringBatches(distributor.getId(), cutoff);

        int count = 0;
        for (ProductBatch batch : batches) {
            try {
                expiryRiskPredictor.predict(
                        distributor.getId(),
                        batch.getWarehouse().getId(),
                        batch.getProduct().getId(),
                        batch.getId()
                );
                count++;
            } catch (Exception e) {
                log.warn("Expiry risk failed for batch {}: {}", batch.getId(), e.getMessage());
            }
        }
        return count;
    }
}
