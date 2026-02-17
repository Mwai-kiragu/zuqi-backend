package com.zuqi.ai.demand;

import com.zuqi.domain.ai.DemandForecast;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.DemandForecastRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.MerchantRepository;
import com.zuqi.repository.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Nightly batch job for generating demand forecasts.
 *
 * Runs at 3:00 AM EAT to generate forecasts for all active merchant-SKU combinations.
 * Forecasts are stored in ai_demand_forecasts table for consumption by:
 * - Order suggestion service (sales rep mobile app)
 * - Stockout prediction service
 * - Inventory planning dashboards
 *
 * Blueprint: implementation_plan.md Phase 3 Task 3.5
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemandForecastJob {

    private final DistributorRepository distributorRepository;
    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final DemandForecaster demandForecaster;
    private final DemandForecastRepository demandForecastRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Generate demand forecasts for all merchant-SKU combinations.
     *
     * Schedule: Daily at 3:00 AM EAT
     * Forecast horizon: Next 7 days
     */
    @Scheduled(cron = "${zuqi.ai.demand.forecast-schedule:0 0 3 * * *}")
    @Transactional
    public void generateForecasts() {
        log.info("=== Starting nightly demand forecast job ===");
        long startTime = System.currentTimeMillis();

        try {
            // Get all active distributors
            List<Distributor> distributors = distributorRepository.findAll();
            log.info("Processing {} distributors", distributors.size());

            int totalForecasts = 0;
            int totalErrors = 0;

            for (Distributor distributor : distributors) {
                try {
                    int distributorForecasts = generateForecastsForDistributor(distributor);
                    totalForecasts += distributorForecasts;
                    log.info("Generated {} forecasts for distributor {}", distributorForecasts, distributor.getId());

                } catch (Exception e) {
                    log.error("Failed to generate forecasts for distributor {}: {}",
                            distributor.getId(), e.getMessage(), e);
                    totalErrors++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("=== Demand forecast job completed: {} forecasts generated, {} errors, duration: {}ms ===",
                    totalForecasts, totalErrors, duration);

            // Record metrics
            recordMetrics(totalForecasts, totalErrors, duration);

        } catch (Exception e) {
            log.error("Demand forecast job failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Generate forecasts for all merchant-SKU combinations in a distributor.
     */
    private int generateForecastsForDistributor(Distributor distributor) {
        // Get all active merchants for this distributor
        List<Merchant> merchants = merchantRepository.findByDistributorIdAndActiveTrue(distributor.getId());
        log.debug("Found {} active merchants for distributor {}", merchants.size(), distributor.getId());

        // Get all active products for this distributor
        List<Product> products = productRepository.findByDistributorIdAndActiveTrue(distributor.getId());
        log.debug("Found {} active products for distributor {}", products.size(), distributor.getId());

        if (merchants.isEmpty() || products.isEmpty()) {
            log.info("Skipping distributor {} - no active merchants or products", distributor.getId());
            return 0;
        }

        // Generate all merchant-product pairs
        List<DemandForecaster.MerchantProductPair> pairs = new ArrayList<>();
        for (Merchant merchant : merchants) {
            for (Product product : products) {
                pairs.add(new DemandForecaster.MerchantProductPair(merchant.getId(), product.getId()));
            }
        }

        log.info("Forecasting demand for {} merchant-SKU pairs (distributor {})",
                pairs.size(), distributor.getId());

        // Batch forecast
        List<DemandForecaster.DemandForecast> forecasts = demandForecaster.batchForecast(pairs);

        // Store forecasts with 7-day horizon
        LocalDate today = LocalDate.now();
        int saved = 0;

        for (DemandForecaster.DemandForecast forecast : forecasts) {
            try {
                // Only save forecasts with predicted quantity > 0
                if (forecast.predictedQuantity().doubleValue() > 0) {
                    saveForecast(distributor, forecast, today);
                    saved++;
                }
            } catch (Exception e) {
                log.warn("Failed to save forecast for merchant {} SKU {}: {}",
                        forecast.merchantId(), forecast.productId(), e.getMessage());
            }
        }

        return saved;
    }

    /**
     * Save a single forecast to the database.
     */
    private void saveForecast(Distributor distributor,
                              DemandForecaster.DemandForecast forecast,
                              LocalDate forecastDate) {

        Merchant merchant = merchantRepository.findById(forecast.merchantId())
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + forecast.merchantId()));

        Product product = productRepository.findById(forecast.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + forecast.productId()));

        // Extract model version from model version string (e.g., "demand_forecaster-v1" -> 1)
        Integer modelVersion = extractModelVersion(forecast.modelVersion());

        // Set expiration to 30 days from creation (for cleanup)
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);

        // Create or update forecast
        DemandForecast entity = demandForecastRepository
                .findByMerchantIdAndSkuIdAndForecastDate(
                        forecast.merchantId(),
                        forecast.productId(),
                        forecastDate
                )
                .orElse(DemandForecast.builder()
                        .merchant(merchant)
                        .sku(product)
                        .distributor(distributor)
                        .forecastDate(forecastDate)
                        .build());

        entity.setPredictedQty(forecast.predictedQuantity().doubleValue());
        entity.setModelVersion(modelVersion);
        entity.setExpiresAt(expiresAt);

        // TODO: Add confidence intervals when model supports them
        // entity.setConfidenceLower(...);
        // entity.setConfidenceUpper(...);

        demandForecastRepository.save(entity);
    }

    /**
     * Extract integer version from model version string.
     */
    private Integer extractModelVersion(String modelVersion) {
        if (modelVersion == null) {
            return 1;
        }

        // Extract number from "demand_forecaster-v1" or "fallback-avg"
        if (modelVersion.contains("-v")) {
            try {
                String versionPart = modelVersion.substring(modelVersion.lastIndexOf("-v") + 2);
                return Integer.parseInt(versionPart);
            } catch (Exception e) {
                log.warn("Failed to parse model version: {}", modelVersion);
            }
        }

        return 1; // Default version
    }

    /**
     * Record Prometheus metrics for monitoring.
     */
    private void recordMetrics(int totalForecasts, int totalErrors, long durationMs) {
        Counter.builder("zuqi_ai_forecast_records_generated")
                .description("Total number of demand forecasts generated")
                .tag("job", "demand_forecast")
                .register(meterRegistry)
                .increment(totalForecasts);

        Counter.builder("zuqi_ai_forecast_errors")
                .description("Total number of forecast generation errors")
                .tag("job", "demand_forecast")
                .register(meterRegistry)
                .increment(totalErrors);

        meterRegistry.gauge("zuqi_ai_forecast_job_duration_ms", durationMs);
    }
}
