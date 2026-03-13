package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.dto.SyntheticMerchant;
import com.zuqi.ai.synthetic.dto.SyntheticOrder;
import com.zuqi.ai.synthetic.dto.SyntheticOrderItem;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import com.zuqi.ai.synthetic.profiles.SeasonalityPatterns;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Generates synthetic order history (orders + line items) for a list of merchants.
 *
 * <p>For each merchant the generator iterates week-by-week through the merchant's
 * active history window (from registration date or config start, whichever is later,
 * up to today). Each week a Gaussian-sampled order count is drawn from the merchant's
 * archetype distribution. Each order receives 1–4 line items whose quantities are
 * back-computed from a target order value that incorporates:
 * <ul>
 *   <li>Archetype mean order value (sampled with Gaussian noise)</li>
 *   <li>Kenya seasonality multiplier from {@link SeasonalityPatterns}</li>
 *   <li>Compounding monthly growth/decline from {@link MerchantArchetype#applyGrowth}</li>
 * </ul>
 *
 * <p>SKU pool: 50 synthetic SKUs (35 food, 15 non-food), prices drawn from a
 * log-normal distribution calibrated so the expected line-item value matches the
 * archetype's {@code orderValueMeanKes}. The top-10 SKUs carry 5× higher popularity
 * weight, creating a realistic long-tail distribution.
 *
 * <p>Expected volume: ~100–200 orders per merchant over 12 months, depending on
 * archetype frequency. For a 500-merchant distributor with 12-month history this
 * produces ~75,000–100,000 orders and ~250,000–350,000 line items.
 */
@Component
@Slf4j
public class OrderHistoryGenerator {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Total SKUs in the synthetic pool. 70% food, 30% non-food. */
    private static final int N_SKUS = 50;

    /** Number of synthetic sales reps. */
    private static final int N_REPS = 10;

    /** Maximum line items per order. */
    private static final int MAX_ITEMS_PER_ORDER = 4;

    /**
     * RNG seed offset applied on top of the config seed.
     * Keeps this generator's random stream independent from MerchantProfileGenerator.
     */
    private static final long SEED_OFFSET = 1_000_000L;

    // -------------------------------------------------------------------------
    // Internal types
    // -------------------------------------------------------------------------

    /** Synthetic SKU specification used internally during generation. */
    private record SkuSpec(UUID id, boolean isFood, double unitPrice, double popularityWeight) {}

    /**
     * Result of a single generation run: orders and their line items.
     * Both lists are unmodifiable.
     */
    public record OrderHistoryResult(
            List<SyntheticOrder>     orders,
            List<SyntheticOrderItem> items) {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generate order history for all merchants in {@code merchants}.
     *
     * @param merchants list of synthetic merchants (from MerchantProfileGenerator)
     * @param config    generation parameters — seed and historyMonths drive everything
     * @return unmodifiable lists of orders and line items
     */
    public OrderHistoryResult generate(List<SyntheticMerchant> merchants,
                                       SyntheticDataConfig config) {
        Random rng = new Random(config.randomSeed() + SEED_OFFSET);

        List<SkuSpec> skuPool = buildSkuPool(config.randomSeed());
        List<UUID>    repPool = buildRepPool(config.randomSeed());

        LocalDate historyEnd   = LocalDate.now();
        LocalDate historyStart = historyEnd.minusMonths(config.historyMonths());

        List<SyntheticOrder>     orders = new ArrayList<>();
        List<SyntheticOrderItem> items  = new ArrayList<>();

        for (SyntheticMerchant merchant : merchants) {
            // Stable, deterministic rep assignment per merchant
            int repIndex = (merchant.syntheticId().hashCode() & Integer.MAX_VALUE) % repPool.size();
            UUID repId   = repPool.get(repIndex);

            // History starts from registration (if newer than historyStart) or historyStart
            LocalDate windowStart = merchant.registrationDate().isAfter(historyStart)
                    ? merchant.registrationDate()
                    : historyStart;

            generateForMerchant(merchant, repId, windowStart, historyEnd,
                    historyStart, skuPool, rng, orders, items);
        }

        log.info("Generated {} orders ({} items) for {} merchants",
                orders.size(), items.size(), merchants.size());

        return new OrderHistoryResult(
                Collections.unmodifiableList(orders),
                Collections.unmodifiableList(items));
    }

    // -------------------------------------------------------------------------
    // Per-merchant generation
    // -------------------------------------------------------------------------

    private void generateForMerchant(
            SyntheticMerchant merchant,
            UUID repId,
            LocalDate windowStart,
            LocalDate windowEnd,
            LocalDate historyStart,
            List<SkuSpec> skuPool,
            Random rng,
            List<SyntheticOrder> ordersOut,
            List<SyntheticOrderItem> itemsOut) {

        MerchantArchetype archetype = merchant.merchantArchetype();
        LocalDate weekStart = windowStart;

        while (!weekStart.isAfter(windowEnd)) {
            long monthsElapsed = ChronoUnit.MONTHS.between(historyStart, weekStart);

            int orderCount = sampleWeeklyOrderCount(archetype, rng);

            for (int i = 0; i < orderCount; i++) {
                LocalDate     orderDay = pickBusinessDay(weekStart, windowEnd, rng);
                LocalDateTime orderTs  = orderDay.atTime(8 + rng.nextInt(10), rng.nextInt(60));

                double seasonMult   = SeasonalityPatterns.getMultiplier(orderDay, true);
                double growthFactor = Math.max(0.1,
                        archetype.applyGrowth(1.0, (int) monthsElapsed));
                double targetValue  = Math.max(500.0,
                        archetype.sampleOrderValueKes(rng) * seasonMult * growthFactor);

                int itemCount = 1 + rng.nextInt(MAX_ITEMS_PER_ORDER);

                UUID orderId = new UUID(
                        rng.nextLong(),  // deterministic from seeded RNG
                        rng.nextLong());

                List<SyntheticOrderItem> orderItems =
                        buildOrderItems(orderId, targetValue, itemCount, skuPool, rng);

                BigDecimal totalAmount = orderItems.stream()
                        .map(SyntheticOrderItem::lineTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                String status = deriveStatus(orderDay, rng);

                ordersOut.add(new SyntheticOrder(
                        orderId,
                        merchant.syntheticId(),
                        repId,
                        orderTs,
                        totalAmount,
                        status,
                        archetype));

                itemsOut.addAll(orderItems);
            }

            weekStart = weekStart.plusWeeks(1);
        }
    }

    // -------------------------------------------------------------------------
    // Item generation
    // -------------------------------------------------------------------------

    /**
     * Build 1–{@value MAX_ITEMS_PER_ORDER} line items for a single order.
     *
     * {@code targetValue} is distributed across items using random proportional
     * splits; quantities are back-computed as {@code round(item_share / unitPrice)}.
     * The actual {@code totalAmount} on the order is the exact sum of line totals.
     */
    private List<SyntheticOrderItem> buildOrderItems(UUID orderId,
                                                     double targetValue,
                                                     int itemCount,
                                                     List<SkuSpec> skuPool,
                                                     Random rng) {
        List<SkuSpec> selected = selectSkus(skuPool, itemCount, rng);

        // Random proportional splits summing to 1.0
        double[] weights = new double[itemCount];
        double   total   = 0;
        for (int i = 0; i < itemCount; i++) {
            weights[i] = 0.1 + rng.nextDouble();
            total += weights[i];
        }

        List<SyntheticOrderItem> result = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            SkuSpec    sku       = selected.get(i);
            double     itemValue = targetValue * weights[i] / total;
            int        qty       = (int) Math.max(1, Math.round(itemValue / sku.unitPrice()));
            BigDecimal quantity  = BigDecimal.valueOf(qty);
            BigDecimal unitPrice = BigDecimal.valueOf(sku.unitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice.multiply(quantity)
                    .setScale(2, RoundingMode.HALF_UP);
            result.add(new SyntheticOrderItem(orderId, sku.id(), quantity, unitPrice, lineTotal));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Sample weekly order count from the archetype's Gaussian distribution (clamped to ≥ 0).
     */
    private int sampleWeeklyOrderCount(MerchantArchetype archetype, Random rng) {
        double sampled = archetype.ordersPerWeekMean
                + rng.nextGaussian() * archetype.ordersPerWeekStdDev;
        return (int) Math.max(0, Math.round(sampled));
    }

    /**
     * Pick a business day within the week starting at {@code weekStart}, not exceeding
     * {@code ceiling}. Monday–Friday are preferred over weekends.
     */
    private LocalDate pickBusinessDay(LocalDate weekStart, LocalDate ceiling, Random rng) {
        // offset weights: Mon=2, Tue-Fri=3 each, Sat=1, Sun=0  (total=15)
        int[] weights = {2, 3, 3, 3, 3, 1, 0};
        int roll  = rng.nextInt(15);
        int cumul = 0;
        int offset = 0;
        for (int d = 0; d < weights.length; d++) {
            cumul += weights[d];
            if (roll < cumul) { offset = d; break; }
        }
        LocalDate candidate = weekStart.plusDays(offset);
        return candidate.isAfter(ceiling) ? ceiling : candidate;
    }

    /**
     * Derive order status based on how many days ago the order was placed.
     * Historical orders (> 3 days) are predominantly DELIVERED.
     */
    private String deriveStatus(LocalDate orderDay, Random rng) {
        long daysAgo = ChronoUnit.DAYS.between(orderDay, LocalDate.now());
        if (daysAgo > 3) return rng.nextDouble() < 0.95 ? "DELIVERED" : "CANCELLED";
        if (daysAgo > 1) return rng.nextDouble() < 0.70 ? "PROCESSING" : "DELIVERED";
        return rng.nextDouble() < 0.60 ? "CONFIRMED" : "PENDING";
    }

    /**
     * Select {@code count} SKUs from the pool by popularity-weighted sampling with replacement.
     */
    private List<SkuSpec> selectSkus(List<SkuSpec> pool, int count, Random rng) {
        double totalWeight = pool.stream().mapToDouble(SkuSpec::popularityWeight).sum();
        List<SkuSpec> selected = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double roll  = rng.nextDouble() * totalWeight;
            double cumul = 0;
            for (SkuSpec sku : pool) {
                cumul += sku.popularityWeight();
                if (roll < cumul) { selected.add(sku); break; }
            }
            // Safety: if floating-point drift misses all entries, pick last
            if (selected.size() < i + 1) selected.add(pool.get(pool.size() - 1));
        }
        return selected;
    }

    // -------------------------------------------------------------------------
    // Pool builders (deterministic from seed, independent RNGs)
    // -------------------------------------------------------------------------

    /**
     * Build a deterministic pool of {@value N_SKUS} synthetic SKUs.
     * <ul>
     *   <li>70% food/FMCG: unit prices log-normal centred ~500 KES</li>
     *   <li>30% non-food: unit prices log-normal centred ~1,500 KES</li>
     *   <li>Top 10 SKUs (by index) have 5× popularity weight (long-tail distribution)</li>
     * </ul>
     */
    private List<SkuSpec> buildSkuPool(long seed) {
        Random rng  = new Random(seed + 2_000_000L);
        List<SkuSpec> pool = new ArrayList<>(N_SKUS);
        for (int i = 0; i < N_SKUS; i++) {
            boolean isFood     = i < (int) (N_SKUS * 0.70);
            double  popularity = (i < 10) ? 5.0 : 1.0;

            // exp(6.2) ≈ 493 KES (food), exp(7.3) ≈ 1,480 KES (non-food)
            double logMean  = isFood ? 6.2 : 7.3;
            double logPrice = logMean + rng.nextGaussian() * 0.5;
            double unitPrice = Math.max(50.0, Math.exp(logPrice));

            pool.add(new SkuSpec(
                    new UUID(seed + i, seed * 31L + i),
                    isFood,
                    unitPrice,
                    popularity));
        }
        return pool;
    }

    /**
     * Build a deterministic pool of {@value N_REPS} synthetic sales rep UUIDs.
     * Merchants are assigned to reps via {@code merchantId.hashCode() % N_REPS}.
     */
    private List<UUID> buildRepPool(long seed) {
        List<UUID> pool = new ArrayList<>(N_REPS);
        for (int i = 0; i < N_REPS; i++) {
            pool.add(new UUID(seed + 3_000_000L + i, i));
        }
        return pool;
    }
}
