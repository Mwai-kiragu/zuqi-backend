package com.zuqi.ai.anomaly;

import com.zuqi.ai.feature.InventoryFeatures;
import com.zuqi.ai.feature.PaymentFeatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Feature;
import org.tribuo.MutableDataset;
import org.tribuo.anomaly.AnomalyFactory;
import org.tribuo.anomaly.Event;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds Tribuo ML feature vectors for anomaly detection models.
 *
 * Converts InventoryFeatures and PaymentFeatures into Tribuo ArrayExample<Event>
 * for LibSVM one-class anomaly detection inference and training.
 *
 * Blueprint reference: plan.md Section 6.3 - AnomalyFeatureBuilder (Phase 4 foundation)
 */
@Service
@Slf4j
public class AnomalyFeatureBuilder {

    private static final AnomalyFactory ANOMALY_FACTORY = new AnomalyFactory();

    // Shared Event sentinels (constructed once — no static constants in Tribuo 4.3.x Event class)
    static final Event EXPECTED  = new Event(Event.EventType.EXPECTED);
    static final Event ANOMALOUS = new Event(Event.EventType.ANOMALOUS);

    // ── Inventory feature names (8 features) ──────────────────────────────

    static final String FEAT_DISCREPANCY_PCT         = "discrepancy_pct";
    static final String FEAT_MANUAL_ADJ_COUNT_7D     = "manual_adj_count_7d";
    static final String FEAT_UNIQUE_ADJUSTING_USERS  = "unique_adjusting_users";
    static final String FEAT_CONSUMPTION_RATE_7D     = "consumption_rate_7d";
    static final String FEAT_CONSUMPTION_TREND       = "consumption_trend_numeric";
    static final String FEAT_PENDING_RESERVED_PCT    = "pending_reserved_pct";
    static final String FEAT_EXPECTED_INCOMING_PCT   = "expected_incoming_pct";
    static final String FEAT_CURRENT_STOCK_NORM      = "current_stock_normalized";

    // ── Payment feature names (10 features) ───────────────────────────────

    static final String FEAT_DAYS_TO_PAY             = "days_to_pay";
    static final String FEAT_DAYS_TO_PAY_VS_AVG      = "days_to_pay_vs_avg";
    static final String FEAT_GAP_SINCE_LAST_PAYMENT  = "gap_since_last_payment_days";
    static final String FEAT_AMOUNT_VS_INVOICE       = "amount_vs_invoice_ratio";
    static final String FEAT_AMOUNT_VS_MERCHANT_AVG  = "amount_vs_merchant_avg";
    static final String FEAT_HOUR_OF_DAY             = "hour_of_day";
    static final String FEAT_IS_PARTIAL              = "is_partial";
    static final String FEAT_IS_LATE                 = "is_late";
    static final String FEAT_PAYMENT_MPESA           = "payment_method_mpesa";
    static final String FEAT_PAYMENT_CASH            = "payment_method_cash";

    // ── Inventory example ─────────────────────────────────────────────────

    /**
     * Build Tribuo ArrayExample from inventory features, labelled EXPECTED (normal).
     * Used for both inference and normal-data training.
     */
    public ArrayExample<Event> buildInventoryExample(InventoryFeatures features) {
        List<Feature> featureList = buildInventoryFeatureList(features);
        return new ArrayExample<>(EXPECTED, featureList);
    }

    /**
     * Build Tribuo ArrayExample labelled ANOMALOUS — for synthetic test-set injection.
     */
    public ArrayExample<Event> buildAnomalousInventoryExample(InventoryFeatures features) {
        List<Feature> featureList = buildInventoryFeatureList(features);
        return new ArrayExample<>(ANOMALOUS, featureList);
    }

    /**
     * Build a training dataset from normal inventory feature snapshots.
     * All examples labelled EXPECTED (one-class training on normal data).
     */
    public MutableDataset<Event> buildInventoryDataset(List<InventoryFeatures> featuresList) {
        SimpleDataSourceProvenance provenance = new SimpleDataSourceProvenance(
                "inventory_anomaly_training", ANOMALY_FACTORY);
        MutableDataset<Event> dataset = new MutableDataset<>(provenance, ANOMALY_FACTORY);

        for (InventoryFeatures f : featuresList) {
            dataset.add(buildInventoryExample(f));
        }

        log.info("Built inventory anomaly training dataset with {} examples", dataset.size());
        return dataset;
    }

    // ── Payment example ───────────────────────────────────────────────────

    /**
     * Build Tribuo ArrayExample from payment features, labelled EXPECTED (normal).
     */
    public ArrayExample<Event> buildPaymentExample(PaymentFeatures features) {
        List<Feature> featureList = buildPaymentFeatureList(features);
        return new ArrayExample<>(EXPECTED, featureList);
    }

    /**
     * Build Tribuo ArrayExample labelled ANOMALOUS — for synthetic test-set injection.
     */
    public ArrayExample<Event> buildAnomalousPaymentExample(PaymentFeatures features) {
        List<Feature> featureList = buildPaymentFeatureList(features);
        return new ArrayExample<>(ANOMALOUS, featureList);
    }

