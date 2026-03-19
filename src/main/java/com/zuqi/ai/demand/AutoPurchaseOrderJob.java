package com.zuqi.ai.demand;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.DistributorRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Daily job: converts high-confidence AI reorder suggestions into Purchase Requisitions.
 * Only runs for distributors in REAL data phase.
 *
 * Schedule: Daily at 6:00 AM EAT (after ReorderOptimizationJob at 5AM)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoPurchaseOrderJob {

    private final DistributorRepository distributorRepository;
    private final AutoPurchaseOrderService autoPOService;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${zuqi.ai.inventory.auto-po-schedule:0 0 6 * * *}")
    public void runAutoPurchaseOrders() {
        log.info("=== Starting auto purchase order job ===");
        long start = System.currentTimeMillis();

        int totalCreated = 0;
        List<Distributor> distributors = distributorRepository.findAll();

        for (Distributor distributor : distributors) {
            try {
                int count = autoPOService.processApprovedSuggestions(distributor.getId());
                totalCreated += count;
            } catch (Exception e) {
                log.error("Auto-PO job failed for distributor {}: {}",
                        distributor.getId(), e.getMessage(), e);
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== Auto-PO job complete: {} PRs created, {}ms ===", totalCreated, duration);

        Counter.builder("zuqi_ai_auto_po_prs_created")
                .tag("job", "auto_purchase_order")
                .register(meterRegistry)
                .increment(totalCreated);
    }
}
