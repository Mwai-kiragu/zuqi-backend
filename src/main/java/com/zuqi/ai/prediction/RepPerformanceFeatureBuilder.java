package com.zuqi.ai.prediction;

import com.zuqi.ai.feature.SalesRepFeatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Feature;
import org.tribuo.MutableDataset;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.RegressionFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds Tribuo regression feature vectors for sales rep performance prediction.
 *
 * 12 features:
 *   visit_count_vs_target, order_conversion_rate, total_order_value_normalized,
 *   avg_order_value, new_merchants_acquired, merchant_retention_rate,
 *   collection_rate, route_adherence_pct, territory_penetration_pct,
 *   month_of_year, is_end_of_month, days_in_period
 *
 * Blueprint reference: implementation_plan.md Phase 4, Step 7b
 */
@Service
@Slf4j
public class RepPerformanceFeatureBuilder {

    private static final RegressionFactory REGRESSION_FACTORY = new RegressionFactory();
    static final String OUTPUT_DIMENSION = "performance_score";

    static final String FEAT_VISIT_COUNT_VS_TARGET       = "visit_count_vs_target";
    static final String FEAT_ORDER_CONVERSION_RATE       = "order_conversion_rate";
    static final String FEAT_TOTAL_ORDER_VALUE_NORM      = "total_order_value_normalized";
    static final String FEAT_AVG_ORDER_VALUE             = "avg_order_value";
    static final String FEAT_NEW_MERCHANTS_ACQUIRED      = "new_merchants_acquired";
    static final String FEAT_MERCHANT_RETENTION_RATE     = "merchant_retention_rate";
    static final String FEAT_COLLECTION_RATE             = "collection_rate";
    static final String FEAT_ROUTE_ADHERENCE_PCT         = "route_adherence_pct";
    static final String FEAT_TERRITORY_PENETRATION_PCT   = "territory_penetration_pct";
    static final String FEAT_MONTH_OF_YEAR               = "month_of_year";
    static final String FEAT_IS_END_OF_MONTH             = "is_end_of_month";
    static final String FEAT_DAYS_IN_PERIOD              = "days_in_period";

    // Normalisation constant for order value (KES 1,000,000 reference)
    private static final double ORDER_VALUE_NORM = 1_000_000.0;

    /**
     * Build example for inference (target score = 0.0 — unused during prediction).
     */
    public ArrayExample<Regressor> buildExample(SalesRepFeatures features) {
        Regressor output = new Regressor(OUTPUT_DIMENSION, 0.0);
        return new ArrayExample<>(output, buildFeatureList(features));
    }

    /**
     * Build labelled example for training.
     */
    public ArrayExample<Regressor> buildLabelledExample(SalesRepFeatures features, double score) {
        Regressor output = new Regressor(OUTPUT_DIMENSION, score);
        return new ArrayExample<>(output, buildFeatureList(features));
    }

    /**
     * Build a training dataset.
     */
    public MutableDataset<Regressor> buildDataset(List<SalesRepFeatures> featuresList,
                                                   List<Double> scores) {
        if (featuresList.size() != scores.size()) {
            throw new IllegalArgumentException("Features and scores must have the same size");
        }

        SimpleDataSourceProvenance provenance = new SimpleDataSourceProvenance(
                "rep_performance_training", REGRESSION_FACTORY);
        MutableDataset<Regressor> dataset = new MutableDataset<>(provenance, REGRESSION_FACTORY);

        for (int i = 0; i < featuresList.size(); i++) {
            dataset.add(buildLabelledExample(featuresList.get(i), scores.get(i)));
        }

        log.info("Built rep performance training dataset: {} examples", dataset.size());
        return dataset;
    }

    // ── Private ───────────────────────────────────────────────────────────

    private List<Feature> buildFeatureList(SalesRepFeatures f) {
        List<Feature> list = new ArrayList<>();

        list.add(new Feature(FEAT_VISIT_COUNT_VS_TARGET,
                f.visitCountVsTarget() != null ? f.visitCountVsTarget() : 0.0));

        list.add(new Feature(FEAT_ORDER_CONVERSION_RATE,
                f.orderConversionRate() != null ? f.orderConversionRate() : 0.0));

        list.add(new Feature(FEAT_TOTAL_ORDER_VALUE_NORM,
                f.totalOrderValue() != null ? f.totalOrderValue().doubleValue() / ORDER_VALUE_NORM : 0.0));

        list.add(new Feature(FEAT_AVG_ORDER_VALUE,
                f.avgOrderValue() != null ? f.avgOrderValue().doubleValue() / ORDER_VALUE_NORM : 0.0));

        list.add(new Feature(FEAT_NEW_MERCHANTS_ACQUIRED,
                f.newMerchantsAcquired() != null ? f.newMerchantsAcquired().doubleValue() : 0.0));

        list.add(new Feature(FEAT_MERCHANT_RETENTION_RATE,
                f.merchantRetentionRate() != null ? f.merchantRetentionRate() : 0.0));

        list.add(new Feature(FEAT_COLLECTION_RATE,
                f.collectionRate() != null ? f.collectionRate() : 0.0));

        list.add(new Feature(FEAT_ROUTE_ADHERENCE_PCT,
                f.routeAdherencePct() != null ? f.routeAdherencePct() : 0.0));

        list.add(new Feature(FEAT_TERRITORY_PENETRATION_PCT,
                f.territoryPenetrationPct() != null ? f.territoryPenetrationPct() : 0.0));

        // Calendar context
        int monthOfYear = f.periodEnd() != null ? f.periodEnd().getMonthValue() : 6;
        list.add(new Feature(FEAT_MONTH_OF_YEAR, (double) monthOfYear));

        boolean isEndOfMonth = f.periodEnd() != null && f.periodEnd().getDayOfMonth() >= 25;
        list.add(new Feature(FEAT_IS_END_OF_MONTH, isEndOfMonth ? 1.0 : 0.0));

        double daysInPeriod = 30.0;
        if (f.periodStart() != null && f.periodEnd() != null) {
            daysInPeriod = java.time.temporal.ChronoUnit.DAYS.between(
                    f.periodStart().toLocalDate(), f.periodEnd().toLocalDate());
        }
        list.add(new Feature(FEAT_DAYS_IN_PERIOD, daysInPeriod));

        return list;
    }

    public int getFeatureCount() {
        return 12;
    }
}
