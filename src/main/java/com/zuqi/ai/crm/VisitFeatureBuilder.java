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
 * Converts visit-specific features into Tribuo {@code Example<Regressor>}
 * for predicting order conversion probability on a given day of the week.
 *
 * <p>10 features capturing day-level and customer-level signals.
 */
@Component
@RequiredArgsConstructor
public class VisitFeatureBuilder {

    private static final int FEATURE_COUNT = 10;
    private static final RegressionFactory REGRESSION_FACTORY = new RegressionFactory();

    private static final String[] FEATURE_NAMES = {
            "day_of_week",
            "days_since_last_order",
            "avg_order_value",
            "order_count_on_this_day",
            "is_payday_week",
            "is_month_start",
            "tenure_months",
            "order_frequency_per_week",
            "revenue_trend_slope",
            "credit_utilization_pct"
    };

    public int getFeatureCount() {
        return FEATURE_COUNT;
    }

    /**
     * Build an inference example for a specific day of week.
     *
     * @param f                 customer analytics features
     * @param dayOfWeek         1=Monday … 7=Sunday
     * @param orderCountOnDay   historical orders placed on this day of week
     * @param isPaydayWeek      true if we're in the last week of the month (payday)
     * @param isMonthStart      true if day of month is 1–5
     */
    public Example<Regressor> buildExample(CustomerAnalyticsFeatures f,
                                            int dayOfWeek,
                                            double orderCountOnDay,
                                            boolean isPaydayWeek,
                                            boolean isMonthStart) {
        Regressor target = new Regressor("order_conversion", 0.0);
        return new ArrayExample<>(target, FEATURE_NAMES,
                featureValues(f, dayOfWeek, orderCountOnDay, isPaydayWeek, isMonthStart));
    }

    /**
     * Build a labelled training example with known conversion outcome (0.0 or 1.0).
     */
    public Example<Regressor> buildLabelledExample(CustomerAnalyticsFeatures f,
                                                    int dayOfWeek,
                                                    double orderCountOnDay,
                                                    boolean isPaydayWeek,
                                                    boolean isMonthStart,
                                                    double conversionOutcome) {
        Regressor target = new Regressor("order_conversion",
                Math.max(0.0, Math.min(1.0, conversionOutcome)));
        return new ArrayExample<>(target, FEATURE_NAMES,
                featureValues(f, dayOfWeek, orderCountOnDay, isPaydayWeek, isMonthStart));
    }

    /**
     * Build a training dataset.
     */
    public MutableDataset<Regressor> buildDataset(List<LabelledVisitExample> examples) {
        MutableDataset<Regressor> dataset = new MutableDataset<>(
                new SimpleDataSourceProvenance("VisitFeatureBuilder", REGRESSION_FACTORY),
                REGRESSION_FACTORY);

        for (LabelledVisitExample le : examples) {
            dataset.add(le.toExample(this));
        }
        return dataset;
    }

    public record LabelledVisitExample(
            CustomerAnalyticsFeatures features,
            int dayOfWeek,
            double orderCountOnDay,
            boolean isPaydayWeek,
            boolean isMonthStart,
            double conversionOutcome
    ) {
        public Example<Regressor> toExample(VisitFeatureBuilder builder) {
            return builder.buildLabelledExample(features, dayOfWeek, orderCountOnDay,
                    isPaydayWeek, isMonthStart, conversionOutcome);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double[] featureValues(CustomerAnalyticsFeatures f,
                                    int dayOfWeek,
                                    double orderCountOnDay,
                                    boolean isPaydayWeek,
                                    boolean isMonthStart) {
        return new double[]{
                (double) dayOfWeek,
                (double) Math.max(0, Math.min(9999, f.daysSinceLastOrder())),
                Math.max(0.0, f.avgOrderValue()),
                Math.max(0.0, orderCountOnDay),
                isPaydayWeek ? 1.0 : 0.0,
                isMonthStart ? 1.0 : 0.0,
                (double) Math.max(0, f.tenureMonths()),
                Math.max(0.0, f.orderFrequencyPerWeek()),
                f.revenueTrendSlope(),
                Math.min(100.0, Math.max(0.0, f.creditUtilizationPct()))
        };
    }
}
