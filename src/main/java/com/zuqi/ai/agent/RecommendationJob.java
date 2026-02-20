package com.zuqi.ai.agent;

import com.zuqi.domain.ai.Recommendation;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.DistributorRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scheduled weekly job that runs the AI recommendation agent across all distributors.
 *
 * Activated only when {@code zuqi.ai.agent.enabled=true} is set in configuration,
 * which prevents the LLM agent from running in environments without Ollama.
 *
 * Schedule: Every Monday at 08:00 (EAT) by default.
 * Override via: {@code zuqi.ai.agent.recommendation-cron}
 *
 * Metrics published:
 *   - zuqi_ai_recommendation_runs   (total successful runs per distributor)
 *   - zuqi_ai_recommendation_failures (total per-distributor failures)
 *
 * Blueprint reference: implementation_plan.md Phase 6 Task 6.2
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "zuqi.ai.agent", name = "enabled", havingValue = "true", matchIfMissing = false)
public class RecommendationJob {

    private final RecommendationService recommendationService;
    private final DistributorRepository distributorRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Weekly recommendation generation for every active distributor.
     *
     * Each distributor is processed independently so that a single LLM failure
     * does not abort the entire batch.
     */
    @Scheduled(cron = "${zuqi.ai.agent.recommendation-cron:0 0 8 ? * MON}")
    public void runWeeklyRecommendations() {
        log.info("=".repeat(80));
        log.info("=== Starting weekly AI recommendation job ===");
        log.info("=".repeat(80));

        List<Distributor> distributors = distributorRepository.findAll();
        log.info("Processing {} distributors", distributors.size());

        int totalGenerated = 0;
        int totalFailures  = 0;

        for (Distributor distributor : distributors) {
            try {
                List<Recommendation> recommendations =
                        recommendationService.generateAndSave(distributor.getId());

                int count = recommendations.size();
                totalGenerated += count;

                log.info("Generated {} recommendations for distributor {} ({})",
                        count, distributor.getName(), distributor.getId());

                // Increment run counter for this distributor
                Counter.builder("zuqi_ai_recommendation_runs")
                        .description("Total number of successful recommendation runs per distributor")
                        .tag("distributor_id", distributor.getId().toString())
                        .register(meterRegistry)
                        .increment();

            } catch (Exception e) {
                totalFailures++;
                log.error("Failed to generate recommendations for distributor {} ({}): {}",
                        distributor.getName(), distributor.getId(), e.getMessage(), e);

                // Increment failure counter for this distributor
                Counter.builder("zuqi_ai_recommendation_failures")
                        .description("Total number of recommendation generation failures per distributor")
                        .tag("distributor_id", distributor.getId().toString())
                        .register(meterRegistry)
                        .increment();
            }
        }

        log.info("=".repeat(80));
        log.info("=== Weekly recommendation job complete: {} recommendations generated, {} failures ===",
                totalGenerated, totalFailures);
        log.info("=".repeat(80));
    }
}
