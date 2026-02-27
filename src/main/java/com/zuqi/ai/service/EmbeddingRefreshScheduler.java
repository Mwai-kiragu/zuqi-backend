package com.zuqi.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly scheduler that keeps merchant embeddings current.
 *
 * Refreshes embeddings older than the configured threshold so that
 * credit scoring RAG peer-comparison always uses up-to-date merchant profiles.
 *
 * Default schedule: 02:00 AM every day (configurable via
 * {@code zuqi.ai.embeddings.refresh-cron}).
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.3
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmbeddingRefreshScheduler {

    private final MerchantEmbeddingService merchantEmbeddingService;

    @Value("${zuqi.ai.embeddings.stale-days:7}")
    private int staleDaysThreshold;

    /**
     * Refresh merchant embeddings that are older than {@code staleDaysThreshold} days.
     *
     * Runs nightly at 02:00 AM by default. Any merchant whose embedding was last
     * generated more than {@code staleDaysThreshold} days ago will be re-embedded
     * so that order/payment behavior changes are reflected in similarity searches.
     */
    @Scheduled(cron = "${zuqi.ai.embeddings.refresh-cron:0 0 2 * * ?}")
    public void refreshStaleEmbeddings() {
        log.info("Starting nightly embedding refresh (stale threshold: {} days)", staleDaysThreshold);
        try {
            int refreshed = merchantEmbeddingService.refreshStaleEmbeddings(staleDaysThreshold);
            log.info("Nightly embedding refresh complete: {} embeddings updated", refreshed);
        } catch (Exception e) {
            log.error("Nightly embedding refresh failed: {}", e.getMessage(), e);
        }
    }
}
