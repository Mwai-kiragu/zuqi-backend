package com.zuqi.ai.procurement;

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
 * Monthly batch job: analyzes price trends for all (supplier, product) pairs
 * per distributor.
 * Schedule: 1st of every month at 02:30.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceTrendJob {

    private final DistributorRepository distributorRepository;
    private final PriceTrendAnalyzer priceTrendAnalyzer;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${zuqi.ai.procurement.price-trends-cron:0 30 2 1 * *}")
    public void runPriceTrendAnalysis() {
        log.info("=== Starting monthly price trend analysis job ===");
        long start = System.currentTimeMillis();
        int totalPairs = 0;
        int totalErrors = 0;

        List<Distributor> distributors = distributorRepository.findAll();

        for (Distributor distributor : distributors) {
            try {
                int pairs = priceTrendAnalyzer.analyze(distributor.getId());
                totalPairs += pairs;
            } catch (Exception e) {
                log.warn("[PriceTrendJob] Failed distributor={}: {}",
                        distributor.getId(), e.getMessage());
                totalErrors++;
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== Price trend analysis complete: {} pairs analyzed, {} errors, {}ms ===",
                totalPairs, totalErrors, duration);

        Counter.builder("zuqi_ai_price_trends_analyzed")
                .tag("job", "price_trend")
                .register(meterRegistry)
                .increment(totalPairs);
    }
}
