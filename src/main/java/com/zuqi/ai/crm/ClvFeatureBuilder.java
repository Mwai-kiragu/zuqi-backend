package com.zuqi.ai.crm;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.Regressor;

import java.util.List;

/**
 * Converts {@link CustomerAnalyticsFeatures} into Tribuo {@code Example<Regressor>}
 * for CLV (Customer Lifetime Value) prediction.
 *
 * <p>12 features used to predict {@code revenue12m} (predicted 12-month revenue).
 */
@Component
@RequiredArgsConstructor
public class ClvFeatureBuilder {

    private static final int FEATURE_COUNT = 12;
    private static final RegressionFactory REGRESSION_FACTORY = new RegressionFactory();

    private static final String[] FEATURE_NAMES = {
            "tenure_months",
            "lifetime_revenue",
            "revenue_3m",
            "revenue_6m",
            "revenue_12m",
            "order_frequency_per_week",
            "avg_order_value",
            "revenue_trend_slope",
            "payment_timeliness_score",
            "credit_utilization_pct",
            "product_diversity_score",
            "order_count_90d"
    };

    public int getFeatureCount() {
        return FEATURE_COUNT;
    }

    /**
     * Build an inference example (target = 0.0 placeholder).
     */
    public Example<Regressor> buildExample(CustomerAnalyticsFeatures f) {
        Regressor target = new Regressor("predicted_revenue_12m", 0.0);
        return new ArrayExample<>(target, FEATURE_NAMES, featureValues(f));
    }

    /**
     * Build a labelled training example with known 12-month revenue target.
     */
    public Example<Regressor> buildLabelledExample(CustomerAnalyticsFeatures f, double targetRevenue12m) {
        Regressor target = new Regressor("predicted_revenue_12m",
                Math.max(0.0, targetRevenue12m));
        return new ArrayExample<>(target, FEATURE_NAMES, featureValues(f));
    }

    /**
     * Build a training dataset from labelled examples.
     */
    public MutableDataset<Regressor> buildDataset(List<LabelledClvExample> examples) {
        MutableDataset<Regressor> dataset = new MutableDataset<>(
                new SimpleDataSourceProvenance("ClvFeatureBuilder", REGRESSION_FACTORY),
                REGRESSION_FACTORY);

        for (LabelledClvExample le : examples) {
            dataset.add(buildLabelledExample(le.features(), le.revenue12mTarget()));
        }
        return dataset;
    }

    public record LabelledClvExample(CustomerAnalyticsFeatures features, double revenue12mTarget) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double[] featureValues(CustomerAnalyticsFeatures f) {
        return new double[]{
                (double) Math.max(0, f.tenureMonths()),
                Math.max(0.0, f.lifetimeRevenue()),
                Math.max(0.0, f.revenue3m()),
                Math.max(0.0, f.revenue6m()),
                Math.max(0.0, f.revenue12m()),
                Math.max(0.0, f.orderFrequencyPerWeek()),
                Math.max(0.0, f.avgOrderValue()),
                f.revenueTrendSlope(),
                Math.min(100.0, Math.max(0.0, f.paymentTimelinessScore())),
                Math.min(100.0, Math.max(0.0, f.creditUtilizationPct())),
                Math.min(1.0, Math.max(0.0, f.productDiversityScore())),
                Math.max(0.0, f.orderCount90d())
        };
    }
}
