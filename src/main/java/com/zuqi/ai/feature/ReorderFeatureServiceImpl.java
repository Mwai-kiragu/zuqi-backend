package com.zuqi.ai.feature;

import com.zuqi.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Computes reorder features for EOQ and safety stock calculations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReorderFeatureServiceImpl {

    private final StockRepository stockRepository;

    private static final double DEFAULT_CARRYING_COST_RATE = 0.25;
    private static final double DEFAULT_ORDERING_COST_KES  = 500.0;
    private static final double DEFAULT_UNIT_COST_KES      = 100.0;

    @Cacheable(value = "reorderFeatures", key = "#warehouseId + ':' + #productId")
    public ReorderFeatures computeFeatures(UUID distributorId, UUID warehouseId, UUID productId) {
        log.debug("Computing reorder features for warehouse {} product {}", warehouseId, productId);

        // Current stock level
        double currentStock = stockRepository
                .findByWarehouseIdAndProductId(warehouseId, productId)
                .map(s -> s.getQuantity() != null ? s.getQuantity().doubleValue() : 0.0)
                .orElse(0.0);

        // Estimate avg daily demand from reorder level as a proxy
        // (7 days supply at reorder level → avg demand = reorderLevel / 7)
        double avgDailyDemand = stockRepository
                .findByWarehouseIdAndProductId(warehouseId, productId)
                .map(s -> {
                    BigDecimal rl = s.getReorderLevel();
                    return (rl != null && rl.doubleValue() > 0) ? rl.doubleValue() / 7.0 : 1.0;
                })
                .orElse(1.0);

        double leadTimeAvg    = 7.0;  // default 7-day lead time
        double leadTimeStddev = 2.0;
        double demandCv       = 0.2;  // default 20% coefficient of variation

        double daysOfSupply = avgDailyDemand > 0 ? currentStock / avgDailyDemand : 999.0;

        return new ReorderFeatures(
                distributorId,
                warehouseId,
                productId,
                avgDailyDemand,
                demandCv,
                leadTimeAvg,
                leadTimeStddev,
                DEFAULT_CARRYING_COST_RATE,
                DEFAULT_ORDERING_COST_KES,
                DEFAULT_UNIT_COST_KES * 0.3,
                currentStock,
                0.0,   // pending orders — PO items are JSONB, skip for now
                daysOfSupply,
                DEFAULT_UNIT_COST_KES
        );
    }
}
