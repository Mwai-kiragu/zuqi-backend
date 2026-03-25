package com.zuqi.ai.crm;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.DistributorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job that regenerates product recommendations for all active distributors
 * every Monday at 05:00 AM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductRecommendationJob {

    private final DistributorRepository distributorRepository;
    private final ProductRecommendationService recommendationService;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${zuqi.ai.crm.product-rec-cron:0 0 5 ? * MON}")
    public void runProductRecommendations() {
        log.info("[ProductRecJob] Starting weekly product recommendation run");
        long start = System.currentTimeMillis();

        List<Distributor> distributors = distributorRepository.findByActiveTrue();
        int totalSaved = 0;

        for (Distributor distributor : distributors) {
            try {
                int count = recommendationService.generateRecommendations(distributor.getId());
                totalSaved += count;
            } catch (Exception e) {
                log.error("[ProductRecJob] Failed for distributor={}: {}",
                        distributor.getId(), e.getMessage(), e);
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("[ProductRecJob] Saved {} recommendations in {}ms", totalSaved, duration);
        meterRegistry.timer("ai.crm.product_rec.duration").record(duration, TimeUnit.MILLISECONDS);
    }
}
