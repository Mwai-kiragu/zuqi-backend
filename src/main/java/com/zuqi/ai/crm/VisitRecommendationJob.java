package com.zuqi.ai.crm;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.user.User;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job that regenerates visit recommendations for all active sales reps
 * every Monday at 05:30 AM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VisitRecommendationJob {

    private final DistributorRepository distributorRepository;
    private final UserRepository userRepository;
    private final VisitFrequencyOptimizer visitOptimizer;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${zuqi.ai.crm.visit-rec-cron:0 30 5 ? * MON}")
    public void runVisitRecommendations() {
        log.info("[VisitRecJob] Starting weekly visit recommendation run");
        long start = System.currentTimeMillis();

        List<Distributor> distributors = distributorRepository.findByActiveTrue();
        int totalSaved = 0;

        for (Distributor distributor : distributors) {
            try {
                // Find all active users for this distributor (sales reps included)
                List<User> salesReps = userRepository.findByDistributorIdAndActiveTrue(
                        distributor.getId());
                for (User rep : salesReps) {
                    try {
                        int count = visitOptimizer
                                .generateVisitPlan(rep.getId(), distributor.getId())
                                .size();
                        totalSaved += count;
                    } catch (Exception e) {
                        log.warn("[VisitRecJob] Failed for rep={}: {}", rep.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("[VisitRecJob] Failed for distributor={}: {}",
                        distributor.getId(), e.getMessage(), e);
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("[VisitRecJob] Saved {} visit recommendations in {}ms", totalSaved, duration);
        meterRegistry.timer("ai.crm.visit_rec.duration").record(duration, TimeUnit.MILLISECONDS);
    }
}
