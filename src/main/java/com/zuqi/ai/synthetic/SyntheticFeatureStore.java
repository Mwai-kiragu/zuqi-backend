package com.zuqi.ai.synthetic;

import com.zuqi.ai.anomaly.AnomalyFeatureBuilder;
import com.zuqi.ai.credit.CreditMlFeatureBuilder;
import com.zuqi.ai.feature.InventoryFeatures;
import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.feature.PaymentFeatures;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.anomaly.Event;
import org.tribuo.classification.Label;
import org.tribuo.regression.Regressor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
 *       so that the vector layout is identical between synthetic and real training runs.</li>
 * </ol>
 *
 * <h3>Supported models</h3>
 * <ul>
 *   <li>{@link DataPhaseTracker#MODEL_CREDIT_CLASSIFIER} → {@code Example<Label>}</li>
 *   <li>{@link DataPhaseTracker#MODEL_CREDIT_LIMIT_REGRESSOR} → {@code Example<Regressor>}</li>
 *   <li>{@link DataPhaseTracker#MODEL_SHRINKAGE_DETECTOR} → {@code Example<Event>}</li>
 *   <li>{@link DataPhaseTracker#MODEL_PAYMENT_ANOMALY_DETECTOR} → {@code Example<Event>}</li>
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

    // ── Counts / diagnostics ───────────────────────────────────────────────

    /**
     * Return a summary count map of how many training examples each model would receive.
     *
     * <p>Does not materialise the full example lists — only counts merchants, payments,
     * and distinct warehouse-SKU pairs.
     *
     * @param bundle the synthetic dataset
     * @return map of modelName → example count
     */
    public java.util.Map<String, Integer> summarizeExampleCounts(SyntheticDataBundle bundle) {
        long distinctWarehouseSkuPairs = bundle.getInventoryMovements().stream()
                .map(m -> new WarehouseSkuPair(m.warehouseId(), m.skuId()))
                .distinct()
                .count();

        return java.util.Map.of(
                DataPhaseTracker.MODEL_CREDIT_CLASSIFIER,       bundle.getMerchants().size(),
                DataPhaseTracker.MODEL_CREDIT_LIMIT_REGRESSOR,  bundle.getMerchants().size(),
                DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR,      (int) distinctWarehouseSkuPairs,
                DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR, bundle.getPayments().size()
        );
    }

    // ── Internal types ─────────────────────────────────────────────────────

    private record WarehouseSkuPair(UUID warehouseId, UUID skuId) {}
}
