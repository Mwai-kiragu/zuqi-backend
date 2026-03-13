package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.dto.SyntheticInventoryMovement;
import com.zuqi.ai.synthetic.dto.SyntheticOrder;
import com.zuqi.ai.synthetic.profiles.AnomalyPatterns;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates synthetic inventory movement records for a distributor's warehouse network.
 *
 * <p>Produces four movement types:
 * <ul>
 *   <li><b>OUT</b> — delivery fulfillment; one movement per 1–3 SKUs per delivered order.</li>
 *   <li><b>IN</b> — restocking purchase orders; triggered when any SKU falls below 20% of its
 *       initial stock level.</li>
 *   <li><b>ADJUSTMENT (normal)</b> — 2.5% of warehouse-SKU pairs per week; small quantity
 *       changes representing counting errors, minor damage, or corrections.</li>
 *   <li><b>ADJUSTMENT (shrinkage)</b> — labeled anomaly events injected via
 *       {@link AnomalyPatterns.ShrinkagePattern}. Each pattern covers a pre-assigned
 *       (warehouse, date range) window. All shrinkage movements carry
 *       {@code isShrinkage=true} and the pattern name.</li>
 * </ul>
 *
 * <p>Expiry events are also generated once per month for food/FMCG SKUs: 1–3% of
 * current food stock is written off as ADJUSTMENT (non-shrinkage, representing spoilage).
 *
 * <p>Stock-level consistency is maintained throughout: every movement records
 * {@code previousStock} from the live tracking map and updates it immediately, so
 * {@code |previousStock − newStock| == quantity} for every generated record.
 *
 * <p>The SKU pool is built with the same formula as {@link OrderHistoryGenerator},
 * so inventory movement SKU IDs match those in the order items.
 *
 * <p>Expected volume: ~200,000 movements for a 500-merchant / 12-month run.
 */
@Component
@Slf4j
public class InventoryMovementGenerator {

    private static final long   SEED_OFFSET          = 4_000_000L;
    private static final int    N_WAREHOUSES         = 3;
    private static final int    N_SKUS               = 50;
    private static final int    N_USERS_PER_WAREHOUSE = 5;
    private static final int    INITIAL_STOCK_BASE   = 500;   // units per SKU per warehouse
    private static final double RESTOCK_THRESHOLD    = 0.20;  // restock when stock < 20% of initial
    private static final int    RESTOCK_QTY_MIN      = 500;
    private static final int    RESTOCK_QTY_MAX      = 2_000;
    private static final double NORMAL_ADJUST_RATE   = 0.025; // per warehouse-SKU per week
    private static final double EXPIRY_RATE          = 0.02;  // 2% of food stock per month

    // -------------------------------------------------------------------------
    // Internal types
    // -------------------------------------------------------------------------

    private record SkuSpec(UUID id, boolean isFood, double roughUnitPrice,
                           double popularityWeight) {}

