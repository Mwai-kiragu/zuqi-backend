package com.zuqi.ai.synthetic.generators;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Generates synthetic expiry batch data for training ExpiryRiskPredictor.
 *
 * Distribution (from plan):
 * - 60% sell out before expiry (sell_through_probability = 0.85–1.0)
 * - 25% partial sell-through  (sell_through_probability = 0.40–0.84)
 * - 15% expire unsold         (sell_through_probability = 0.00–0.39)
 */
@Component
@Slf4j
public class SyntheticExpiryBatchGenerator {

    private static final int DEFAULT_BATCH_COUNT = 500;

    public List<SyntheticExpiryBatch> generateBatches(int count) {
        Random rng = new Random(42L);
        List<SyntheticExpiryBatch> batches = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            double roll = rng.nextDouble();
            double sellThrough;
            String outcome;

            if (roll < 0.60) {
                // Sells out: high probability
                sellThrough = 0.85 + rng.nextDouble() * 0.15;
                outcome = "SOLD_OUT";
            } else if (roll < 0.85) {
                // Partial: moderate probability
                sellThrough = 0.40 + rng.nextDouble() * 0.44;
                outcome = "PARTIAL";
            } else {
                // Expires unsold: low probability
                sellThrough = rng.nextDouble() * 0.39;
                outcome = "EXPIRED";
            }

            // Days to expiry: 1–90 days
            int daysToExpiry = 1 + rng.nextInt(90);
            LocalDate expiryDate = LocalDate.now().plusDays(daysToExpiry);

            // Batch age ratio: how much shelf life consumed
            double batchAgeRatio = 0.1 + rng.nextDouble() * 0.85;

            // Stock qty: 10–500 units
            double stockQty = 10 + rng.nextInt(490);

            // Daily sales rate: correlated with sell-through
            double avgDailyRate = sellThrough > 0.7
                    ? stockQty / (daysToExpiry * 0.8)  // will sell fast
                    : stockQty / (daysToExpiry * 2.5); // will sell slowly

            double projectedDays = avgDailyRate > 0 ? stockQty / avgDailyRate : 999.0;

            batches.add(new SyntheticExpiryBatch(
                    UUID.randomUUID(),
                    "BATCH-" + (1000 + i),
                    expiryDate,
                    daysToExpiry,
                    stockQty,
                    avgDailyRate,
                    projectedDays,
                    avgDailyRate * 0.9,
                    12.0 + rng.nextDouble() * 6,
                    0.1 + rng.nextDouble() * 0.8,
                    batchAgeRatio,
                    sellThrough,
                    outcome
            ));
        }

        log.info("Generated {} synthetic expiry batches", batches.size());
        return batches;
    }

    public List<SyntheticExpiryBatch> generateBatches() {
        return generateBatches(DEFAULT_BATCH_COUNT);
    }

    public record SyntheticExpiryBatch(
            UUID batchId,
            String batchNumber,
            LocalDate expiryDate,
            int daysToExpiry,
            double currentStockQty,
            double avgDailySalesRate,
            double projectedDaysToSell,
            double similarSkuVelocity,
            double warehouseTurnoverRate,
            double priceSensitivityScore,
            double batchAgeRatio,
            double sellThroughProbability,
            String outcome
    ) {}
}
