package com.zuqi.ai.anomaly;

import com.zuqi.ai.event.OrderCreatedEvent;
import com.zuqi.domain.order.Order;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tribuo.Feature;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds Tribuo ML feature vectors for Tier-2 data quality detection.
 *
 * 14 features span the order event itself (structural) and merchant
 * history (contextual). This enables the XGBoost classifier to catch
 * anomalies that evade the Tier-1 rules engine — e.g. prices that are
 * technically > 0 but 10× above the merchant's typical range.
 *
 * Feature groups:
 *   Structural (8)  — derived from the OrderCreatedEvent alone, zero DB calls
 *   Contextual (6)  — require merchant order history and product catalog
 *
 * Blueprint reference: plan.md Section 6.3 - DataQualityDetector Tier-2
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataQualityFeatureBuilder {

    static final Label NORMAL    = new Label("NORMAL");
    static final Label ANOMALOUS = new Label("ANOMALOUS");

    private static final LabelFactory LABEL_FACTORY = new LabelFactory();

    // Feature name constants (14 total)
    static final String F_ITEM_COUNT            = "item_count";
    static final String F_TOTAL_AMOUNT_LOG      = "total_amount_log1p";
    static final String F_MAX_ITEM_QTY          = "max_item_qty";
    static final String F_HAS_ZERO_PRICE        = "has_zero_price_item";
    static final String F_MAX_ITEM_VALUE_PCT    = "max_item_value_pct";
    static final String F_PRICE_CV              = "price_coefficient_of_variation";
    static final String F_ORDER_TYPE            = "order_type_encoded";
    static final String F_HOUR_OF_DAY           = "hour_of_day";
    // --- contextual features ---
    static final String F_VALUE_VS_AVG          = "order_value_vs_merchant_avg";
    static final String F_VALUE_Z_SCORE         = "order_value_z_score";
    static final String F_DAYS_SINCE_LAST       = "days_since_last_order";
    static final String F_FREQ_RATIO            = "order_frequency_ratio";
    static final String F_ITEM_COUNT_VS_AVG     = "item_count_vs_merchant_avg";
    static final String F_PRICE_CONSISTENCY     = "price_consistency_score";

    private final OrderRepository   orderRepository;
    private final ProductRepository productRepository;

    // ── Public API ────────────────────────────────────────────────────────

    /** Build a labelled NORMAL example (for training on known-good orders). */
    public ArrayExample<Label> buildNormalExample(OrderCreatedEvent event) {
        return new ArrayExample<>(NORMAL, buildFeatures(event));
    }

    /** Build a labelled ANOMALOUS example (for training on injected bad orders). */
    public ArrayExample<Label> buildAnomalousExample(OrderCreatedEvent event) {
        return new ArrayExample<>(ANOMALOUS, buildFeatures(event));
    }

    /** Build an unlabelled example for inference (label not used). */
    public ArrayExample<Label> buildInferenceExample(OrderCreatedEvent event) {
        return new ArrayExample<>(NORMAL, buildFeatures(event));
    }

    /**
     * Build a labelled training dataset.
     *
     * @param normalOrders   Known-good order events (label = NORMAL)
     * @param anomalousOrders Injected bad order events (label = ANOMALOUS)
     */
    public MutableDataset<Label> buildTrainingDataset(List<OrderCreatedEvent> normalOrders,
                                                       List<OrderCreatedEvent> anomalousOrders) {
        SimpleDataSourceProvenance prov = new SimpleDataSourceProvenance(
                "data_quality_training", LABEL_FACTORY);
        MutableDataset<Label> dataset = new MutableDataset<>(prov, LABEL_FACTORY);

        for (OrderCreatedEvent e : normalOrders)    dataset.add(buildNormalExample(e));
        for (OrderCreatedEvent e : anomalousOrders) dataset.add(buildAnomalousExample(e));

        log.info("Built data quality training dataset: {} normal, {} anomalous",
                normalOrders.size(), anomalousOrders.size());
        return dataset;
    }

    // ── Feature computation ───────────────────────────────────────────────

    List<Feature> buildFeatures(OrderCreatedEvent event) {
        List<Feature> features = new ArrayList<>();

        // ── Structural features (no DB) ────────────────────────────────────
        int itemCount = event.items() != null ? event.items().size() : 0;
        features.add(new Feature(F_ITEM_COUNT, itemCount));

        double totalAmountLog = event.totalAmount() != null
                ? Math.log1p(event.totalAmount().doubleValue()) : 0.0;
        features.add(new Feature(F_TOTAL_AMOUNT_LOG, totalAmountLog));

        int maxQty = event.items() != null
                ? event.items().stream().mapToInt(i -> i.quantity() != null ? i.quantity() : 0).max().orElse(0) : 0;
        features.add(new Feature(F_MAX_ITEM_QTY, maxQty));

        boolean hasZeroPrice = event.items() != null && event.items().stream()
                .anyMatch(i -> i.unitPrice() == null || i.unitPrice().compareTo(BigDecimal.ZERO) <= 0);
        features.add(new Feature(F_HAS_ZERO_PRICE, hasZeroPrice ? 1.0 : 0.0));

        features.add(new Feature(F_MAX_ITEM_VALUE_PCT, computeMaxItemValuePct(event)));
        features.add(new Feature(F_PRICE_CV, computePriceCoefficientOfVariation(event)));
        features.add(new Feature(F_ORDER_TYPE, encodeOrderType(event.orderType())));

        double hourOfDay = event.createdAt() != null ? event.createdAt().getHour() : 12.0;
        features.add(new Feature(F_HOUR_OF_DAY, hourOfDay));

        // ── Contextual features (requires DB) ─────────────────────────────
        List<Order> recentOrders = fetchRecentOrders(event.merchantId(), event.createdAt());
        features.addAll(buildContextualFeatures(event, recentOrders));

        return features;
    }

    private List<Feature> buildContextualFeatures(OrderCreatedEvent event, List<Order> history) {
        List<Feature> features = new ArrayList<>();
        double total = event.totalAmount() != null ? event.totalAmount().doubleValue() : 0.0;

        if (history.isEmpty()) {
            // First order — use neutral defaults
            features.add(new Feature(F_VALUE_VS_AVG,      1.0));
            features.add(new Feature(F_VALUE_Z_SCORE,     0.0));
            features.add(new Feature(F_DAYS_SINCE_LAST,   0.0));
            features.add(new Feature(F_FREQ_RATIO,        1.0));
            features.add(new Feature(F_ITEM_COUNT_VS_AVG, 1.0));
            features.add(new Feature(F_PRICE_CONSISTENCY, 1.0));
            return features;
        }

        // Order value statistics over merchant history
        double[] amounts = history.stream()
                .filter(o -> o.getTotalAmount() != null)
                .mapToDouble(o -> o.getTotalAmount().doubleValue())
                .toArray();
        double mean   = mean(amounts);
        double stddev = stddev(amounts, mean);

        features.add(new Feature(F_VALUE_VS_AVG,  mean > 0 ? total / mean : 1.0));
        features.add(new Feature(F_VALUE_Z_SCORE, stddev > 0 ? (total - mean) / stddev : 0.0));

        // Days since last order
        Order mostRecent = history.get(0); // history is DESC by createdAt
        long daysSinceLast = mostRecent.getCreatedAt() != null && event.createdAt() != null
                ? ChronoUnit.DAYS.between(mostRecent.getCreatedAt(), event.createdAt()) : 0;
        features.add(new Feature(F_DAYS_SINCE_LAST, (double) Math.max(0, daysSinceLast)));

        // Order frequency ratio
        double avgFreqDays = computeAvgOrderFrequencyDays(history);
        features.add(new Feature(F_FREQ_RATIO, avgFreqDays > 0 ? daysSinceLast / avgFreqDays : 1.0));

        // Item count vs merchant average
        double avgItemCount = history.stream()
                .filter(o -> o.getItems() != null)
                .mapToDouble(o -> (double) o.getItems().size())
                .average().orElse(1.0);
        features.add(new Feature(F_ITEM_COUNT_VS_AVG,
                avgItemCount > 0 ? (event.items() != null ? event.items().size() : 0) / avgItemCount : 1.0));

        // Price consistency — fraction of items whose unit price is within 2× of catalog price
        features.add(new Feature(F_PRICE_CONSISTENCY, computePriceConsistencyScore(event)));

        return features;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private double computeMaxItemValuePct(OrderCreatedEvent event) {
        if (event.items() == null || event.items().isEmpty()) return 0.0;
        if (event.totalAmount() == null || event.totalAmount().compareTo(BigDecimal.ZERO) == 0) return 0.0;
        double maxItemValue = event.items().stream()
                .filter(i -> i.unitPrice() != null && i.quantity() != null)
                .mapToDouble(i -> i.unitPrice().doubleValue() * i.quantity())
                .max().orElse(0.0);
        return maxItemValue / event.totalAmount().doubleValue();
    }

    private double computePriceCoefficientOfVariation(OrderCreatedEvent event) {
        if (event.items() == null || event.items().size() < 2) return 0.0;
        double[] prices = event.items().stream()
                .filter(i -> i.unitPrice() != null && i.unitPrice().compareTo(BigDecimal.ZERO) > 0)
                .mapToDouble(i -> i.unitPrice().doubleValue())
                .toArray();
        if (prices.length < 2) return 0.0;
        double m = mean(prices);
        if (m == 0) return 0.0;
        return stddev(prices, m) / m;
    }

    private double encodeOrderType(String type) {
        if (type == null) return 0.0;
        return switch (type) {
            case "CREDIT" -> 1.0;
            case "SAMPLE" -> 2.0;
            default       -> 0.0;  // REGULAR
        };
    }

    /**
     * Fraction of order items whose unit price is within 2× of the catalog price.
     * Items whose product is not in the catalog are skipped (treated as consistent).
     */
    private double computePriceConsistencyScore(OrderCreatedEvent event) {
        if (event.items() == null || event.items().isEmpty()) return 1.0;
        int checked = 0;
        int consistent = 0;
        for (OrderCreatedEvent.OrderItem item : event.items()) {
            if (item.productId() == null || item.unitPrice() == null) continue;
            var productOpt = productRepository.findById(item.productId());
            if (productOpt.isEmpty()) continue;
            BigDecimal catalogPrice = productOpt.get().getUnitPrice();
            if (catalogPrice == null || catalogPrice.compareTo(BigDecimal.ZERO) <= 0) continue;
            checked++;
            double ratio = item.unitPrice().divide(catalogPrice, 6, RoundingMode.HALF_UP).doubleValue();
            if (ratio >= 0.5 && ratio <= 2.0) consistent++;
        }
        return checked == 0 ? 1.0 : (double) consistent / checked;
    }

    private List<Order> fetchRecentOrders(UUID merchantId, LocalDateTime before) {
        try {
            LocalDateTime cutoff = before != null ? before : LocalDateTime.now();
            List<Order> all = orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, cutoff);
            // Keep last 90 orders for statistics
            return all.size() <= 90 ? all : all.subList(0, 90);
        } catch (Exception e) {
            log.warn("Failed to fetch merchant order history for {}: {}", merchantId, e.getMessage());
            return List.of();
        }
    }

    private double computeAvgOrderFrequencyDays(List<Order> history) {
        if (history.size() < 2) return 7.0; // assume weekly if only one order
        long totalDays = 0;
        int gaps = 0;
        for (int i = 0; i < history.size() - 1; i++) {
            LocalDateTime a = history.get(i).getCreatedAt();
            LocalDateTime b = history.get(i + 1).getCreatedAt();
            if (a != null && b != null) {
                long gap = ChronoUnit.DAYS.between(b, a); // DESC list
                if (gap > 0) { totalDays += gap; gaps++; }
            }
        }
        return gaps > 0 ? (double) totalDays / gaps : 7.0;
    }

    private double mean(double[] values) {
        if (values.length == 0) return 0.0;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private double stddev(double[] values, double mean) {
        if (values.length < 2) return 0.0;
        double variance = 0;
        for (double v : values) variance += (v - mean) * (v - mean);
        return Math.sqrt(variance / values.length);
    }

    /** For tests — builds features from a synthetic event with a custom history map. */
    List<Feature> buildFeaturesForTest(OrderCreatedEvent event,
                                        Map<String, Double> historyStats,
                                        double daysSinceLast,
                                        double avgFreqDays,
                                        double avgItemCount,
                                        double priceConsistency) {
        List<Feature> features = new ArrayList<>();

        int itemCount = event.items() != null ? event.items().size() : 0;
        features.add(new Feature(F_ITEM_COUNT, itemCount));
        double totalAmountLog = event.totalAmount() != null
                ? Math.log1p(event.totalAmount().doubleValue()) : 0.0;
        features.add(new Feature(F_TOTAL_AMOUNT_LOG, totalAmountLog));
        int maxQty = event.items() != null
                ? event.items().stream().mapToInt(i -> i.quantity() != null ? i.quantity() : 0).max().orElse(0) : 0;
        features.add(new Feature(F_MAX_ITEM_QTY, maxQty));
        boolean hasZeroPrice = event.items() != null && event.items().stream()
                .anyMatch(i -> i.unitPrice() == null || i.unitPrice().compareTo(BigDecimal.ZERO) <= 0);
        features.add(new Feature(F_HAS_ZERO_PRICE, hasZeroPrice ? 1.0 : 0.0));
        features.add(new Feature(F_MAX_ITEM_VALUE_PCT, computeMaxItemValuePct(event)));
        features.add(new Feature(F_PRICE_CV, computePriceCoefficientOfVariation(event)));
        features.add(new Feature(F_ORDER_TYPE, encodeOrderType(event.orderType())));
        features.add(new Feature(F_HOUR_OF_DAY, event.createdAt() != null ? event.createdAt().getHour() : 12.0));

        double mean   = historyStats.getOrDefault("mean",   0.0);
        double stddev = historyStats.getOrDefault("stddev", 0.0);
        double total  = event.totalAmount() != null ? event.totalAmount().doubleValue() : 0.0;
        features.add(new Feature(F_VALUE_VS_AVG,      mean > 0 ? total / mean : 1.0));
        features.add(new Feature(F_VALUE_Z_SCORE,     stddev > 0 ? (total - mean) / stddev : 0.0));
        features.add(new Feature(F_DAYS_SINCE_LAST,   daysSinceLast));
        features.add(new Feature(F_FREQ_RATIO,        avgFreqDays > 0 ? daysSinceLast / avgFreqDays : 1.0));
        features.add(new Feature(F_ITEM_COUNT_VS_AVG, avgItemCount > 0 ? itemCount / avgItemCount : 1.0));
        features.add(new Feature(F_PRICE_CONSISTENCY, priceConsistency));

        return features;
    }
}