    private record ShrinkageWindow(UUID warehouseId,
                                   LocalDate startDate, LocalDate endDate,
                                   AnomalyPatterns.ShrinkagePattern pattern,
                                   UUID concentratedUserId) {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generate inventory movements for the entire history window implied by {@code config}.
     *
     * @param orders all synthetic orders (DELIVERED orders drive outbound movements)
     * @param config generation parameters — seed and historyMonths
     * @return unmodifiable list of inventory movement DTOs
     */
    public List<SyntheticInventoryMovement> generate(List<SyntheticOrder> orders,
                                                     SyntheticDataConfig config) {
        Random rng = new Random(config.randomSeed() + SEED_OFFSET);

        List<UUID>            warehouses     = buildWarehousePool(config.randomSeed());
        List<SkuSpec>         skus           = buildSkuPool(config.randomSeed());
        Map<UUID, List<UUID>> usersByWarehouse = buildUserPool(config.randomSeed(), warehouses);

        // Running stock state: warehouseId → skuId → current units
        Map<UUID, Map<UUID, Integer>> stock     = initializeStock(warehouses, skus, rng);
        Map<UUID, Map<UUID, Integer>> initStock = deepCopyStock(stock);

        LocalDate historyEnd   = LocalDate.now();
        LocalDate historyStart = historyEnd.minusMonths(config.historyMonths());

        List<ShrinkageWindow> shrinkageWindows = buildShrinkageWindows(
                warehouses, usersByWarehouse, historyStart, historyEnd, config.randomSeed());

        // Index delivered orders by the Monday of their week
        Map<LocalDate, List<SyntheticOrder>> ordersByWeek = orders.stream()
                .filter(o -> "DELIVERED".equals(o.status()))
                .collect(Collectors.groupingBy(
                        o -> o.orderDate().toLocalDate().with(DayOfWeek.MONDAY)));

        List<SyntheticInventoryMovement> movements = new ArrayList<>();

        LocalDate weekStart = historyStart.with(DayOfWeek.MONDAY);
        while (!weekStart.isAfter(historyEnd)) {
            LocalDate weekEnd = weekStart.plusDays(6);

            // 1. Outbound — one movement per SKU-slot in each delivered order
            generateOutbound(ordersByWeek.getOrDefault(weekStart, List.of()),
                    warehouses, skus, stock, usersByWarehouse, rng, weekStart, weekEnd, movements);

            for (UUID wh : warehouses) {
                List<UUID> users = usersByWarehouse.get(wh);

                // 2. Restock if needed
                generateRestock(wh, skus, stock, initStock, rng, weekStart, users, movements);

                // 3. Normal adjustments
                generateNormalAdjustments(wh, skus, stock, rng, weekStart, weekEnd, users, movements);

                // 4. Shrinkage
                for (ShrinkageWindow window : shrinkageWindows) {
                    if (!window.warehouseId().equals(wh)) continue;
                    if (!weekOverlapsWindow(weekStart, weekEnd, window)) continue;
                    generateShrinkage(window, skus, stock, rng, weekStart, weekEnd, users, movements);
                }

                // 5. Expiry (once per month — when week falls in first 7 days of month)
                if (weekStart.getDayOfMonth() <= 7) {
                    generateExpiry(wh, skus, stock, rng, weekStart, users, movements);
                }
            }

            weekStart = weekStart.plusWeeks(1);
        }

        log.info("Generated {} inventory movements across {} warehouses (seed={})",
                movements.size(), warehouses.size(), config.randomSeed());

        return Collections.unmodifiableList(movements);
    }

    // -------------------------------------------------------------------------
    // Movement generators
    // -------------------------------------------------------------------------

    private void generateOutbound(List<SyntheticOrder> weekOrders,
                                  List<UUID> warehouses, List<SkuSpec> skus,
                                  Map<UUID, Map<UUID, Integer>> stock,
                                  Map<UUID, List<UUID>> usersByWarehouse,
                                  Random rng, LocalDate weekStart, LocalDate weekEnd,
                                  List<SyntheticInventoryMovement> out) {
        for (SyntheticOrder order : weekOrders) {
            // Assign order to warehouse via stable hash
            UUID wh = warehouses.get(
                    (order.syntheticId().hashCode() & Integer.MAX_VALUE) % warehouses.size());

            int numSkus  = 1 + rng.nextInt(3);
            double valuePerSku = order.totalAmount().doubleValue() / numSkus;

            List<UUID> users = usersByWarehouse.getOrDefault(wh, List.of());
            if (users.isEmpty()) continue;

            for (int i = 0; i < numSkus; i++) {
                SkuSpec sku = selectSku(skus, rng);
                int qty = (int) Math.max(1, valuePerSku / sku.roughUnitPrice());
                qty = clampToStock(stock, wh, sku.id(), qty);
                if (qty == 0) continue;

                LocalDateTime ts = pickTimestamp(weekStart, weekEnd, rng, 8, 17);
                out.add(movement(rng, wh, sku.id(), "OUT", qty, stock, "OUT", ts,
                        pickUser(users, rng), false, null));
                deductStock(stock, wh, sku.id(), qty);
            }
        }
    }

    private void generateRestock(UUID wh, List<SkuSpec> skus,
                                 Map<UUID, Map<UUID, Integer>> stock,
                                 Map<UUID, Map<UUID, Integer>> initStock,
                                 Random rng, LocalDate weekStart,
                                 List<UUID> users,
                                 List<SyntheticInventoryMovement> out) {
        for (SkuSpec sku : skus) {
            int current = stockOf(stock, wh, sku.id());
            int initial = stockOf(initStock, wh, sku.id());
            if (current < (int) (initial * RESTOCK_THRESHOLD)) {
                int qty = RESTOCK_QTY_MIN + rng.nextInt(RESTOCK_QTY_MAX - RESTOCK_QTY_MIN);
                LocalDateTime ts = weekStart.atTime(6, rng.nextInt(60));
                out.add(movement(rng, wh, sku.id(), "IN", qty, stock, "IN", ts, pickUser(users, rng), false, null));
                addStock(stock, wh, sku.id(), qty);
            }
        }
    }

    private void generateNormalAdjustments(UUID wh, List<SkuSpec> skus,
                                           Map<UUID, Map<UUID, Integer>> stock,
                                           Random rng, LocalDate weekStart, LocalDate weekEnd,
                                           List<UUID> users,
                                           List<SyntheticInventoryMovement> out) {
        for (SkuSpec sku : skus) {
            if (rng.nextDouble() >= NORMAL_ADJUST_RATE) continue;
            int current = stockOf(stock, wh, sku.id());
            if (current == 0) continue;

            boolean isUp = rng.nextBoolean();
            int qty = 1 + rng.nextInt(5);

            LocalDateTime ts = pickTimestamp(weekStart, weekEnd, rng, 18, 20);

            if (isUp) {
                out.add(movement(rng, wh, sku.id(), "ADJUSTMENT", qty, stock, "IN", ts, pickUser(users, rng), false, null));
                addStock(stock, wh, sku.id(), qty);
            } else {
                qty = clampToStock(stock, wh, sku.id(), qty);
                if (qty == 0) continue;
                out.add(movement(rng, wh, sku.id(), "ADJUSTMENT", qty, stock, "OUT", ts, pickUser(users, rng), false, null));
                deductStock(stock, wh, sku.id(), qty);
            }
        }
    }

    private void generateShrinkage(ShrinkageWindow window, List<SkuSpec> skus,
                                   Map<UUID, Map<UUID, Integer>> stock,
                                   Random rng, LocalDate weekStart, LocalDate weekEnd,
                                   List<UUID> users,
                                   List<SyntheticInventoryMovement> out) {
        // Pick 1–3 SKUs to shrink during this window
        int numSkus = 1 + rng.nextInt(3);
        // Movements per week: scaled by burst duration
        int movementsThisWeek = Math.max(1,
                (int) Math.ceil(window.pattern().burstDays / 7.0));
        movementsThisWeek = Math.min(movementsThisWeek, 5);

        for (int i = 0; i < movementsThisWeek; i++) {
            SkuSpec sku = skus.get(rng.nextInt(numSkus < skus.size() ? numSkus : skus.size()));
            int current = stockOf(stock, window.warehouseId(), sku.id());
            if (current == 0) continue;

            int baseQty = 2 + rng.nextInt(5);
            int qty = (int) Math.round(baseQty * window.pattern().quantityFactor);
            qty = clampToStock(stock, window.warehouseId(), sku.id(), qty);
            if (qty == 0) continue;

            // Timestamp: late night for CONCENTRATED_TIME; random evening otherwise
            int hour = (window.pattern() == AnomalyPatterns.ShrinkagePattern.CONCENTRATED_TIME)
                    ? 22 : 20 + rng.nextInt(3);
            LocalDate day = pickDayInRange(weekStart,
                    weekEnd.isBefore(window.endDate()) ? weekEnd : window.endDate(), rng);
            LocalDateTime ts = day.atTime(hour, rng.nextInt(60));

            // User: pinned for CONCENTRATED_USER; random otherwise
            UUID userId = (window.concentratedUserId() != null)
                    ? window.concentratedUserId()
                    : pickUser(users, rng);

            out.add(shrinkageMovement(rng, window.warehouseId(), sku.id(), qty,
                    stock, ts, userId, window.pattern().name()));
            deductStock(stock, window.warehouseId(), sku.id(), qty);
        }
    }

    private void generateExpiry(UUID wh, List<SkuSpec> skus,
                                Map<UUID, Map<UUID, Integer>> stock,
                                Random rng, LocalDate weekStart, List<UUID> users,
                                List<SyntheticInventoryMovement> out) {
        for (SkuSpec sku : skus) {
            if (!sku.isFood()) continue;
            int current = stockOf(stock, wh, sku.id());
            if (current == 0) continue;

            int expiredQty = (int) Math.round(current * EXPIRY_RATE * (0.5 + rng.nextDouble()));
            expiredQty = Math.max(0, Math.min(expiredQty, current));
            if (expiredQty == 0) continue;

            LocalDateTime ts = weekStart.atTime(7, rng.nextInt(60));
            out.add(movement(rng, wh, sku.id(), "ADJUSTMENT", expiredQty, stock, "OUT",
                    ts, pickUser(users, rng), false, null));
            deductStock(stock, wh, sku.id(), expiredQty);
        }
    }

    // -------------------------------------------------------------------------
    // Movement record builders
    // -------------------------------------------------------------------------

    /**
     * Build a normal (non-shrinkage) movement record. {@code direction} is "IN" or "OUT"
     * and determines whether the stock is increased or decreased.
     */
    private SyntheticInventoryMovement movement(Random rng, UUID wh, UUID skuId,
                                                String movementType, int qty,
                                                Map<UUID, Map<UUID, Integer>> stock,
                                                String direction,
                                                LocalDateTime ts, UUID userId,
                                                boolean isShrinkage, String pattern) {
        int prev = stockOf(stock, wh, skuId);
        int next = "IN".equals(direction) ? prev + qty : Math.max(0, prev - qty);
        return new SyntheticInventoryMovement(
                new UUID(rng.nextLong(), rng.nextLong()),
                wh, skuId, movementType,
                BigDecimal.valueOf(qty),
                BigDecimal.valueOf(prev),
                BigDecimal.valueOf(next),
                ts, userId, isShrinkage, pattern);
    }

    /** Build a labeled shrinkage ADJUSTMENT movement (always decreases stock). */
    private SyntheticInventoryMovement shrinkageMovement(Random rng, UUID wh, UUID skuId,
                                                         int qty,
                                                         Map<UUID, Map<UUID, Integer>> stock,
                                                         LocalDateTime ts, UUID userId,
                                                         String patternName) {
        int prev = stockOf(stock, wh, skuId);
        int next = Math.max(0, prev - qty);
        return new SyntheticInventoryMovement(
                new UUID(rng.nextLong(), rng.nextLong()),
                wh, skuId, "ADJUSTMENT",
                BigDecimal.valueOf(qty),
                BigDecimal.valueOf(prev),
                BigDecimal.valueOf(next),
                ts, userId, true, patternName);
    }

    // -------------------------------------------------------------------------
    // Pool builders
    // -------------------------------------------------------------------------

    private List<UUID> buildWarehousePool(long seed) {
        List<UUID> pool = new ArrayList<>(N_WAREHOUSES);
        for (int i = 0; i < N_WAREHOUSES; i++) {
            pool.add(new UUID(seed + 6_000_000L + i, seed * 7L + i));
        }
        return pool;
    }

    /**
     * Build SKU pool using the same formula as {@link OrderHistoryGenerator#buildSkuPool},
     * so both generators reference the same synthetic SKU UUIDs.
     */
    private List<SkuSpec> buildSkuPool(long seed) {
        Random rng = new Random(seed + 2_000_000L);  // matches OrderHistoryGenerator offset
        List<SkuSpec> pool = new ArrayList<>(N_SKUS);
        for (int i = 0; i < N_SKUS; i++) {
            boolean isFood     = i < (int) (N_SKUS * 0.70);
            double  popularity = (i < 10) ? 5.0 : 1.0;
            double  roughPrice = isFood ? 500.0 : 1_500.0;
            pool.add(new SkuSpec(
                    new UUID(seed + i, seed * 31L + i),  // same UUID formula
                    isFood, roughPrice, popularity));
            // consume RNG to stay in sync (mirrors the exp(logPrice) call in OrderHistoryGenerator)
            rng.nextGaussian();
        }
        return pool;
    }

    private Map<UUID, List<UUID>> buildUserPool(long seed, List<UUID> warehouses) {
        Map<UUID, List<UUID>> map = new HashMap<>();
        for (int w = 0; w < warehouses.size(); w++) {
            List<UUID> users = new ArrayList<>(N_USERS_PER_WAREHOUSE);
            for (int u = 0; u < N_USERS_PER_WAREHOUSE; u++) {
                users.add(new UUID(seed + 7_000_000L + w * 100L + u, w * 100L + u));
            }
            map.put(warehouses.get(w), users);
        }
        return map;
    }

    private Map<UUID, Map<UUID, Integer>> initializeStock(List<UUID> warehouses,
                                                          List<SkuSpec> skus, Random rng) {
        Map<UUID, Map<UUID, Integer>> stock = new HashMap<>();
        for (UUID wh : warehouses) {
            Map<UUID, Integer> skuStock = new HashMap<>();
            for (SkuSpec sku : skus) {
                skuStock.put(sku.id(), INITIAL_STOCK_BASE + rng.nextInt(INITIAL_STOCK_BASE));
            }
            stock.put(wh, skuStock);
        }
        return stock;
    }

    private Map<UUID, Map<UUID, Integer>> deepCopyStock(Map<UUID, Map<UUID, Integer>> src) {
        Map<UUID, Map<UUID, Integer>> copy = new HashMap<>();
        src.forEach((wh, skuMap) -> copy.put(wh, new HashMap<>(skuMap)));
        return copy;
    }

    /**
     * Pre-assign shrinkage windows across the history.
     * Guarantees at least one window per ShrinkagePattern so the training set is always labelled.
     */
    private List<ShrinkageWindow> buildShrinkageWindows(List<UUID> warehouses,
                                                        Map<UUID, List<UUID>> usersByWh,
                                                        LocalDate historyStart,
                                                        LocalDate historyEnd,
                                                        long seed) {
        Random rng      = new Random(seed + 5_000_000L);
        long   totalDays = ChronoUnit.DAYS.between(historyStart, historyEnd);
        List<ShrinkageWindow> windows = new ArrayList<>();

        for (AnomalyPatterns.ShrinkagePattern pattern : AnomalyPatterns.ShrinkagePattern.values()) {
            // How many windows: injection rate × warehouses × months (min 1)
            long months = ChronoUnit.MONTHS.between(historyStart, historyEnd);
            int count = Math.max(1, (int) Math.round(
                    pattern.injectionRate * warehouses.size() * months));
            count = Math.min(count, 3);

            for (int i = 0; i < count; i++) {
                UUID wh = warehouses.get(rng.nextInt(warehouses.size()));
                long startOffset = totalDays > pattern.burstDays
                        ? rng.nextInt((int) (totalDays - pattern.burstDays))
                        : 0;
                LocalDate startDate = historyStart.plusDays(startOffset);
                LocalDate endDate   = startDate.plusDays(pattern.burstDays);
                if (endDate.isAfter(historyEnd)) endDate = historyEnd;

                UUID concentratedUser = null;
                if (pattern == AnomalyPatterns.ShrinkagePattern.CONCENTRATED_USER) {
                    List<UUID> users = usersByWh.get(wh);
                    concentratedUser = users.get(rng.nextInt(users.size()));
                }

                windows.add(new ShrinkageWindow(wh, startDate, endDate, pattern, concentratedUser));
            }
        }
        return windows;
    }

    // -------------------------------------------------------------------------
    // Stock helpers
    // -------------------------------------------------------------------------

    private int stockOf(Map<UUID, Map<UUID, Integer>> stock, UUID wh, UUID skuId) {
        return stock.getOrDefault(wh, Map.of()).getOrDefault(skuId, 0);
    }

    private void addStock(Map<UUID, Map<UUID, Integer>> stock, UUID wh, UUID skuId, int qty) {
        stock.computeIfAbsent(wh, k -> new HashMap<>())
             .merge(skuId, qty, Integer::sum);
    }

    private void deductStock(Map<UUID, Map<UUID, Integer>> stock, UUID wh, UUID skuId, int qty) {
        stock.computeIfAbsent(wh, k -> new HashMap<>())
             .merge(skuId, -qty, Integer::sum);
    }

    private int clampToStock(Map<UUID, Map<UUID, Integer>> stock, UUID wh, UUID skuId, int qty) {
        return Math.min(qty, stockOf(stock, wh, skuId));
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private SkuSpec selectSku(List<SkuSpec> pool, Random rng) {
        double total = pool.stream().mapToDouble(SkuSpec::popularityWeight).sum();
        double roll  = rng.nextDouble() * total;
        double cumul = 0;
        for (SkuSpec sku : pool) {
            cumul += sku.popularityWeight();
            if (roll < cumul) return sku;
        }
        return pool.get(pool.size() - 1);
    }

    private boolean weekOverlapsWindow(LocalDate weekStart, LocalDate weekEnd,
                                       ShrinkageWindow window) {
        return !weekStart.isAfter(window.endDate()) && !weekEnd.isBefore(window.startDate());
    }

    private LocalDate pickDayInRange(LocalDate start, LocalDate end, Random rng) {
        long days = ChronoUnit.DAYS.between(start, end);
        if (days <= 0) return start;
        return start.plusDays(rng.nextInt((int) days + 1));
    }

    private LocalDateTime pickTimestamp(LocalDate weekStart, LocalDate weekEnd,
                                        Random rng, int hourMin, int hourMax) {
        LocalDate day = pickDayInRange(weekStart, weekEnd, rng);
        return day.atTime(hourMin + rng.nextInt(Math.max(1, hourMax - hourMin)),
                rng.nextInt(60));
    }

    private UUID pickUser(List<UUID> users, Random rng) {
        return users.get(rng.nextInt(users.size()));
    }

}
