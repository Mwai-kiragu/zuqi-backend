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
 * Scheduled job that recomputes customer health scores for all active distributors
 * every Monday at 03:30 AM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerHealthScoreJob {

    private final DistributorRepository distributorRepository;
    private final CustomerHealthScoreService healthScoreService;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${zuqi.ai.crm.health-score-cron:0 30 3 ? * MON}")
    public void runHealthScoreUpdate() {
        log.info("[HealthScoreJob] Starting weekly health score update");
        long start = System.currentTimeMillis();

        List<Distributor> distributors = distributorRepository.findByActiveTrue();
        int totalScored = 0;

        for (Distributor distributor : distributors) {
            try {
                int count = healthScoreService.computeAll(distributor.getId());
                totalScored += count;
                meterRegistry.counter("ai.crm.health_score.customers",
                        "distributor", distributor.getId().toString()).increment(count);
            } catch (Exception e) {
                log.error("[HealthScoreJob] Failed for distributor={}: {}",
                        distributor.getId(), e.getMessage(), e);
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("[HealthScoreJob] Scored {} customers across {} distributors in {}ms",
                totalScored, distributors.size(), duration);
        meterRegistry.timer("ai.crm.health_score.duration").record(duration, TimeUnit.MILLISECONDS);
    }
}
