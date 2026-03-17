package com.zuqi.ai.feature;

import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.StockMovement;
import com.zuqi.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of InventoryFeatureService for shrinkage detection and stockout prediction.
 *
 * Computes features used by:
 * - Shrinkage detection (Isolation Forest anomaly detection)
 * - Stockout prediction (XGBoost classification)
 *
 * Tracks inventory discrepancies, manual adjustment patterns, and consumption trends.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryFeatureServiceImpl implements InventoryFeatureService {

    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final DemandForecastRepository demandForecastRepository;

    @Override
    @Cacheable(value = "inventoryFeatures", key = "#warehouseId + ':' + #productId")
    public InventoryFeatures computeFeatures(UUID warehouseId, UUID productId) {
        return computeFeatures(warehouseId, productId, LocalDateTime.now());
    }

    @Override
    public InventoryFeatures computeFeatures(UUID warehouseId, UUID productId, LocalDateTime asOfDate) {
        // Validate warehouse and product exist
        var warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + warehouseId));

        productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        // Get current stock record (or empty if none exists)
        Optional<Stock> stockOpt = stockRepository.findByWarehouseIdAndProductId(warehouseId, productId);
        BigDecimal currentStock = stockOpt.map(Stock::getQuantity).orElse(BigDecimal.ZERO);
        BigDecimal reservedQty = stockOpt.map(Stock::getReservedQuantity).orElse(BigDecimal.ZERO);

        // Get all stock movements up to asOfDate
        List<StockMovement> movements = getStockMovements(warehouseId, productId, asOfDate);

        // Compute expected stock based on movements
        BigDecimal expectedStock = computeExpectedStock(movements);

        // Compute discrepancy
        BigDecimal discrepancy = currentStock.subtract(expectedStock);
        Double discrepancyPct = expectedStock.compareTo(BigDecimal.ZERO) == 0 ? 0.0 :
                discrepancy.divide(expectedStock, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();

        // Get manual adjustments in last 7 days
        List<StockMovement> adjustments7d = getManualAdjustments(movements, asOfDate, 7);

        // Pull demand forecast for next 7 days from ai_demand_forecasts (null-safe — no forecast = null)
        BigDecimal predictedDemand7d = null;
        try {
            UUID distributorId = warehouse.getDistributor().getId();
            LocalDate fromDate = asOfDate.toLocalDate();
            LocalDate toDate   = fromDate.plusDays(6);
            double forecastSum = demandForecastRepository.sumPredictedQtyForProduct(
                    productId, distributorId, fromDate, toDate);
            if (forecastSum > 0.0) {
                predictedDemand7d = BigDecimal.valueOf(forecastSum).setScale(3, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.debug("No demand forecast available for product={} warehouse={}: {}",
                    productId, warehouseId, e.getMessage());
        }

        return InventoryFeatures.builder()
                .warehouseId(warehouseId)
                .productId(productId)
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
                .consumptionRate7d(computeConsumptionRate(movements, asOfDate, 7))
                .consumptionRate30d(computeConsumptionRate(movements, asOfDate, 30))
                .consumptionTrend(computeConsumptionTrend(movements, asOfDate))
                // Pending quantities
                .pendingReservedQty(reservedQty)
                .expectedIncomingQty(computeExpectedIncoming(movements, asOfDate))
                // Demand forecast
                .predictedDemand7d(predictedDemand7d)
                .build();
    }

    @Override
    @CacheEvict(value = "inventoryFeatures", key = "#warehouseId + ':' + #productId")
    public void evictCache(UUID warehouseId, UUID productId) {
        log.debug("Evicted inventory features cache for warehouse {} product {}", warehouseId, productId);
    }

    @Override
    @CacheEvict(value = "inventoryFeatures", allEntries = true)
    public void evictWarehouseCache(UUID warehouseId) {
        log.debug("Evicted all inventory features cache for warehouse {}", warehouseId);
    }

    // ==================== Helper Methods ====================

    private List<StockMovement> getStockMovements(UUID warehouseId, UUID productId, LocalDateTime asOfDate) {
        // Get all movements for this warehouse-product up to asOfDate
        // Using pagination to get all records (limit 10000 for safety)
        return stockMovementRepository
                .findByWarehouseIdAndProductIdOrderByCreatedAtDesc(warehouseId, productId, PageRequest.of(0, 10000))
                .stream()
                .filter(sm -> !sm.getCreatedAt().isAfter(asOfDate))
                .sorted(Comparator.comparing(StockMovement::getCreatedAt))
                .collect(Collectors.toList());
    }

    private BigDecimal computeExpectedStock(List<StockMovement> movements) {
        BigDecimal expected = BigDecimal.ZERO;

        for (StockMovement movement : movements) {
            switch (movement.getMovementType()) {
                case IN:
                    expected = expected.add(movement.getQuantity());
                    break;
                case OUT:
                    expected = expected.subtract(movement.getQuantity());
                    break;
                case ADJUSTMENT:
                    // Adjustment can be positive or negative
                    expected = expected.add(movement.getQuantity());
                    break;
                case TRANSFER:
                    // Transfer logic depends on direction - for simplicity, treat as adjustment
                    expected = expected.add(movement.getQuantity());
                    break;
            }
        }

        return expected;
    }

    private List<StockMovement> getManualAdjustments(List<StockMovement> movements, LocalDateTime asOfDate, int days) {
        LocalDateTime cutoffDate = asOfDate.minusDays(days);

        return movements.stream()
                .filter(sm -> sm.getMovementType() == StockMovement.MovementType.ADJUSTMENT)
                .filter(sm -> !sm.getCreatedAt().isBefore(cutoffDate))
                .collect(Collectors.toList());
    }

    private Map<String, Integer> computeTimeDistribution(List<StockMovement> adjustments) {
        Map<String, Integer> distribution = new HashMap<>();

        for (StockMovement adjustment : adjustments) {
            int hour = adjustment.getCreatedAt().getHour();
            String hourKey = String.format("%02d:00", hour);
            distribution.merge(hourKey, 1, Integer::sum);
        }

        return distribution;
    }

    private List<UUID> getAdjustingUserIds(List<StockMovement> adjustments) {
        return adjustments.stream()
                .map(StockMovement::getCreatedBy)
                .filter(Objects::nonNull)
                .map(user -> user.getId())
                .distinct()
                .collect(Collectors.toList());
    }

    private BigDecimal computeConsumptionRate(List<StockMovement> movements, LocalDateTime asOfDate, int days) {
        LocalDateTime cutoffDate = asOfDate.minusDays(days);

        // Calculate total OUT movements in the period
        BigDecimal totalOut = movements.stream()
                .filter(sm -> sm.getMovementType() == StockMovement.MovementType.OUT)
                .filter(sm -> !sm.getCreatedAt().isBefore(cutoffDate))
                .map(StockMovement::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Return average daily consumption
        if (days == 0) {
            return BigDecimal.ZERO;
        }

        return totalOut.divide(BigDecimal.valueOf(days), 3, RoundingMode.HALF_UP);
    }

    private String computeConsumptionTrend(List<StockMovement> movements, LocalDateTime asOfDate) {
        BigDecimal rate7d = computeConsumptionRate(movements, asOfDate, 7);
        BigDecimal rate30d = computeConsumptionRate(movements, asOfDate, 30);

        if (rate7d.compareTo(BigDecimal.ZERO) == 0 && rate30d.compareTo(BigDecimal.ZERO) == 0) {
            return "STABLE";
        }

        // Compare recent 7 days to longer 30 days
        BigDecimal threshold = BigDecimal.valueOf(0.20); // 20% threshold
        BigDecimal diff = rate30d.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                rate7d.subtract(rate30d).divide(rate30d, 4, RoundingMode.HALF_UP);

        if (diff.compareTo(threshold) > 0) {
            return "INCREASING";
        } else if (diff.compareTo(threshold.negate()) < 0) {
            return "DECREASING";
        } else {
            return "STABLE";
        }
    }

    private BigDecimal computeExpectedIncoming(List<StockMovement> movements, LocalDateTime asOfDate) {
        // Get pending IN movements (e.g., purchase orders not yet received)
        // For simplicity, sum all IN movements in the last 7 days that reference PURCHASE
        LocalDateTime cutoffDate = asOfDate.minusDays(7);

        return movements.stream()
                .filter(sm -> sm.getMovementType() == StockMovement.MovementType.IN)
                .filter(sm -> !sm.getCreatedAt().isBefore(cutoffDate))
                .filter(sm -> "PURCHASE".equals(sm.getReferenceType()))
                .map(StockMovement::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
