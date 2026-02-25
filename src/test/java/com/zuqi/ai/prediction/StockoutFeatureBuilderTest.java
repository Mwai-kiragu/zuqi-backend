package com.zuqi.ai.prediction;

import com.zuqi.ai.feature.InventoryFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tribuo.Feature;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.impl.ArrayExample;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for StockoutFeatureBuilder — no Spring context required.
 *
 * Verifies 12-feature vector construction, label assignment,
 * and daysOfStockRemaining edge cases.
 */
class StockoutFeatureBuilderTest {

    private StockoutFeatureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new StockoutFeatureBuilder();
    }

    // ── Feature count ─────────────────────────────────────────────────────

    @Test
    void buildExample_hasTwelveFeatures() {
        ArrayExample<Label> example = builder.buildExample(typicalInventory());
        assertThat(example.size()).isEqualTo(12);
    }

    @Test
    void getFeatureCount_returns12() {
        assertThat(builder.getFeatureCount()).isEqualTo(12);
    }

    // ── Label assignment ──────────────────────────────────────────────────

    @Test
    void buildExample_labelledNoStockout() {
        ArrayExample<Label> example = builder.buildExample(typicalInventory());
        assertThat(example.getOutput().getLabel()).isEqualTo(StockoutFeatureBuilder.LABEL_NO_STOCKOUT);
    }

    @Test
    void buildLabelledExample_stockoutLabel() {
        ArrayExample<Label> example = builder.buildLabelledExample(
                typicalInventory(), StockoutFeatureBuilder.LABEL_STOCKOUT);
        assertThat(example.getOutput().getLabel()).isEqualTo(StockoutFeatureBuilder.LABEL_STOCKOUT);
    }

    @Test
    void buildLabelledExample_noStockoutLabel() {
        ArrayExample<Label> example = builder.buildLabelledExample(
                typicalInventory(), StockoutFeatureBuilder.LABEL_NO_STOCKOUT);
        assertThat(example.getOutput().getLabel()).isEqualTo(StockoutFeatureBuilder.LABEL_NO_STOCKOUT);
    }

    // ── Feature names ─────────────────────────────────────────────────────

    @Test
    void buildExample_containsAllTwelveFeatureNames() {
        ArrayExample<Label> example = builder.buildExample(typicalInventory());
        List<String> names = featureNames(example);

        assertThat(names).containsExactlyInAnyOrder(
                StockoutFeatureBuilder.FEAT_CURRENT_STOCK,
                StockoutFeatureBuilder.FEAT_CONSUMPTION_RATE_7D,
                StockoutFeatureBuilder.FEAT_CONSUMPTION_RATE_30D,
                StockoutFeatureBuilder.FEAT_CONSUMPTION_TREND,
                StockoutFeatureBuilder.FEAT_PENDING_RESERVED_QTY,
                StockoutFeatureBuilder.FEAT_EXPECTED_INCOMING_QTY,
                StockoutFeatureBuilder.FEAT_DAYS_OF_STOCK_REMAINING,
                StockoutFeatureBuilder.FEAT_DISCREPANCY_PCT,
                StockoutFeatureBuilder.FEAT_MANUAL_ADJ_COUNT_7D,
                StockoutFeatureBuilder.FEAT_MONTH_OF_YEAR,
                StockoutFeatureBuilder.FEAT_DAY_OF_WEEK,
                StockoutFeatureBuilder.FEAT_IS_PAYDAY_WEEK
        );
    }

    // ── No NaN / Infinite ─────────────────────────────────────────────────

    @Test
    void buildExample_noNaNOrInfiniteValues() {
        ArrayExample<Label> example = builder.buildExample(typicalInventory());
        for (Feature f : example) {
            assertThat(Double.isNaN(f.getValue())).as("NaN in " + f.getName()).isFalse();
            assertThat(Double.isInfinite(f.getValue())).as("Infinite in " + f.getName()).isFalse();
        }
    }

    @Test
    void buildExample_allNullFields_noNaNOrInfinite() {
        InventoryFeatures empty = InventoryFeatures.builder()
                .warehouseId(UUID.randomUUID()).productId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .build();

        ArrayExample<Label> example = builder.buildExample(empty);
        for (Feature f : example) {
            assertThat(Double.isNaN(f.getValue())).as("NaN in " + f.getName()).isFalse();
            assertThat(Double.isInfinite(f.getValue())).as("Infinite in " + f.getName()).isFalse();
        }
    }

    // ── daysOfStockRemaining edge cases ───────────────────────────────────

    @Test
    void daysOfStockRemaining_normalCase_computedCorrectly() {
        // 700 stock, rate7d = 70 → dailyRate = 10 → days = 70
        InventoryFeatures f = InventoryFeatures.builder()
                .warehouseId(UUID.randomUUID()).productId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .currentStock(BigDecimal.valueOf(700))
                .consumptionRate7d(BigDecimal.valueOf(70))
                .build();

        double days = builder.computeDaysOfStockRemaining(f);
        assertThat(days).isEqualTo(70.0);
    }

    @Test
    void daysOfStockRemaining_zeroConsumptionRate_returns30() {
        InventoryFeatures f = InventoryFeatures.builder()
                .warehouseId(UUID.randomUUID()).productId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .currentStock(BigDecimal.valueOf(500))
                .consumptionRate7d(BigDecimal.ZERO)
                .build();

        double days = builder.computeDaysOfStockRemaining(f);
        assertThat(days).isEqualTo(30.0);
    }

    @Test
    void daysOfStockRemaining_nullConsumptionRate_returns30() {
        InventoryFeatures f = InventoryFeatures.builder()
                .warehouseId(UUID.randomUUID()).productId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .currentStock(BigDecimal.valueOf(500))
                .build();

        double days = builder.computeDaysOfStockRemaining(f);
        assertThat(days).isEqualTo(30.0);
    }

    @Test
    void daysOfStockRemaining_nullCurrentStock_returnsZero() {
        InventoryFeatures f = InventoryFeatures.builder()
                .warehouseId(UUID.randomUUID()).productId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .consumptionRate7d(BigDecimal.valueOf(70))
                .build();

        double days = builder.computeDaysOfStockRemaining(f);
        assertThat(days).isEqualTo(0.0);
    }

    // ── Trend encoding ────────────────────────────────────────────────────

    @Test
    void increasingTrend_encodedAs1() {
        InventoryFeatures f = inventoryWithTrend("INCREASING");
        double trend = featureValue(builder.buildExample(f), StockoutFeatureBuilder.FEAT_CONSUMPTION_TREND);
        assertThat(trend).isEqualTo(1.0);
    }

    @Test
    void decreasingTrend_encodedAsNeg1() {
        InventoryFeatures f = inventoryWithTrend("DECREASING");
        double trend = featureValue(builder.buildExample(f), StockoutFeatureBuilder.FEAT_CONSUMPTION_TREND);
        assertThat(trend).isEqualTo(-1.0);
    }

    // ── Dataset building ──────────────────────────────────────────────────

    @Test
    void buildDataset_sizeMatchesInput() {
        List<InventoryFeatures> features = List.of(typicalInventory(), typicalInventory());
        List<String> labels = List.of(StockoutFeatureBuilder.LABEL_STOCKOUT,
                StockoutFeatureBuilder.LABEL_NO_STOCKOUT);

        MutableDataset<Label> dataset = builder.buildDataset(features, labels);
        assertThat(dataset.size()).isEqualTo(2);
    }

    @Test
    void buildDataset_mismatchedSizes_throwsException() {
        List<InventoryFeatures> features = List.of(typicalInventory(), typicalInventory());
        List<String> labels = List.of(StockoutFeatureBuilder.LABEL_STOCKOUT); // size 1

        assertThatThrownBy(() -> builder.buildDataset(features, labels))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same size");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private InventoryFeatures typicalInventory() {
        return InventoryFeatures.builder()
                .warehouseId(UUID.randomUUID()).productId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .currentStock(BigDecimal.valueOf(500))
                .consumptionRate7d(BigDecimal.valueOf(70))
                .consumptionRate30d(BigDecimal.valueOf(280))
                .consumptionTrend("STABLE")
                .pendingReservedQty(BigDecimal.valueOf(50))
                .expectedIncomingQty(BigDecimal.valueOf(100))
                .discrepancyPct(-2.0)
                .manualAdjustmentCount7d(1)
                .build();
    }

    private InventoryFeatures inventoryWithTrend(String trend) {
        return InventoryFeatures.builder()
                .warehouseId(UUID.randomUUID()).productId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .consumptionTrend(trend)
                .build();
    }

    private List<String> featureNames(ArrayExample<Label> example) {
        List<String> names = new ArrayList<>();
        for (Feature f : example) names.add(f.getName());
        return names;
    }

    private double featureValue(ArrayExample<Label> example, String name) {
        for (Feature f : example) {
            if (f.getName().equals(name)) return f.getValue();
        }
        throw new AssertionError("Feature not found: " + name);
    }
}
