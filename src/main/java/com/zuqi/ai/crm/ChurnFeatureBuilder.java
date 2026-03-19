package com.zuqi.ai.crm;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import java.util.List;

/**
 * Converts {@link CustomerAnalyticsFeatures} into Tribuo {@code Example<Label>}
 * for churn prediction (binary: CHURNED / ACTIVE).
 *
 * <p>10 features focused on recency, frequency and trend signals that indicate
 * a customer is at risk of churning.
 */
@Component
@RequiredArgsConstructor
public class ChurnFeatureBuilder {

    private static final int FEATURE_COUNT = 10;
    private static final LabelFactory LABEL_FACTORY = new LabelFactory();

    static final String LABEL_CHURNED = "CHURNED";
    static final String LABEL_ACTIVE = "ACTIVE";

    private static final String[] FEATURE_NAMES = {
            "days_since_last_order",
            "order_count_30d",
            "order_count_90d",
            "ratio_30d_vs_90d",
            "order_frequency_per_week",
            "avg_order_value",
            "revenue_trend_slope",
            "credit_utilization_pct",
            "tenure_months",
            "payment_timeliness_score"
    };

    public int getFeatureCount() {
        return FEATURE_COUNT;
    }

    /**
     * Build an inference example (no churn label known).
     */
    public Example<Label> buildExample(CustomerAnalyticsFeatures f) {
        Label output = new Label(LABEL_ACTIVE);
        return new ArrayExample<>(output, FEATURE_NAMES, featureValues(f));
    }

    /**
     * Build a labelled training example.
     *
     * @param f       analytics features
     * @param churned true if this customer churned, false if still active
     */
    public Example<Label> buildLabelledExample(CustomerAnalyticsFeatures f, boolean churned) {
        Label output = new Label(churned ? LABEL_CHURNED : LABEL_ACTIVE);
        return new ArrayExample<>(output, FEATURE_NAMES, featureValues(f));
    }

    /**
     * Build a training dataset from labelled examples.
     */
    public MutableDataset<Label> buildDataset(List<LabelledChurnExample> examples) {
        MutableDataset<Label> dataset = new MutableDataset<>(
                new SimpleDataSourceProvenance("ChurnFeatureBuilder", LABEL_FACTORY),
                LABEL_FACTORY);

        for (LabelledChurnExample le : examples) {
            dataset.add(buildLabelledExample(le.features(), le.churned()));
        }
        return dataset;
    }

    public record LabelledChurnExample(CustomerAnalyticsFeatures features, boolean churned) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double[] featureValues(CustomerAnalyticsFeatures f) {
        double orderCount30d = Math.max(0.0, f.orderCount30d());
        double orderCount90d = Math.max(0.0, f.orderCount90d());
        double ratio30dVs90d = orderCount90d > 0 ? orderCount30d / orderCount90d : 0.0;

        return new double[]{
                (double) Math.max(0, Math.min(9999, f.daysSinceLastOrder())),
                orderCount30d,
                orderCount90d,
                ratio30dVs90d,
                Math.max(0.0, f.orderFrequencyPerWeek()),
                Math.max(0.0, f.avgOrderValue()),
                f.revenueTrendSlope(),
                Math.min(100.0, Math.max(0.0, f.creditUtilizationPct())),
                (double) Math.max(0, f.tenureMonths()),
                Math.min(100.0, Math.max(0.0, f.paymentTimelinessScore()))
        };
    }
}
