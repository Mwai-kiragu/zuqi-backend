package com.zuqi.ai.feature;

import com.zuqi.domain.inventory.ProductBatch;
import com.zuqi.repository.ProductBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Computes expiry risk features for XGBoost sell-through prediction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpiryFeatureServiceImpl {

    private final ProductBatchRepository productBatchRepository;

    @Cacheable(value = "expiryFeatures", key = "#batchId")
    public ExpiryFeatures computeFeatures(UUID distributorId, UUID warehouseId,
                                          UUID productId, UUID batchId) {
        ProductBatch batch = productBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        LocalDate today = LocalDate.now();
        LocalDate expiryDate = batch.getExpiryDate();
        int daysToExpiry = (int) ChronoUnit.DAYS.between(today, expiryDate);

        double currentQty = batch.getCurrentQuantity() != null ? batch.getCurrentQuantity() : 0.0;

        // Estimate avg daily rate: assume batch sells in 2× daysToExpiry under normal conditions
        double avgDailyRate = daysToExpiry > 0 ? currentQty / (daysToExpiry * 2.0) : 1.0;

        double projectedDaysToSell = avgDailyRate > 0 ? currentQty / avgDailyRate : 999.0;

        // Shelf life ratio: how much of shelf life has been consumed
        // Use manufacture date if available, fallback to 180-day default shelf life
        long totalShelfLifeDays = 180;
        if (batch.getManufactureDate() != null) {
            totalShelfLifeDays = ChronoUnit.DAYS.between(batch.getManufactureDate(), expiryDate);
        }
        double batchAgeRatio = totalShelfLifeDays > 0
                ? (double) (totalShelfLifeDays - daysToExpiry) / totalShelfLifeDays
                : 0.5;

        return new ExpiryFeatures(
                distributorId,
                warehouseId,
                productId,
                batch.getBatchNumber(),
                expiryDate,
                daysToExpiry,
                currentQty,
                avgDailyRate,
                projectedDaysToSell,
                avgDailyRate * 0.9,  // similar SKU velocity proxy
                12.0,                // default turnover rate (12× per year)
                0.3,                 // default price sensitivity
                Math.min(1.0, Math.max(0.0, batchAgeRatio))
        );
    }
}
