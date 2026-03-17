package com.zuqi.ai.prediction;

import com.zuqi.ai.feature.InventoryFeatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Feature;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds Tribuo classification feature vectors for stockout prediction.
 *
 * 13 features:
 *   current_stock, consumption_rate_7d, consumption_rate_30d, consumption_trend,
 *   pending_reserved_qty, expected_incoming_qty, days_of_stock_remaining,
 *   discrepancy_pct, manual_adj_count_7d, month_of_year, day_of_week, is_payday_week,
 *   predicted_demand_7d
 *
 * Blueprint reference: implementation_plan.md Phase 4, Step 7a
 */
@Service
@Slf4j
public class StockoutFeatureBuilder {

    private static final LabelFactory LABEL_FACTORY = new LabelFactory();

    static final String FEAT_CURRENT_STOCK          = "current_stock";
    static final String FEAT_CONSUMPTION_RATE_7D    = "consumption_rate_7d";
    static final String FEAT_CONSUMPTION_RATE_30D   = "consumption_rate_30d";
    static final String FEAT_CONSUMPTION_TREND      = "consumption_trend";
    static final String FEAT_PENDING_RESERVED_QTY   = "pending_reserved_qty";
    static final String FEAT_EXPECTED_INCOMING_QTY  = "expected_incoming_qty";
    static final String FEAT_DAYS_OF_STOCK_REMAINING = "days_of_stock_remaining";
    static final String FEAT_DISCREPANCY_PCT        = "discrepancy_pct";
    static final String FEAT_MANUAL_ADJ_COUNT_7D    = "manual_adj_count_7d";
    static final String FEAT_MONTH_OF_YEAR          = "month_of_year";
    static final String FEAT_DAY_OF_WEEK            = "day_of_week";
    static final String FEAT_IS_PAYDAY_WEEK         = "is_payday_week";
    static final String FEAT_PREDICTED_DEMAND_7D    = "predicted_demand_7d";

    public static final String LABEL_STOCKOUT    = "STOCKOUT";
    public static final String LABEL_NO_STOCKOUT = "NO_STOCKOUT";

    /**
     * Build example for inference (labelled NO_STOCKOUT — label unused during prediction).
     */
    public ArrayExample<Label> buildExample(InventoryFeatures features) {
        return new ArrayExample<>(new Label(LABEL_NO_STOCKOUT), buildFeatureList(features));
    }

    /**
     * Build labelled example for training.
     */
    public ArrayExample<Label> buildLabelledExample(InventoryFeatures features, String label) {
        return new ArrayExample<>(new Label(label), buildFeatureList(features));
    }

    /**
     * Build a training dataset.
     */
    public MutableDataset<Label> buildDataset(List<InventoryFeatures> featuresList,
                                               List<String> labels) {
        if (featuresList.size() != labels.size()) {
            throw new IllegalArgumentException("Features and labels must have the same size");
        }

        SimpleDataSourceProvenance provenance = new SimpleDataSourceProvenance(
                "stockout_prediction_training", LABEL_FACTORY);
        MutableDataset<Label> dataset = new MutableDataset<>(provenance, LABEL_FACTORY);

        for (int i = 0; i < featuresList.size(); i++) {
            dataset.add(buildLabelledExample(featuresList.get(i), labels.get(i)));
        }

        log.info("Built stockout prediction dataset: {} examples", dataset.size());
        return dataset;
    }

    // ── Private ───────────────────────────────────────────────────────────

    private List<Feature> buildFeatureList(InventoryFeatures f) {
        List<Feature> list = new ArrayList<>();

        list.add(new Feature(FEAT_CURRENT_STOCK,
                f.currentStock() != null ? f.currentStock().doubleValue() : 0.0));

        list.add(new Feature(FEAT_CONSUMPTION_RATE_7D,
                f.consumptionRate7d() != null ? f.consumptionRate7d().doubleValue() : 0.0));

        list.add(new Feature(FEAT_CONSUMPTION_RATE_30D,
                f.consumptionRate30d() != null ? f.consumptionRate30d().doubleValue() : 0.0));

        list.add(new Feature(FEAT_CONSUMPTION_TREND, encodeTrend(f.consumptionTrend())));

        list.add(new Feature(FEAT_PENDING_RESERVED_QTY,
                f.pendingReservedQty() != null ? f.pendingReservedQty().doubleValue() : 0.0));

        list.add(new Feature(FEAT_EXPECTED_INCOMING_QTY,
                f.expectedIncomingQty() != null ? f.expectedIncomingQty().doubleValue() : 0.0));

        list.add(new Feature(FEAT_DAYS_OF_STOCK_REMAINING, computeDaysOfStockRemaining(f)));

        list.add(new Feature(FEAT_DISCREPANCY_PCT,
                f.discrepancyPct() != null ? f.discrepancyPct() : 0.0));

        list.add(new Feature(FEAT_MANUAL_ADJ_COUNT_7D,
                f.manualAdjustmentCount7d() != null ? f.manualAdjustmentCount7d().doubleValue() : 0.0));

        // Calendar features (default to generic values for inference without date context)
        list.add(new Feature(FEAT_MONTH_OF_YEAR,
                f.computedAt() != null ? f.computedAt().getMonthValue() : 6.0));

        list.add(new Feature(FEAT_DAY_OF_WEEK,
                f.computedAt() != null ? f.computedAt().getDayOfWeek().getValue() : 3.0));

        list.add(new Feature(FEAT_IS_PAYDAY_WEEK,
                f.computedAt() != null && isPaydayWeek(f.computedAt().getDayOfMonth()) ? 1.0 : 0.0));

        // Demand forecast — 0.0 when unavailable (SYNTHETIC phase); real value once forecaster is active
        list.add(new Feature(FEAT_PREDICTED_DEMAND_7D,
                f.predictedDemand7d() != null ? f.predictedDemand7d().doubleValue() : 0.0));

        return list;
    }

    public double computeDaysOfStockRemaining(InventoryFeatures f) {
        if (f.currentStock() == null) return 0.0;

        // Prefer demand forecast (forward-looking) over historical consumption rate
        double effectiveDemand7d;
        if (f.predictedDemand7d() != null && f.predictedDemand7d().doubleValue() > 0.0) {
            effectiveDemand7d = f.predictedDemand7d().doubleValue();
        } else if (f.consumptionRate7d() != null && f.consumptionRate7d().doubleValue() > 0.0) {
            effectiveDemand7d = f.consumptionRate7d().doubleValue();
        } else {
            return 30.0; // no consumption signal — default safe value
        }

        double dailyRate = effectiveDemand7d / 7.0;
        return f.currentStock().doubleValue() / dailyRate;
    }

    private double encodeTrend(String trend) {
        if (trend == null) return 0.0;
        return switch (trend) {
            case "INCREASING" -> 1.0;
            case "DECREASING" -> -1.0;
            default -> 0.0;
        };
    }

    private boolean isPaydayWeek(int dayOfMonth) {
        return dayOfMonth >= 28 || dayOfMonth <= 5;
    }

    public int getFeatureCount() {
        return 13;
    }
}
