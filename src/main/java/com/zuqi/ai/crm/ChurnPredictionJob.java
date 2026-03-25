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
 * Scheduled job that refreshes churn predictions for all active customers
 * every Monday at 04:00 AM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChurnPredictionJob {

    private final DistributorRepository distributorRepository;
    private final CustomerRepository customerRepository;
    private final ChurnPredictor churnPredictor;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${zuqi.ai.crm.churn-cron:0 0 4 ? * MON}")
    public void runChurnPrediction() {
        log.info("[ChurnJob] Starting weekly churn prediction run");
        long start = System.currentTimeMillis();

        List<Distributor> distributors = distributorRepository.findByActiveTrue();
        int totalPredicted = 0;

        for (Distributor distributor : distributors) {
            try {
                List<Customer> customers =
                        customerRepository.findByDistributorIdAndActiveTrue(distributor.getId());
                for (Customer customer : customers) {
                    try {
                        churnPredictor.predict(customer.getId(), distributor.getId());
                        totalPredicted++;
                    } catch (Exception e) {
                        log.warn("[ChurnJob] Failed for customer={}: {}", customer.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("[ChurnJob] Failed for distributor={}: {}",
                        distributor.getId(), e.getMessage(), e);
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("[ChurnJob] Predicted churn for {} customers in {}ms", totalPredicted, duration);
        meterRegistry.timer("ai.crm.churn.duration").record(duration, TimeUnit.MILLISECONDS);
    }
}
