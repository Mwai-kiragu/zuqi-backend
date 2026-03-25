package com.zuqi.ai.demand;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.StockRepository;
import com.zuqi.repository.WarehouseRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Nightly batch job: computes reorder suggestions for all warehouse-SKU combinations
 * whose stock is below the reorder point.
 *
 * Schedule: Daily at 5:00 AM EAT
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReorderOptimizationJob {

    private final DistributorRepository distributorRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockRepository stockRepository;
    private final ReorderOptimizationService reorderService;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${zuqi.ai.inventory.reorder-schedule:0 0 5 * * *}")
    public void runReorderOptimization() {
        log.info("=== Starting nightly reorder optimization job ===");
        long start = System.currentTimeMillis();

        int totalSuggestions = 0;
        int totalErrors = 0;

        List<Distributor> distributors = distributorRepository.findAll();
        for (Distributor distributor : distributors) {
            try {
                int count = processDistributor(distributor);
                totalSuggestions += count;
            } catch (Exception e) {
                log.error("Reorder job failed for distributor {}: {}", distributor.getId(), e.getMessage(), e);
                totalErrors++;
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== Reorder optimization complete: {} suggestions, {} errors, {}ms ===",
                totalSuggestions, totalErrors, duration);

        Counter.builder("zuqi_ai_reorder_suggestions_generated")
                .tag("job", "reorder_optimization")
                .register(meterRegistry)
                .increment(totalSuggestions);
    }

    private int processDistributor(Distributor distributor) {
        List<Warehouse> warehouses = warehouseRepository
                .findByDistributorIdAndActiveTrue(distributor.getId());
        int count = 0;

        for (Warehouse warehouse : warehouses) {
            List<Stock> stockItems = stockRepository.findByWarehouseId(
                    warehouse.getId(), Pageable.unpaged()).getContent();

            for (Stock stock : stockItems) {
                try {
                    var suggestion = reorderService.computeSuggestion(
                            distributor.getId(),
                            warehouse.getId(),
                            stock.getProduct().getId()
                    );
                    if (suggestion != null) {
                        count++;
                    }
                } catch (Exception e) {
                    log.warn("Reorder failed for stock {}: {}", stock.getId(), e.getMessage());
                }
            }
        }
        return count;
    }
}