    /**
     * Build a training dataset from normal payment feature snapshots.
     * All examples labelled EXPECTED (one-class training on normal data).
     */
    public MutableDataset<Event> buildPaymentDataset(List<PaymentFeatures> featuresList) {
        SimpleDataSourceProvenance provenance = new SimpleDataSourceProvenance(
                "payment_anomaly_training", ANOMALY_FACTORY);
        MutableDataset<Event> dataset = new MutableDataset<>(provenance, ANOMALY_FACTORY);

        for (PaymentFeatures f : featuresList) {
            dataset.add(buildPaymentExample(f));
        }

        log.info("Built payment anomaly training dataset with {} examples", dataset.size());
        return dataset;
    }

    // ── Private builders ──────────────────────────────────────────────────

    private List<Feature> buildInventoryFeatureList(InventoryFeatures features) {
        List<Feature> list = new ArrayList<>();

        list.add(new Feature(FEAT_DISCREPANCY_PCT,
                features.discrepancyPct() != null ? features.discrepancyPct() : 0.0));

        list.add(new Feature(FEAT_MANUAL_ADJ_COUNT_7D,
                features.manualAdjustmentCount7d() != null ? features.manualAdjustmentCount7d().doubleValue() : 0.0));

        list.add(new Feature(FEAT_UNIQUE_ADJUSTING_USERS,
                features.adjustingUserIds() != null ? (double) features.adjustingUserIds().size() : 0.0));

        list.add(new Feature(FEAT_CONSUMPTION_RATE_7D,
                features.consumptionRate7d() != null ? features.consumptionRate7d().doubleValue() : 0.0));

        list.add(new Feature(FEAT_CONSUMPTION_TREND, encodeTrend(features.consumptionTrend())));

        list.add(new Feature(FEAT_PENDING_RESERVED_PCT, computePendingReservedPct(features)));

        list.add(new Feature(FEAT_EXPECTED_INCOMING_PCT, computeExpectedIncomingPct(features)));

        list.add(new Feature(FEAT_CURRENT_STOCK_NORM, computeCurrentStockNormalized(features)));

        return list;
    }

    private List<Feature> buildPaymentFeatureList(PaymentFeatures features) {
        List<Feature> list = new ArrayList<>();

        list.add(new Feature(FEAT_DAYS_TO_PAY,
                features.daysToPay() != null ? features.daysToPay() : 0.0));

        list.add(new Feature(FEAT_DAYS_TO_PAY_VS_AVG,
                features.daysToPayVsMerchantAvg() != null ? features.daysToPayVsMerchantAvg() : 0.0));

        list.add(new Feature(FEAT_GAP_SINCE_LAST_PAYMENT,
                features.gapSinceLastPaymentDays() != null ? features.gapSinceLastPaymentDays().doubleValue() : 0.0));

        list.add(new Feature(FEAT_AMOUNT_VS_INVOICE,
                features.amountVsInvoiceRatio() != null ? features.amountVsInvoiceRatio() : 1.0));

        list.add(new Feature(FEAT_AMOUNT_VS_MERCHANT_AVG,
                features.amountVsMerchantAvg() != null ? features.amountVsMerchantAvg() : 1.0));

        list.add(new Feature(FEAT_HOUR_OF_DAY,
                features.hourOfDay() != null ? features.hourOfDay().doubleValue() : 12.0));

        list.add(new Feature(FEAT_IS_PARTIAL,
                Boolean.TRUE.equals(features.isPartial()) ? 1.0 : 0.0));

        list.add(new Feature(FEAT_IS_LATE,
                Boolean.TRUE.equals(features.isLate()) ? 1.0 : 0.0));

        list.add(new Feature(FEAT_PAYMENT_MPESA,
                "MPESA".equalsIgnoreCase(features.paymentMethodEncoded()) ? 1.0 : 0.0));

        list.add(new Feature(FEAT_PAYMENT_CASH,
                "CASH".equalsIgnoreCase(features.paymentMethodEncoded()) ? 1.0 : 0.0));

        return list;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private double encodeTrend(String trend) {
        if (trend == null) return 0.0;
        return switch (trend) {
            case "INCREASING" -> 1.0;
            case "DECREASING" -> -1.0;
            default -> 0.0;
        };
    }

    private double computePendingReservedPct(InventoryFeatures f) {
        if (f.currentStock() == null || f.currentStock().doubleValue() == 0.0) return 0.0;
        if (f.pendingReservedQty() == null) return 0.0;
        return f.pendingReservedQty().doubleValue() / f.currentStock().doubleValue();
    }

    private double computeExpectedIncomingPct(InventoryFeatures f) {
        if (f.consumptionRate7d() == null || f.consumptionRate7d().doubleValue() == 0.0) return 0.0;
        if (f.expectedIncomingQty() == null) return 0.0;
        return f.expectedIncomingQty().doubleValue() / (f.consumptionRate7d().doubleValue() * 7.0);
    }

    private double computeCurrentStockNormalized(InventoryFeatures f) {
        if (f.consumptionRate7d() == null || f.consumptionRate7d().doubleValue() == 0.0) return 1.0;
        if (f.currentStock() == null) return 0.0;
        return f.currentStock().doubleValue() / (f.consumptionRate7d().doubleValue() * 30.0);
    }
}
