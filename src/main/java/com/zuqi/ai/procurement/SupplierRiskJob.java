package com.zuqi.ai.procurement;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.SupplierRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Monthly batch job: scores all suppliers for each distributor.
 * Schedule: 1st of every month at 02:00.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SupplierRiskJob {

    private final DistributorRepository distributorRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierRiskScorer supplierRiskScorer;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${zuqi.ai.procurement.supplier-risk-cron:0 0 2 1 * *}")
    public void runSupplierRiskScoring() {
        log.info("=== Starting monthly supplier risk scoring job ===");
        long start = System.currentTimeMillis();
        int totalScored = 0;
        int totalErrors = 0;

        List<Distributor> distributors = distributorRepository.findAll();
        List<Supplier> suppliers = supplierRepository.findAll();

        for (Distributor distributor : distributors) {
            for (Supplier supplier : suppliers) {
                try {
                    supplierRiskScorer.score(supplier.getId(), distributor.getId());
                    totalScored++;
                } catch (Exception e) {
                    log.warn("[SupplierRiskJob] Failed supplier={} distributor={}: {}",
                            supplier.getId(), distributor.getId(), e.getMessage());
                    totalErrors++;
                }
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== Supplier risk scoring complete: {} scored, {} errors, {}ms ===",
                totalScored, totalErrors, duration);

        Counter.builder("zuqi_ai_supplier_risk_scored")
                .tag("job", "supplier_risk")
                .register(meterRegistry)
                .increment(totalScored);
    }
}
