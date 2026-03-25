package com.zuqi.ai.cashflow;

import com.zuqi.repository.DistributorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that generates cash flow forecasts for all distributors.
 *
 * Runs daily at 4:00 AM and generates 7, 30, and 90-day forecasts.
 * The 90-day set supersedes 7 and 30 (all days are covered).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CashFlowForecastJob {

    private static final int HORIZON_DAYS = 90;

    private final CashFlowPredictor cashFlowPredictor;
    private final DistributorRepository distributorRepository;

    @Scheduled(cron = "0 0 4 * * *")
    public void run() {
        log.info("=== CashFlowForecastJob started ===");
        long start = System.currentTimeMillis();
        int total = 0;

        for (var distributor : distributorRepository.findAll()) {
            try {
                var forecasts = cashFlowPredictor.forecast(distributor.getId(), HORIZON_DAYS);
                total += forecasts.size();
                log.debug("Forecast {} days for distributor {}", forecasts.size(), distributor.getId());
            } catch (Exception e) {
                log.error("Cash flow forecast failed for distributor {}: {}",
                        distributor.getId(), e.getMessage());
            }
        }

        log.info("=== CashFlowForecastJob done — {} forecasts in {}ms ===",
                total, System.currentTimeMillis() - start);
    }
}
