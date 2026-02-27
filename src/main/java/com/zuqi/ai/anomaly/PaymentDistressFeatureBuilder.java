package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.MerchantPaymentTrendFeatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Feature;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds Tribuo classification feature vectors for payment distress prediction.
 *
 * <p>20 features covering payment timing, credit utilisation, partial payment
 * behaviour, order-value trends, and overall financial health — identical to the
 * feature vector used by {@link PaymentDistressClassifier} at inference time.
 *
 * <p>Blueprint reference: implementation_plan.md Phase 6 — Payment Distress Classification
 */
@Service
@Slf4j
public class PaymentDistressFeatureBuilder {

    private static final LabelFactory LABEL_FACTORY = new LabelFactory();

    static final String LABEL_DISTRESS    = "DISTRESS";
    static final String LABEL_NO_DISTRESS = "NO_DISTRESS";

    /**
     * Build an unlabelled example for inference (label unused during prediction).
     */
    public ArrayExample<Label> buildExample(MerchantPaymentTrendFeatures f) {
        return new ArrayExample<>(new Label(LABEL_NO_DISTRESS), buildFeatureList(f));
    }

    /**
     * Build a labelled example for training.
     */
    public ArrayExample<Label> buildLabelledExample(MerchantPaymentTrendFeatures f, String label) {
        return new ArrayExample<>(new Label(label), buildFeatureList(f));
    }

    /**
     * Build a training dataset from lists of features and labels.
     */
    public MutableDataset<Label> buildDataset(List<MerchantPaymentTrendFeatures> featuresList,
                                               List<String> labels) {
        if (featuresList.size() != labels.size()) {
            throw new IllegalArgumentException("Features and labels must have the same size");
        }

        SimpleDataSourceProvenance provenance = new SimpleDataSourceProvenance(
                "payment_distress_training", LABEL_FACTORY);
        MutableDataset<Label> dataset = new MutableDataset<>(provenance, LABEL_FACTORY);

        for (int i = 0; i < featuresList.size(); i++) {
            dataset.add(buildLabelledExample(featuresList.get(i), labels.get(i)));
        }

        log.info("Built payment distress dataset: {} examples", dataset.size());
        return dataset;
    }

    // ── Private ───────────────────────────────────────────────────────────

    /**
     * 20 features — mirrors {@link PaymentDistressClassifier#buildExample} exactly.
     */
    private List<Feature> buildFeatureList(MerchantPaymentTrendFeatures f) {
        List<Feature> list = new ArrayList<>();

        // Payment timing trends
        list.add(new Feature("days_to_pay_trend_3m", safeDouble(f.daysToPayTrend3m())));
        list.add(new Feature("days_to_pay_stddev_3m", safeDouble(f.daysToPayStddev3m())));
        list.add(new Feature("late_payment_rate_3m", safeDouble(f.latePaymentRate3m())));
        list.add(new Feature("late_payment_rate_trend_3m", safeDouble(f.latePaymentRateTrend3m())));

        // Order frequency trends
        list.add(new Feature("order_frequency_3m", safeDouble(f.orderFrequency3m())));
        list.add(new Feature("order_frequency_trend_3m", safeDouble(f.orderFrequencyTrend3m())));
        list.add(new Feature("consecutive_missed_orders", safeDoubleFromInt(f.consecutiveMissedOrders())));

        // Credit utilisation trends
        list.add(new Feature("credit_utilization_3m", safeDouble(f.creditUtilization3m())));
        list.add(new Feature("credit_utilization_trajectory", safeDouble(f.creditUtilizationTrajectory())));
        list.add(new Feature("peak_utilization_3m", safeDouble(f.peakUtilization3m())));
        list.add(new Feature("hit_credit_limit_3m", Boolean.TRUE.equals(f.hitCreditLimit3m()) ? 1.0 : 0.0));

        // Partial payment trends
        list.add(new Feature("partial_payment_freq_3m", safeDouble(f.partialPaymentFreq3m())));
        list.add(new Feature("partial_payment_freq_trend_3m", safeDouble(f.partialPaymentFreqTrend3m())));
        list.add(new Feature("consecutive_partial_payments", safeDoubleFromInt(f.consecutivePartialPayments())));

        // Order value trends
        list.add(new Feature("avg_order_value_3m", safeDouble(f.avgOrderValue3m())));
        list.add(new Feature("avg_order_value_trend_3m", safeDouble(f.avgOrderValueTrend3m())));
        list.add(new Feature("order_value_volatility_3m", safeDouble(f.orderValueVolatility3m())));

        // Overall financial health
        list.add(new Feature("outstanding_trend_3m", safeDouble(f.outstandingTrend3m())));
        list.add(new Feature("days_overdue_max", safeDoubleFromInt(f.daysOverdueMax())));
        list.add(new Feature("payment_to_order_ratio_3m", safeDouble(f.paymentToOrderRatio3m())));

        return list;
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private double safeDoubleFromInt(Integer value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    private double safeDoubleFromBigDecimal(BigDecimal value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    public int getFeatureCount() {
        return 20;
    }
}
