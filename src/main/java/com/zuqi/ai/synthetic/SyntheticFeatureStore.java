package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.ai.anomaly.AnomalyFeatureBuilder;
import com.zuqi.ai.credit.CreditMlFeatureBuilder;
import com.zuqi.ai.feature.DemandFeatures;
import com.zuqi.ai.feature.InventoryFeatures;
import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.feature.MerchantPaymentTrendFeatures;
import com.zuqi.ai.feature.PaymentFeatures;
import com.zuqi.ai.feature.SalesRepFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.anomaly.Event;
import org.tribuo.classification.Label;
import org.tribuo.impl.ArrayExample;
import org.tribuo.regression.Regressor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assembles Tribuo training {@link Example} lists from a {@link SyntheticDataBundle}.
 *
 * <p>Each method corresponds to one ML model and delegates in two steps:
 * <ol>
 *   <li>Compute feature records using the appropriate {@code Synthetic*FeatureBuilder}.</li>
 *   <li>Convert feature records to Tribuo {@code Example} objects via the real ML
 *       feature builders ({@link CreditMlFeatureBuilder}, {@link AnomalyFeatureBuilder})
 *       so that the vector layout is identical between synthetic and real training runs.
 *       New classification models build vectors inline with {@link ArrayExample}.</li>
 * </ol>
 *
 * <h3>Supported models (9 total)</h3>
 * <ul>
 *   <li>{@link DataPhaseTracker#MODEL_CREDIT_CLASSIFIER}           → {@code Example<Label>}</li>
 *   <li>{@link DataPhaseTracker#MODEL_CREDIT_LIMIT_REGRESSOR}      → {@code Example<Regressor>}</li>
 *   <li>{@link DataPhaseTracker#MODEL_DEMAND_FORECASTER}           → {@code Example<Regressor>}</li>
 *   <li>{@link DataPhaseTracker#MODEL_STOCKOUT_PREDICTOR}          → {@code Example<Label>}</li>
 *   <li>{@link DataPhaseTracker#MODEL_SHRINKAGE_DETECTOR}          → {@code Example<Event>}</li>
 *   <li>{@link DataPhaseTracker#MODEL_PAYMENT_ANOMALY_DETECTOR}    → {@code Example<Event>}</li>
 *   <li>{@link DataPhaseTracker#MODEL_PAYMENT_DISTRESS_CLASSIFIER} → {@code Example<Label>}</li>
 *   <li>{@link DataPhaseTracker#MODEL_REP_PERFORMANCE_PREDICTOR}   → {@code Example<Label>}</li>
 *   <li>{@link DataPhaseTracker#MODEL_DATA_QUALITY_DETECTOR}       → {@code Example<Label>}</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SyntheticFeatureStore {

    private final SyntheticMerchantFeatureBuilder  merchantFeatureBuilder;
    private final SyntheticPaymentFeatureBuilder   paymentFeatureBuilder;
    private final SyntheticInventoryFeatureBuilder inventoryFeatureBuilder;
    private final SyntheticSalesRepFeatureBuilder  salesRepFeatureBuilder;
    private final SyntheticOrderFeatureBuilder     orderFeatureBuilder;
    private final CreditMlFeatureBuilder           creditMlFeatureBuilder;
    private final AnomalyFeatureBuilder            anomalyFeatureBuilder;

    // ── Credit classifier ──────────────────────────────────────────────────

    /**
     * Build labelled classification examples for credit default prediction.
     *
     * <p>Label is {@code "DEFAULT"} or {@code "NO_DEFAULT"} based on whether the merchant
     * has any {@link SyntheticCreditEvaluation} with {@code defaulted=true}.
     *
     * @param bundle the synthetic dataset
     * @return list of Tribuo {@code Example<Label>} instances
     */
    public List<Example<Label>> buildCreditClassifierExamples(SyntheticDataBundle bundle) {
        LocalDateTime asOfDate = bundle.getGeneratedAt();
        List<Example<Label>> examples = new ArrayList<>();

        for (SyntheticMerchant merchant : bundle.getMerchants()) {
            try {
                MerchantFeatures features = merchantFeatureBuilder.computeFeatures(
                        merchant, bundle, asOfDate);
                boolean defaulted = bundle.getCreditHistoryForMerchant(merchant.syntheticId())
                        .stream()
                        .anyMatch(SyntheticCreditEvaluation::defaulted);
                examples.add(creditMlFeatureBuilder.buildClassificationExample(features, defaulted));
            } catch (Exception ex) {
                log.warn("[SyntheticFeatureStore] Skipping merchant {} for credit classifier: {}",
                        merchant.syntheticId(), ex.getMessage());
            }
        }

        log.info("[SyntheticFeatureStore] Built {} credit classifier examples", examples.size());
        return examples;
    }

    // ── Credit limit regressor ─────────────────────────────────────────────

    /**
     * Build regression examples for credit limit prediction.
     *
     * <p>Target is the credit limit from the merchant's most recent
     * {@link SyntheticCreditEvaluation}, falling back to
     * {@link SyntheticMerchant#initialCreditLimit()} when no evaluations exist.
     *
     * @param bundle the synthetic dataset
     * @return list of Tribuo {@code Example<Regressor>} instances
     */
    public List<Example<Regressor>> buildCreditLimitRegressorExamples(SyntheticDataBundle bundle) {
        LocalDateTime asOfDate = bundle.getGeneratedAt();
        List<Example<Regressor>> examples = new ArrayList<>();

        for (SyntheticMerchant merchant : bundle.getMerchants()) {
            try {
                MerchantFeatures features = merchantFeatureBuilder.computeFeatures(
                        merchant, bundle, asOfDate);
                java.math.BigDecimal targetLimit = bundle
                        .getCreditHistoryForMerchant(merchant.syntheticId())
                        .stream()
                        .max(java.util.Comparator.comparing(SyntheticCreditEvaluation::evaluationDate))
                        .map(SyntheticCreditEvaluation::creditLimit)
                        .orElse(merchant.initialCreditLimit());

                examples.add(creditMlFeatureBuilder.buildRegressionExample(features, targetLimit));
            } catch (Exception ex) {
                log.warn("[SyntheticFeatureStore] Skipping merchant {} for credit limit: {}",
                        merchant.syntheticId(), ex.getMessage());
            }
        }

        log.info("[SyntheticFeatureStore] Built {} credit limit regressor examples", examples.size());
        return examples;
    }

    // ── Demand forecaster ──────────────────────────────────────────────────

    /**
     * Build regression examples for demand forecasting.
     *
     * <p>Iterates over distinct merchant-SKU pairs from the bundle's order items.
     * Uses lag features (qty2w–qty4w, 12-week rolling average, temporal context)
     * as inputs; the 4-week rolling average ({@code rollingAvg4w}) is the regression target.
     *
     * @param bundle the synthetic dataset
     * @return list of Tribuo {@code Example<Regressor>} instances
     */
    public List<Example<Regressor>> buildDemandForecasterExamples(SyntheticDataBundle bundle) {
        LocalDateTime asOfDate = bundle.getGeneratedAt();
        List<Example<Regressor>> examples = new ArrayList<>();

        // merchantId → SyntheticMerchant for O(1) lookups
        Map<UUID, SyntheticMerchant> merchantMap = bundle.getMerchants().stream()
                .collect(Collectors.toMap(SyntheticMerchant::syntheticId, m -> m));

        // Distinct (merchantId, skuId) pairs
        List<MerchantSkuPair> pairs = bundle.getOrders().stream()
                .flatMap(o -> bundle.getItemsForOrder(o.syntheticId()).stream()
                        .map(item -> new MerchantSkuPair(o.merchantRef(), item.skuId())))
                .distinct()
                .collect(Collectors.toList());

        for (MerchantSkuPair pair : pairs) {
            try {
                SyntheticMerchant merchant = merchantMap.get(pair.merchantId());
                if (merchant == null) continue;

                DemandFeatures features = orderFeatureBuilder.computeFeatures(
                        merchant, pair.skuId(), bundle, asOfDate);

                double target = features.rollingAvg4w().doubleValue();
                Regressor regressor = new Regressor("demand_qty", target);
                examples.add(new ArrayExample<>(regressor, buildDemandFeatureVector(features)));
            } catch (Exception ex) {
                log.warn("[SyntheticFeatureStore] Skipping merchant-sku ({},{}) for demand: {}",
                        pair.merchantId(), pair.skuId(), ex.getMessage());
            }
        }

        log.info("[SyntheticFeatureStore] Built {} demand forecaster examples", examples.size());
        return examples;
    }

    // ── Shrinkage detector ─────────────────────────────────────────────────

    /**
     * Build anomaly detection examples for inventory shrinkage.
     *
     * <p>Iterates over distinct warehouse-SKU pairs found in the bundle's inventory
     * movements and computes one {@link InventoryFeatures} snapshot per pair.
     * Movements flagged {@code isShrinkage=true} produce {@code ANOMALOUS} examples;
     * all others produce {@code EXPECTED} examples.
     *
     * @param bundle the synthetic dataset
     * @return list of Tribuo {@code Example<Event>} instances
     */
    public List<Example<Event>> buildShrinkageDetectorExamples(SyntheticDataBundle bundle) {
        LocalDateTime asOfDate = bundle.getGeneratedAt();
        List<Example<Event>> examples = new ArrayList<>();

        // Distinct (warehouseId, skuId) pairs
        List<WarehouseSkuPair> pairs = bundle.getInventoryMovements().stream()
                .map(m -> new WarehouseSkuPair(m.warehouseId(), m.skuId()))
                .distinct()
                .collect(Collectors.toList());

        for (WarehouseSkuPair pair : pairs) {
            try {
                InventoryFeatures features = inventoryFeatureBuilder.computeFeatures(
                        pair.warehouseId(), pair.skuId(), bundle, asOfDate);

                // Determine if this warehouse-SKU has any shrinkage movements
                boolean hasShrinkage = bundle.getInventoryMovements().stream()
                        .anyMatch(m -> m.warehouseId().equals(pair.warehouseId())
                                && m.skuId().equals(pair.skuId())
                                && m.isShrinkage());

                examples.add(hasShrinkage
                        ? anomalyFeatureBuilder.buildAnomalousInventoryExample(features)
                        : anomalyFeatureBuilder.buildInventoryExample(features));
            } catch (Exception ex) {
                log.warn("[SyntheticFeatureStore] Skipping warehouse-sku ({}, {}) for shrinkage: {}",
                        pair.warehouseId(), pair.skuId(), ex.getMessage());
            }
        }

        log.info("[SyntheticFeatureStore] Built {} shrinkage detector examples", examples.size());
        return examples;
    }

    // ── Stockout predictor ─────────────────────────────────────────────────

    /**
     * Build labelled classification examples for stockout prediction.
     *
     * <p>Iterates over the same warehouse-SKU pairs as the shrinkage detector.
     * Label is {@code "STOCKOUT"} when {@code currentStock < consumptionRate7d × 7};
     * otherwise {@code "NO_STOCKOUT"}.
     *
     * @param bundle the synthetic dataset
     * @return list of Tribuo {@code Example<Label>} instances
     */
    public List<Example<Label>> buildStockoutPredictorExamples(SyntheticDataBundle bundle) {
        LocalDateTime asOfDate = bundle.getGeneratedAt();
        List<Example<Label>> examples = new ArrayList<>();

        List<WarehouseSkuPair> pairs = bundle.getInventoryMovements().stream()
                .map(m -> new WarehouseSkuPair(m.warehouseId(), m.skuId()))
                .distinct()
                .collect(Collectors.toList());

        for (WarehouseSkuPair pair : pairs) {
            try {
                InventoryFeatures features = inventoryFeatureBuilder.computeFeatures(
                        pair.warehouseId(), pair.skuId(), bundle, asOfDate);

                double currentStock = features.currentStock() != null
                        ? features.currentStock().doubleValue() : 0.0;
                double rate7d = features.consumptionRate7d() != null
                        ? features.consumptionRate7d().doubleValue() : 0.0;
                boolean stockout = currentStock < rate7d * 7;

                Label label = new Label(stockout ? "STOCKOUT" : "NO_STOCKOUT");
                examples.add(new ArrayExample<>(label, buildStockoutFeatureVector(features)));
            } catch (Exception ex) {
                log.warn("[SyntheticFeatureStore] Skipping warehouse-sku ({},{}) for stockout: {}",
                        pair.warehouseId(), pair.skuId(), ex.getMessage());
            }
        }

        log.info("[SyntheticFeatureStore] Built {} stockout predictor examples", examples.size());
        return examples;
    }

    // ── Payment anomaly detector ───────────────────────────────────────────

    /**
     * Build anomaly detection examples for payment anomaly detection.
     *
     * <p>Each {@link SyntheticPayment} produces one example.
     * Payments flagged {@code isDefault=true} or {@code isPartial=true} produce
     * {@code ANOMALOUS} examples; others produce {@code EXPECTED} examples.
     *
     * @param bundle the synthetic dataset
     * @return list of Tribuo {@code Example<Event>} instances
     */
    public List<Example<Event>> buildPaymentAnomalyExamples(SyntheticDataBundle bundle) {
        List<Example<Event>> examples = new ArrayList<>();

        for (SyntheticPayment payment : bundle.getPayments()) {
            try {
                PaymentFeatures features = paymentFeatureBuilder.computePaymentFeatures(
                        payment, bundle);

                boolean isAnomalous = payment.isDefault() || payment.isPartial();
                examples.add(isAnomalous
                        ? anomalyFeatureBuilder.buildAnomalousPaymentExample(features)
                        : anomalyFeatureBuilder.buildPaymentExample(features));
            } catch (Exception ex) {
                log.warn("[SyntheticFeatureStore] Skipping payment {} for anomaly: {}",
                        payment.syntheticId(), ex.getMessage());
            }
        }

        log.info("[SyntheticFeatureStore] Built {} payment anomaly examples", examples.size());
        return examples;
    }

    // ── Payment distress classifier ────────────────────────────────────────

    /**
     * Build labelled classification examples for payment distress prediction.
     *
     * <p>One example per merchant. Label is {@code "DISTRESSED"} when
     * {@code latePaymentRate3m > 0.5} or {@code consecutiveMissedOrders > 2};
     * otherwise {@code "STABLE"}.
     *
     * @param bundle the synthetic dataset
     * @return list of Tribuo {@code Example<Label>} instances
     */
    public List<Example<Label>> buildPaymentDistressExamples(SyntheticDataBundle bundle) {
        LocalDateTime asOfDate = bundle.getGeneratedAt();
        List<Example<Label>> examples = new ArrayList<>();

        for (SyntheticMerchant merchant : bundle.getMerchants()) {
            try {
                MerchantPaymentTrendFeatures features =
                        paymentFeatureBuilder.computeMerchantTrendFeatures(merchant, bundle, asOfDate);

                boolean distressed = features.latePaymentRate3m() > 0.5
                        || features.consecutiveMissedOrders() > 2;
                Label label = new Label(distressed ? "DISTRESSED" : "STABLE");
                examples.add(new ArrayExample<>(label, buildPaymentDistressFeatureVector(features)));
            } catch (Exception ex) {
                log.warn("[SyntheticFeatureStore] Skipping merchant {} for payment distress: {}",
                        merchant.syntheticId(), ex.getMessage());
            }
        }

        log.info("[SyntheticFeatureStore] Built {} payment distress examples", examples.size());
        return examples;
    }

    // ── Rep performance predictor ──────────────────────────────────────────

    /**
     * Build labelled classification examples for sales rep underperformance detection.
     *
     * <p>One example per distinct sales rep ID. The feature window is the 30-day period
     * ending at {@code bundle.getGeneratedAt()}. Label is {@code "UNDERPERFORMING"} when
     * {@code orderConversionRate < 30.0} or {@code merchantRetentionRate < 50.0};
     * otherwise {@code "PERFORMING"}.
     *
     * @param bundle the synthetic dataset
     * @return list of Tribuo {@code Example<Label>} instances
     */
    public List<Example<Label>> buildRepPerformancePredictorExamples(SyntheticDataBundle bundle) {
        LocalDateTime asOfDate   = bundle.getGeneratedAt();
        LocalDateTime periodStart = asOfDate.minusDays(30);
        List<Example<Label>> examples = new ArrayList<>();

        List<UUID> repIds = bundle.getRepActivities().stream()
                .map(SyntheticRepActivity::salesRepId)
                .distinct()
                .collect(Collectors.toList());

        for (UUID repId : repIds) {
            try {
                SalesRepFeatures features = salesRepFeatureBuilder.computeFeatures(
                        repId, periodStart, asOfDate, bundle);

                boolean underperforming = features.orderConversionRate() < 30.0
                        || features.merchantRetentionRate() < 50.0;
                Label label = new Label(underperforming ? "UNDERPERFORMING" : "PERFORMING");
                examples.add(new ArrayExample<>(label, buildRepFeatureVector(features)));
            } catch (Exception ex) {
                log.warn("[SyntheticFeatureStore] Skipping rep {} for rep performance: {}",
                        repId, ex.getMessage());
            }
        }

        log.info("[SyntheticFeatureStore] Built {} rep performance examples", examples.size());
        return examples;
    }

    // ── Data quality detector ──────────────────────────────────────────────

    /**
     * Build labelled classification examples for data quality anomaly detection.
     *
     * <p>One example per merchant. Label is {@code "ANOMALOUS"} when
     * {@code cancellationRate > 0.8} or {@code avgDaysToPay > 180};
     * otherwise {@code "NORMAL"}.
     *
     * @param bundle the synthetic dataset
     * @return list of Tribuo {@code Example<Label>} instances
     */
    public List<Example<Label>> buildDataQualityExamples(SyntheticDataBundle bundle) {
        LocalDateTime asOfDate = bundle.getGeneratedAt();
        List<Example<Label>> examples = new ArrayList<>();

        for (SyntheticMerchant merchant : bundle.getMerchants()) {
            try {
                MerchantFeatures features = merchantFeatureBuilder.computeFeatures(
                        merchant, bundle, asOfDate);

                boolean anomalous = features.cancellationRate() > 0.8
                        || features.avgDaysToPay() > 180.0;
                Label label = new Label(anomalous ? "ANOMALOUS" : "NORMAL");
                examples.add(new ArrayExample<>(label, buildDataQualityFeatureVector(features)));
            } catch (Exception ex) {
                log.warn("[SyntheticFeatureStore] Skipping merchant {} for data quality: {}",
                        merchant.syntheticId(), ex.getMessage());
            }
        }

        log.info("[SyntheticFeatureStore] Built {} data quality examples", examples.size());
        return examples;
    }

    // ── Counts / diagnostics ───────────────────────────────────────────────

    /**
     * Return a summary count map of how many training examples each model would receive.
     *
     * <p>Does not materialise the full example lists — only counts merchants, payments,
     * distinct warehouse-SKU pairs, distinct merchant-SKU pairs, and distinct rep IDs.
     *
     * @param bundle the synthetic dataset
     * @return map of modelName → example count
     */
    public Map<String, Integer> summarizeExampleCounts(SyntheticDataBundle bundle) {
        long distinctWarehouseSkuPairs = bundle.getInventoryMovements().stream()
                .map(m -> new WarehouseSkuPair(m.warehouseId(), m.skuId()))
                .distinct()
                .count();

        long distinctMerchantSkuPairs = bundle.getOrders().stream()
                .flatMap(o -> bundle.getItemsForOrder(o.syntheticId()).stream()
                        .map(item -> new MerchantSkuPair(o.merchantRef(), item.skuId())))
                .distinct()
                .count();

        long distinctRepIds = bundle.getRepActivities().stream()
                .map(SyntheticRepActivity::salesRepId)
                .distinct()
                .count();

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(DataPhaseTracker.MODEL_CREDIT_CLASSIFIER,          bundle.getMerchants().size());
        counts.put(DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR,     bundle.getMerchants().size());
        counts.put(DataPhaseTracker.MODEL_DEMAND_FORECASTER,          (int) distinctMerchantSkuPairs);
        counts.put(DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR,         (int) distinctWarehouseSkuPairs);
        counts.put(DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR,         (int) distinctWarehouseSkuPairs);
        counts.put(DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR,   bundle.getPayments().size());
        counts.put(DataPhaseTracker.MODEL_PAYMENT_DISTRESS_CLASSIFIER, bundle.getMerchants().size());
        counts.put(DataPhaseTracker.MODEL_REP_PERFORMANCE_PREDICTOR,  (int) distinctRepIds);
        counts.put(DataPhaseTracker.MODEL_DATA_QUALITY_DETECTOR,      bundle.getMerchants().size());
        return Collections.unmodifiableMap(counts);
    }

    // ── Feature vector builders ────────────────────────────────────────────

    /**
     * Demand forecaster features: lag quantities (2w–4w), 12-week rolling average,
     * trend direction, temporal signals, and merchant context.
     */
    private List<Feature> buildDemandFeatureVector(DemandFeatures f) {
        List<Feature> list = new ArrayList<>();
        list.add(new Feature("qty2w_ago",          f.qty2wAgo().doubleValue()));
        list.add(new Feature("qty3w_ago",          f.qty3wAgo().doubleValue()));
        list.add(new Feature("qty4w_ago",          f.qty4wAgo().doubleValue()));
        list.add(new Feature("rolling_avg_12w",    f.rollingAvg12w().doubleValue()));
        list.add(new Feature("trend_direction",    encodeTrendDirection(f.trendDirection())));
        list.add(new Feature("day_of_week",        f.dayOfWeek()     != null ? f.dayOfWeek().doubleValue()     : 0.0));
        list.add(new Feature("week_of_month",      f.weekOfMonth()   != null ? f.weekOfMonth().doubleValue()   : 0.0));
        list.add(new Feature("month_of_year",      f.monthOfYear()   != null ? f.monthOfYear().doubleValue()   : 0.0));
        list.add(new Feature("is_holiday",         Boolean.TRUE.equals(f.isHoliday())     ? 1.0 : 0.0));
        list.add(new Feature("is_payday_week",     Boolean.TRUE.equals(f.isPaydayWeek())  ? 1.0 : 0.0));
        list.add(new Feature("merchant_tenure_days", f.merchantTenureDays() != null ? f.merchantTenureDays().doubleValue() : 0.0));
        return list;
    }

    /**
     * Stockout predictor features: stock levels, consumption rates, discrepancy,
     * and manual adjustment count.
     */
    private List<Feature> buildStockoutFeatureVector(InventoryFeatures f) {
        List<Feature> list = new ArrayList<>();
        list.add(new Feature("current_stock",       f.currentStock()       != null ? f.currentStock().doubleValue()       : 0.0));
        list.add(new Feature("expected_stock",      f.expectedStock()      != null ? f.expectedStock().doubleValue()      : 0.0));
        list.add(new Feature("discrepancy_pct",     f.discrepancyPct()     != null ? f.discrepancyPct()                  : 0.0));
        list.add(new Feature("consumption_rate_7d", f.consumptionRate7d()  != null ? f.consumptionRate7d().doubleValue()  : 0.0));
        list.add(new Feature("consumption_rate_30d",f.consumptionRate30d() != null ? f.consumptionRate30d().doubleValue() : 0.0));
        list.add(new Feature("consumption_trend",   encodeTrendDirection(f.consumptionTrend())));
        list.add(new Feature("manual_adj_count_7d", f.manualAdjustmentCount7d() != null ? f.manualAdjustmentCount7d().doubleValue() : 0.0));
        return list;
    }

    /**
     * Rep performance predictor features: visit conversion, order value, merchant
     * retention, collections, route adherence, and territory penetration.
     */
    private List<Feature> buildRepFeatureVector(SalesRepFeatures f) {
        List<Feature> list = new ArrayList<>();
        list.add(new Feature("visit_count",               f.visitCount()               != null ? f.visitCount().doubleValue()               : 0.0));
        list.add(new Feature("visit_count_vs_target",     f.visitCountVsTarget()       != null ? f.visitCountVsTarget()                     : 0.0));
        list.add(new Feature("orders_created",            f.ordersCreated()            != null ? f.ordersCreated().doubleValue()             : 0.0));
        list.add(new Feature("order_conversion_rate",     f.orderConversionRate()      != null ? f.orderConversionRate()                    : 0.0));
        list.add(new Feature("total_order_value",         f.totalOrderValue()          != null ? f.totalOrderValue().doubleValue()           : 0.0));
        list.add(new Feature("avg_order_value",           f.avgOrderValue()            != null ? f.avgOrderValue().doubleValue()             : 0.0));
        list.add(new Feature("active_merchants",          f.activeMerchants()          != null ? f.activeMerchants().doubleValue()           : 0.0));
        list.add(new Feature("merchant_retention_rate",   f.merchantRetentionRate()    != null ? f.merchantRetentionRate()                   : 0.0));
        list.add(new Feature("collection_rate",           f.collectionRate()           != null ? f.collectionRate()                         : 0.0));
        list.add(new Feature("route_adherence_pct",       f.routeAdherencePct()        != null ? f.routeAdherencePct()                      : 0.0));
        list.add(new Feature("territory_penetration_pct", f.territoryPenetrationPct()  != null ? f.territoryPenetrationPct()                 : 0.0));
        return list;
    }

    /**
     * Payment distress features: late payment rate, missed orders streak, credit
     * utilization, partial payment frequency, and order value trends.
     */
    private List<Feature> buildPaymentDistressFeatureVector(MerchantPaymentTrendFeatures f) {
        List<Feature> list = new ArrayList<>();
        list.add(new Feature("late_payment_rate_3m",       f.latePaymentRate3m()          != null ? f.latePaymentRate3m()          : 0.0));
        list.add(new Feature("late_payment_rate_trend_3m", f.latePaymentRateTrend3m()     != null ? f.latePaymentRateTrend3m()     : 0.0));
        list.add(new Feature("order_frequency_3m",         f.orderFrequency3m()           != null ? f.orderFrequency3m()           : 0.0));
        list.add(new Feature("consecutive_missed_orders",  f.consecutiveMissedOrders()    != null ? f.consecutiveMissedOrders().doubleValue() : 0.0));
        list.add(new Feature("credit_utilization_3m",      f.creditUtilization3m()        != null ? f.creditUtilization3m()        : 0.0));
        list.add(new Feature("partial_payment_freq_3m",    f.partialPaymentFreq3m()       != null ? f.partialPaymentFreq3m()       : 0.0));
        list.add(new Feature("consecutive_partial_pmts",   f.consecutivePartialPayments() != null ? f.consecutivePartialPayments().doubleValue() : 0.0));
        list.add(new Feature("avg_order_value_trend_3m",   f.avgOrderValueTrend3m()       != null ? f.avgOrderValueTrend3m()       : 0.0));
        list.add(new Feature("days_overdue_max",           f.daysOverdueMax()             != null ? f.daysOverdueMax().doubleValue() : 0.0));
        list.add(new Feature("payment_to_order_ratio_3m",  f.paymentToOrderRatio3m()      != null ? f.paymentToOrderRatio3m()      : 1.0));
        list.add(new Feature("days_to_pay_trend_3m",       f.daysToPayTrend3m()           != null ? f.daysToPayTrend3m()           : 0.0));
        list.add(new Feature("days_to_pay_stddev_3m",      f.daysToPayStddev3m()          != null ? f.daysToPayStddev3m()          : 0.0));
        return list;
    }

    /**
     * Data quality detector features: cancellation/return rates, order frequency,
     * payment timeliness, overdue amount, and credit utilization.
     */
    private List<Feature> buildDataQualityFeatureVector(MerchantFeatures f) {
        List<Feature> list = new ArrayList<>();
        list.add(new Feature("cancellation_rate",       f.cancellationRate()       != null ? f.cancellationRate()                    : 0.0));
        list.add(new Feature("avg_days_to_pay",         f.avgDaysToPay()           != null ? f.avgDaysToPay()                        : 0.0));
        list.add(new Feature("order_frequency_per_week",f.orderFrequencyPerWeek()  != null ? f.orderFrequencyPerWeek()               : 0.0));
        list.add(new Feature("return_rate",             f.returnRate()             != null ? f.returnRate()                          : 0.0));
        list.add(new Feature("total_overdue_amount",    f.totalOverdueAmount()     != null ? f.totalOverdueAmount().doubleValue()     : 0.0));
        list.add(new Feature("current_utilization",     f.currentUtilizationRatio()!= null ? f.currentUtilizationRatio()             : 0.0));
        list.add(new Feature("on_time_payment_pct",     f.onTimePaymentPct()       != null ? f.onTimePaymentPct()                    : 0.0));
        return list;
    }

    /** Encode trend direction string to a numeric value: INCREASING=1, DECREASING=-1, else=0. */
    private double encodeTrendDirection(String trend) {
        if (trend == null) return 0.0;
        return switch (trend) {
            case "INCREASING" ->  1.0;
            case "DECREASING" -> -1.0;
            default           ->  0.0;
        };
    }

    // ── Internal types ─────────────────────────────────────────────────────

    private record WarehouseSkuPair(UUID warehouseId, UUID skuId) {}

    private record MerchantSkuPair(UUID merchantId, UUID skuId) {}
}
