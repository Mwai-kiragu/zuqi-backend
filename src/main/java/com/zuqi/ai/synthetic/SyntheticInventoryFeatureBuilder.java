package com.zuqi.ai.synthetic;

import com.zuqi.ai.feature.FeatureComputationUtils;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.ai.feature.InventoryFeatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds {@link InventoryFeatures} for a warehouse-SKU pair from in-memory
 * synthetic inventory movement data.
 *
 * <p>Computation logic mirrors {@link com.zuqi.ai.feature.InventoryFeatureServiceImpl}
 * exactly — only the data source differs.
 *
 * <h3>Shrinkage modelling</h3>
 * Synthetic movements flagged with {@code isShrinkage=true} represent unrecorded losses.
 * The builder subtracts their quantities from the computed expected stock to produce a
 * realistic discrepancy signal for Isolation Forest training.
 */
@Component
@Slf4j
public class SyntheticInventoryFeatureBuilder {

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Compute inventory features for a warehouse-SKU pair.
     *
     * @param warehouseId the warehouse UUID
     * @param skuId       the product/SKU UUID
     * @param bundle      the full in-memory dataset
     * @param asOfDate    reference date ("now" for training)
     * @return fully populated {@link InventoryFeatures}
     */
    public InventoryFeatures computeFeatures(UUID warehouseId,
                                              UUID skuId,
                                              SyntheticDataBundle bundle,
                                              LocalDateTime asOfDate) {
        List<SyntheticInventoryMovement> movements = bundle.getInventoryMovements().stream()
                .filter(m -> m.warehouseId().equals(warehouseId) && m.skuId().equals(skuId))
                .filter(m -> !m.timestamp().isAfter(asOfDate))
                .sorted(Comparator.comparing(SyntheticInventoryMovement::timestamp))
                .collect(Collectors.toList());

        log.debug("[SyntheticInventoryFB] warehouse={} sku={} movements={}",
                warehouseId, skuId, movements.size());

        // Expected stock: reconstructed from all recorded movements
        BigDecimal expectedStock = computeExpectedStock(movements);

        // Current (actual) stock: reduced by unrecorded shrinkage losses
        BigDecimal shrinkageTotal = movements.stream()
                .filter(SyntheticInventoryMovement::isShrinkage)
                .map(SyntheticInventoryMovement::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentStock = expectedStock.subtract(shrinkageTotal);

        BigDecimal discrepancy = currentStock.subtract(expectedStock);
        Double discrepancyPct = expectedStock.compareTo(BigDecimal.ZERO) == 0 ? 0.0 :
                discrepancy.divide(expectedStock, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();

        // Manual adjustments in the last 7 days
        List<SyntheticInventoryMovement> adjustments7d = movements.stream()
                .filter(m -> "ADJUSTMENT".equals(m.movementType()))
                .filter(m -> m.timestamp().isAfter(asOfDate.minusDays(7)))
                .collect(Collectors.toList());

        // Consumption rates
        BigDecimal rate7d  = computeConsumptionRate(movements, asOfDate, 7);
        BigDecimal rate30d = computeConsumptionRate(movements, asOfDate, 30);

        return InventoryFeatures.builder()
                .warehouseId(warehouseId)
                .productId(skuId)
                .computedAt(asOfDate)
                // Stock level features
                .currentStock(currentStock)
                .expectedStock(expectedStock)
                .discrepancy(discrepancy)
                .discrepancyPct(discrepancyPct)
                // Manual adjustment features
                .manualAdjustmentCount7d(adjustments7d.size())
                .adjustmentTimeDistribution(computeTimeDistribution(adjustments7d))
                .adjustingUserIds(getAdjustingUserIds(adjustments7d))
                // Consumption rate features
                .consumptionRate7d(rate7d)
                .consumptionRate30d(rate30d)
                .consumptionTrend(FeatureComputationUtils.computeConsumptionTrend(rate7d, rate30d))
                // Pending quantities (simplified — no purchase order tracking in synthetic data)
                .pendingReservedQty(BigDecimal.ZERO)
                .expectedIncomingQty(computeExpectedIncoming(movements, asOfDate))
                // Simulate demand forecast as ±15% variation around 7-day consumption rate
                .predictedDemand7d(simulatePredictedDemand(rate7d))
                .build();
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    /**
     * Reconstruct expected stock by replaying all movements.
     * Logic mirrors {@code InventoryFeatureServiceImpl#computeExpectedStock}.
     */
    private BigDecimal computeExpectedStock(List<SyntheticInventoryMovement> movements) {
        BigDecimal expected = BigDecimal.ZERO;
        for (SyntheticInventoryMovement m : movements) {
            expected = switch (m.movementType()) {
                case "IN"         -> expected.add(m.quantity());
                case "OUT"        -> expected.subtract(m.quantity());
                case "ADJUSTMENT" -> expected.add(m.quantity());
                case "TRANSFER"   -> expected.add(m.quantity());
                default           -> expected;
            };
        }
        return expected;
    }

    /**
     * Average daily OUT quantity over the last {@code days} days.
     * Logic mirrors {@code InventoryFeatureServiceImpl#computeConsumptionRate}.
     */
    private BigDecimal computeConsumptionRate(List<SyntheticInventoryMovement> movements,
                                               LocalDateTime asOfDate,
                                               int days) {
        if (days == 0) return BigDecimal.ZERO;
        LocalDateTime cutoff = asOfDate.minusDays(days);
        BigDecimal totalOut = movements.stream()
                .filter(m -> "OUT".equals(m.movementType()))
                .filter(m -> m.timestamp().isAfter(cutoff))
                .map(SyntheticInventoryMovement::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalOut.divide(BigDecimal.valueOf(days), 3, RoundingMode.HALF_UP);
    }

    private Map<String, Integer> computeTimeDistribution(
            List<SyntheticInventoryMovement> adjustments) {
        Map<String, Integer> dist = new HashMap<>();
        for (SyntheticInventoryMovement m : adjustments) {
            String key = String.format("%02d:00", m.timestamp().getHour());
            dist.merge(key, 1, Integer::sum);
        }
        return dist;
    }

    private List<UUID> getAdjustingUserIds(List<SyntheticInventoryMovement> adjustments) {
        return adjustments.stream()
                .map(SyntheticInventoryMovement::userId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Simulate demand forecast as ±15% variation around historical consumption rate.
     * Returns null (no forecast) when consumption rate is zero.
     */
    private BigDecimal simulatePredictedDemand(BigDecimal consumptionRate7d) {
        if (consumptionRate7d == null || consumptionRate7d.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        // Add ±15% noise to mimic demand forecaster output
        double base = consumptionRate7d.doubleValue();
        double noise = 1.0 + (Math.random() * 0.30 - 0.15);
        return BigDecimal.valueOf(base * noise).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Expected incoming: sum of IN movements in the last 7 days.
     *
     * In the real service ({@code InventoryFeatureServiceImpl#computeExpectedIncoming}),
     * only movements with {@code referenceType = "PURCHASE"} are counted. In synthetic data,
     * all IN movements represent purchase receipts (the generator does not produce non-purchase
     * INs), so filtering all IN movements is equivalent. No additional filter is applied here.
     */
    private BigDecimal computeExpectedIncoming(List<SyntheticInventoryMovement> movements,
                                                LocalDateTime asOfDate) {
        return movements.stream()
                .filter(m -> "IN".equals(m.movementType()))
                .filter(m -> m.timestamp().isAfter(asOfDate.minusDays(7)))
                .map(SyntheticInventoryMovement::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
