package com.zuqi.ai.demand;

import com.zuqi.ai.feature.ExpiryFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.Regressor;

import java.util.List;

/**
 * Converts ExpiryFeatures into Tribuo Example<Regressor> for XGBoost training/inference.
 *
 * Features (8):
 * 1. days_to_expiry
 * 2. current_stock_qty
 * 3. avg_daily_sales_rate
 * 4. projected_days_to_sell
 * 5. similar_sku_velocity
 * 6. warehouse_turnover_rate
 * 7. price_sensitivity_score
 * 8. batch_age_ratio
 *
 * Target: sell_through_probability (0.0 = expires unsold, 1.0 = sells out)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryRiskFeatureBuilder {

    private static final int FEATURE_COUNT = 8;
    private static final RegressionFactory REGRESSION_FACTORY = new RegressionFactory();

    private static final String[] FEATURE_NAMES = {
            "days_to_expiry",
            "current_stock_qty",
            "avg_daily_sales_rate",
            "projected_days_to_sell",
            "similar_sku_velocity",
            "warehouse_turnover_rate",
            "price_sensitivity_score",
            "batch_age_ratio"
    };

    public int getFeatureCount() {
        return FEATURE_COUNT;
    }

    /**
     * Build inference example (no target).
     */
    public Example<Regressor> buildExample(ExpiryFeatures features) {
        return buildLabelledExample(features, 0.0);
    }

    /**
     * Build labelled example for training.
     *
     * @param features      ExpiryFeatures
     * @param sellThrough   Actual sell-through probability (0.0–1.0)
     */
    public Example<Regressor> buildLabelledExample(ExpiryFeatures features, double sellThrough) {
        Regressor target = new Regressor("sell_through", sellThrough);
        return new ArrayExample<>(target, FEATURE_NAMES, extractValues(features));
    }

    /**
     * Build Tribuo dataset from labelled examples.
     */
    public MutableDataset<Regressor> buildDataset(
            List<LabelledExpiryExample> labelledExamples) {

        MutableDataset<Regressor> dataset = new MutableDataset<>(
                new org.tribuo.provenance.SimpleDataSourceProvenance(
                        "ExpiryRiskFeatureBuilder", REGRESSION_FACTORY),
                REGRESSION_FACTORY);

        for (LabelledExpiryExample le : labelledExamples) {
            dataset.add(buildLabelledExample(le.features(), le.sellThroughProbability()));
        }
        return dataset;
    }

    public record LabelledExpiryExample(ExpiryFeatures features, double sellThroughProbability) {}

    // ── Classification variants (HIGH_RISK / LOW_RISK) ────────────────────

    public static final String LABEL_HIGH_RISK = "HIGH_RISK";
    public static final String LABEL_LOW_RISK  = "LOW_RISK";
    private static final double HIGH_RISK_THRESHOLD = 0.5;
    private static final LabelFactory LABEL_FACTORY = new LabelFactory();

    /**
     * Build a classification example for inference (no target label).
     */
    public Example<Label> buildClassificationExample(ExpiryFeatures features) {
        double[] values = extractValues(features);
        ArrayExample<Label> example = new ArrayExample<>(new Label(LABEL_HIGH_RISK), FEATURE_NAMES, values);
        return example;
    }

    /**
     * Build a labelled classification dataset from examples.
     * Examples with sellThroughProbability &lt; 0.5 are labelled HIGH_RISK, otherwise LOW_RISK.
     */
    public MutableDataset<Label> buildClassificationDataset(List<LabelledExpiryExample> labelledExamples) {
        MutableDataset<Label> dataset = new MutableDataset<>(
                new SimpleDataSourceProvenance("ExpiryRiskClassification", LABEL_FACTORY),
                LABEL_FACTORY);
        for (LabelledExpiryExample le : labelledExamples) {
            String labelStr = le.sellThroughProbability() < HIGH_RISK_THRESHOLD ? LABEL_HIGH_RISK : LABEL_LOW_RISK;
            double[] values = extractValues(le.features());
            dataset.add(new ArrayExample<>(new Label(labelStr), FEATURE_NAMES, values));
        }
        return dataset;
    }

    private double[] extractValues(ExpiryFeatures features) {
        return new double[]{
                Math.max(0.0, features.daysToExpiry()),
                Math.max(0.0, features.currentStockQty()),
                Math.max(0.0, features.avgDailySalesRate()),
                Math.min(999.0, Math.max(0.0, features.projectedDaysToSell())),
                Math.max(0.0, features.similarSkuVelocity()),
                Math.max(0.0, features.warehouseTurnoverRate()),
                Math.min(1.0, Math.max(0.0, features.priceSensitivityScore())),
                Math.min(1.0, Math.max(0.0, features.batchAgeRatio()))
        };
    }
}
