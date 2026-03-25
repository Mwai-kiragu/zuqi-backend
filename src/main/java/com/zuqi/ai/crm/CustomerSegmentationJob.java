package com.zuqi.ai.crm;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.DistributorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled job that runs customer segmentation for all active distributors
 * every Monday at 03:00 AM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerSegmentationJob {

    private final DistributorRepository distributorRepository;
    private final CustomerSegmentationService segmentationService;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${zuqi.ai.crm.segmentation-cron:0 0 3 ? * MON}")
    public void runSegmentation() {
        log.info("[SegmentationJob] Starting weekly segmentation run");
        long start = System.currentTimeMillis();

        List<Distributor> distributors = distributorRepository.findByActiveTrue();
        int totalSegmented = 0;

        for (Distributor distributor : distributors) {
            try {
                int count = segmentationService.segmentAll(distributor.getId());
                totalSegmented += count;
                meterRegistry.counter("ai.crm.segmentation.customers",
                        "distributor", distributor.getId().toString()).increment(count);
            } catch (Exception e) {
                log.error("[SegmentationJob] Failed for distributor={}: {}",
                        distributor.getId(), e.getMessage(), e);
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("[SegmentationJob] Segmented {} customers across {} distributors in {}ms",
                totalSegmented, distributors.size(), duration);
        meterRegistry.timer("ai.crm.segmentation.duration").record(duration,
                java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
