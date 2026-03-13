package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.ai.feature.InventoryFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SyntheticInventoryFeatureBuilderTest {

    private SyntheticInventoryFeatureBuilder builder;

    private static final LocalDateTime AS_OF  = LocalDateTime.of(2024, 6, 1, 0, 0);
    private static final UUID WAREHOUSE = UUID.randomUUID();
    private static final UUID SKU       = UUID.randomUUID();
    private static final UUID USER      = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        builder = new SyntheticInventoryFeatureBuilder();
    }

    // ── No movements ───────────────────────────────────────────────────────

    @Test
    void noMovements_zeroStockLevels() {
        SyntheticDataBundle bundle = bundle(List.of());

        InventoryFeatures f = builder.computeFeatures(WAREHOUSE, SKU, bundle, AS_OF);

        assertThat(f.currentStock()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(f.expectedStock()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(f.discrepancy()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(f.discrepancyPct()).isEqualTo(0.0);
        assertThat(f.consumptionTrend()).isEqualTo("STABLE");
    }

    // ── Normal movements (no shrinkage) ────────────────────────────────────

    @Test
    void normalMovements_expectedStockReconstructed() {
        // IN 100 → OUT 30 → OUT 20 → expected = 50
        List<SyntheticInventoryMovement> movements = List.of(
                movement("IN",  100, false, AS_OF.minusDays(30)),
                movement("OUT",  30, false, AS_OF.minusDays(15)),
                movement("OUT",  20, false, AS_OF.minusDays(5))
        );

        SyntheticDataBundle bundle = bundle(movements);
        InventoryFeatures f = builder.computeFeatures(WAREHOUSE, SKU, bundle, AS_OF);

        assertThat(f.expectedStock()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(f.currentStock()).isEqualByComparingTo(BigDecimal.valueOf(50));  // no shrinkage
        assertThat(f.discrepancy()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void normalMovements_discrepancyPercentZero_whenNoShrinkage() {
        List<SyntheticInventoryMovement> movements = List.of(
                movement("IN", 200, false, AS_OF.minusDays(20)),
                movement("OUT", 50, false, AS_OF.minusDays(10))
        );

        InventoryFeatures f = builder.computeFeatures(WAREHOUSE, SKU, bundle(movements), AS_OF);

        assertThat(f.discrepancyPct()).isEqualTo(0.0);
    }

    // ── Shrinkage movements ────────────────────────────────────────────────

    @Test
    void shrinkageMovements_discrepancyNegative() {
        // IN 100, then shrinkage of 20 (unrecorded) → current = 80, expected = 100, discrepancy = -20
        List<SyntheticInventoryMovement> movements = List.of(
                movement("IN",  100, false, AS_OF.minusDays(10)),
                movement("OUT",  20, true,  AS_OF.minusDays(5))   // isShrinkage=true
        );

        InventoryFeatures f = builder.computeFeatures(WAREHOUSE, SKU, bundle(movements), AS_OF);

        // expectedStock = IN(100) - OUT(20) = 80 (shrinkage also counted as OUT)
        // currentStock = expectedStock - shrinkageTotal = 80 - 20 = 60
        // discrepancy = 60 - 80 = -20
        assertThat(f.discrepancy()).isEqualByComparingTo(BigDecimal.valueOf(-20));
        assertThat(f.discrepancyPct()).isLessThan(0.0);
    }

    // ── Consumption rate ───────────────────────────────────────────────────

    @Test
    void consumptionRate7d_onlyRecentOuts_counted() {
        // Two OUT movements: one 3 days ago (10 units), one 40 days ago (50 units)
        List<SyntheticInventoryMovement> movements = List.of(
                movement("IN",  200, false, AS_OF.minusDays(60)),
                movement("OUT",  50, false, AS_OF.minusDays(40)),   // outside 7d window
                movement("OUT",  10, false, AS_OF.minusDays(3))     // inside 7d window
        );

        InventoryFeatures f = builder.computeFeatures(WAREHOUSE, SKU, bundle(movements), AS_OF);

        // rate7d = 10 / 7 ≈ 1.429
        assertThat(f.consumptionRate7d().doubleValue()).isCloseTo(10.0 / 7.0, within(0.01));
        // rate30d includes the 3-day movement only (40-day is outside 30d window too)
        assertThat(f.consumptionRate30d().doubleValue()).isCloseTo(10.0 / 30.0, within(0.01));
    }

    @Test
    void consumptionTrend_higherRecentRate_returnsIncreasing() {
        // Recent 7d: high consumption; 30d: lower
        List<SyntheticInventoryMovement> movements = List.of(
                movement("IN",   500, false, AS_OF.minusDays(31)),
                movement("OUT",   10, false, AS_OF.minusDays(25)),  // 10 units in days 25-31
                movement("OUT",  200, false, AS_OF.minusDays(3))    // 200 units in last 3 days
        );

        InventoryFeatures f = builder.computeFeatures(WAREHOUSE, SKU, bundle(movements), AS_OF);

        // rate7d = 200/7 ≈ 28.6; rate30d = (10+200)/30 = 7.0
        // diff = (28.6 - 7.0) / 7.0 >> 0.20 → INCREASING
        assertThat(f.consumptionTrend()).isEqualTo("INCREASING");
    }

    // ── Manual adjustments ─────────────────────────────────────────────────

    @Test
    void manualAdjustmentCount7d_onlyRecentAdjustments_counted() {
        List<SyntheticInventoryMovement> movements = List.of(
                movement("IN",        100, false, AS_OF.minusDays(20)),
                adjustment(AS_OF.minusDays(10)),   // outside 7d
                adjustment(AS_OF.minusDays(2)),    // inside 7d
                adjustment(AS_OF.minusDays(1))     // inside 7d
        );

        InventoryFeatures f = builder.computeFeatures(WAREHOUSE, SKU, bundle(movements), AS_OF);

        assertThat(f.manualAdjustmentCount7d()).isEqualTo(2);
    }

    @Test
    void adjustingUserIds_deduplicatedCorrectly() {
        UUID user2 = UUID.randomUUID();
        List<SyntheticInventoryMovement> movements = List.of(
                adjustmentByUser(AS_OF.minusDays(2), USER),
                adjustmentByUser(AS_OF.minusDays(1), USER),    // same user twice
                adjustmentByUser(AS_OF.minusDays(3), user2)    // outside 7d → not included
        );

        InventoryFeatures f = builder.computeFeatures(WAREHOUSE, SKU, bundle(movements), AS_OF);

        // Only USER is within 7d, user2 is on day -3 so inside, but day -3 < 7
        // Actually both USER (days -2, -1) and user2 (day -3) are within 7 days
        assertThat(f.adjustingUserIds()).contains(USER, user2);
        assertThat(f.adjustingUserIds()).hasSize(2);   // deduplicated
    }

    // ── Metadata ───────────────────────────────────────────────────────────

    @Test
    void computeFeatures_warehouseAndSkuPreserved() {
        InventoryFeatures f = builder.computeFeatures(WAREHOUSE, SKU, bundle(List.of()), AS_OF);

        assertThat(f.warehouseId()).isEqualTo(WAREHOUSE);
        assertThat(f.productId()).isEqualTo(SKU);
        assertThat(f.computedAt()).isEqualTo(AS_OF);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private SyntheticInventoryMovement movement(String type, int qty,
                                                 boolean isShrinkage,
                                                 LocalDateTime timestamp) {
        BigDecimal q = BigDecimal.valueOf(qty);
        return new SyntheticInventoryMovement(
                UUID.randomUUID(), WAREHOUSE, SKU, type, q,
                BigDecimal.ZERO, q, timestamp, USER, isShrinkage,
                isShrinkage ? "GRADUAL" : null);
    }

    private SyntheticInventoryMovement adjustment(LocalDateTime timestamp) {
        return adjustmentByUser(timestamp, USER);
    }

    private SyntheticInventoryMovement adjustmentByUser(LocalDateTime timestamp, UUID userId) {
        return new SyntheticInventoryMovement(
                UUID.randomUUID(), WAREHOUSE, SKU, "ADJUSTMENT", BigDecimal.valueOf(5),
                BigDecimal.valueOf(100), BigDecimal.valueOf(105),
                timestamp, userId, false, null);
    }

    private SyntheticDataBundle bundle(List<SyntheticInventoryMovement> movements) {
        return SyntheticDataBundle.create(
                List.of(), List.of(), List.of(), List.of(),
                movements, List.of(), List.of(),
                1L, SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L));
    }
}
