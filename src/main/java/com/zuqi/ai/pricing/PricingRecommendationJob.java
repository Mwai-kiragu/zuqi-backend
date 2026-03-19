package com.zuqi.ai.pricing;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Weekly batch job: generates smart pricing recommendations for all
 * (distributor, product) pairs.
 * Schedule: Monday at 03:00.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PricingRecommendationJob {

    private final DistributorRepository distributorRepository;
    private final ProductRepository productRepository;
    private final SmartPricingRecommender recommender;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${zuqi.ai.pricing.recommendation-cron:0 0 3 * * MON}")
    public void runPricingRecommendations() {
        log.info("=== Starting weekly pricing recommendation job ===");
        long start = System.currentTimeMillis();
        int totalGenerated = 0;
        int totalErrors    = 0;

        List<Distributor> distributors = distributorRepository.findAll();
        List<Product> products = productRepository.findAll();

        for (Distributor distributor : distributors) {
            for (Product product : products) {
                try {
                    var rec = recommender.recommend(product.getId(), distributor.getId());
                    if (rec != null) totalGenerated++;
                } catch (Exception e) {
                    log.warn("[PricingJob] Failed product={} distributor={}: {}",
                            product.getId(), distributor.getId(), e.getMessage());
                    totalErrors++;
                }
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== Pricing recommendations complete: {} generated, {} errors, {}ms ===",
                totalGenerated, totalErrors, duration);

        Counter.builder("zuqi_ai_pricing_recommendations_generated")
                .tag("job", "pricing_recommendation")
                .register(meterRegistry)
                .increment(totalGenerated);
    }
}
