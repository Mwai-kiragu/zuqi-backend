package com.zuqi.ai.crm;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.DistributorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job that refreshes CLV predictions for all active customers
 * on the 1st of each month at 02:00 AM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClvPredictionJob {

    private final DistributorRepository distributorRepository;
    private final CustomerRepository customerRepository;
    private final CustomerLifetimeValuePredictor clvPredictor;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${zuqi.ai.crm.clv-cron:0 0 2 1 * ?}")
    public void runClvPrediction() {
        log.info("[ClvJob] Starting monthly CLV prediction run");
        long start = System.currentTimeMillis();

        List<Distributor> distributors = distributorRepository.findByActiveTrue();
        int totalPredicted = 0;

        for (Distributor distributor : distributors) {
            try {
                List<Customer> customers =
                        customerRepository.findByDistributorIdAndActiveTrue(distributor.getId());
                for (Customer customer : customers) {
                    try {
                        clvPredictor.predict(customer.getId(), distributor.getId());
                        totalPredicted++;
                    } catch (Exception e) {
                        log.warn("[ClvJob] Failed for customer={}: {}", customer.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("[ClvJob] Failed for distributor={}: {}",
                        distributor.getId(), e.getMessage(), e);
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("[ClvJob] Predicted CLV for {} customers in {}ms", totalPredicted, duration);
        meterRegistry.timer("ai.crm.clv.duration").record(duration, TimeUnit.MILLISECONDS);
    }
}
