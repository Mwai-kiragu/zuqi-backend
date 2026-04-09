package com.zuqi.ai.crm;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tribuo.MutableDataset;
import org.tribuo.clustering.ClusterID;
import org.tribuo.clustering.ClusteringFactory;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import java.util.List;

/**
 * Converts {@link CustomerAnalyticsFeatures} into Tribuo {@code Example<ClusterID>}
 * for K-Means segmentation training and inference.
 *
 * <p>9 features selected to capture the three key segmentation axes:
 * value (revenue, AOV), engagement (frequency, recency) and health (payment, credit, trend).
 */
@Component
@RequiredArgsConstructor
public class SegmentationFeatureBuilder {

    private static final int FEATURE_COUNT = 9;
    private static final ClusteringFactory CLUSTERING_FACTORY = new ClusteringFactory();

    private static final String[] FEATURE_NAMES = {
            "total_revenue_90d",
            "order_frequency_per_week",
            "avg_order_value",
            "revenue_trend_slope",
            "payment_timeliness_score",
            "credit_utilization_pct",
            "product_diversity_score",
            "tenure_months",
            "days_since_last_order"
    };

    public int getFeatureCount() {
        return FEATURE_COUNT;
    }

    /**
     * Build an unlabelled inference example (cluster unknown).
     */
    public org.tribuo.Example<ClusterID> buildExample(CustomerAnalyticsFeatures f) {
        ClusterID output = new ClusterID(ClusterID.UNASSIGNED);
        ArrayExample<ClusterID> example = new ArrayExample<>(output, FEATURE_NAMES, featureValues(f));
        return example;
    }

    /**
     * Build a Tribuo dataset for training.
     */
    public MutableDataset<ClusterID> buildDataset(List<CustomerAnalyticsFeatures> features) {
        MutableDataset<ClusterID> dataset = new MutableDataset<>(
                new SimpleDataSourceProvenance("SegmentationFeatureBuilder", CLUSTERING_FACTORY),
                CLUSTERING_FACTORY);

        for (CustomerAnalyticsFeatures f : features) {
            dataset.add(buildExample(f));
        }
        return dataset;
    }

    // ── Public feature extraction ──────────────────────────────────────────────

    /**
     * Returns the raw feature vector for a given {@link CustomerAnalyticsFeatures}.
     * Used by {@code ModelEvaluator.evaluateSegmentation()} to compute silhouette scores.
     */
    public double[] toFeatureVector(CustomerAnalyticsFeatures f) {
        return featureValues(f);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private double[] featureValues(CustomerAnalyticsFeatures f) {
        return new double[]{
                Math.max(0.0, f.totalRevenue90d()),
                Math.max(0.0, f.orderFrequencyPerWeek()),
                Math.max(0.0, f.avgOrderValue()),
                f.revenueTrendSlope(),                       // may be negative
                Math.min(100.0, Math.max(0.0, f.paymentTimelinessScore())),
                Math.min(100.0, Math.max(0.0, f.creditUtilizationPct())),
                Math.min(1.0, Math.max(0.0, f.productDiversityScore())),
                (double) Math.max(0, f.tenureMonths()),
                (double) Math.max(0, Math.min(9999, f.daysSinceLastOrder()))
        };
    }
}
