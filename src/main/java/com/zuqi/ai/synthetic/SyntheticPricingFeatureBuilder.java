package com.zuqi.ai.synthetic;

import com.zuqi.ai.pricing.PricingFeatureBuilder;
import com.zuqi.ai.pricing.PricingFeatures;
import com.zuqi.ai.synthetic.dto.SyntheticOrder;
import com.zuqi.ai.synthetic.dto.SyntheticOrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tribuo.MutableDataset;
import org.tribuo.regression.Regressor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds pricing training data from the synthetic bundle.
 *
 * Each (skuId, observedPrice, observedQty) triple becomes one training example.
 * Per-SKU price variation and demand trend are computed from the full order history
 * to provide real elasticity signals (rather than constants).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SyntheticPricingFeatureBuilder {

    private final PricingFeatureBuilder pricingFeatureBuilder;

    /**
     * Builds a training dataset from all order items in the bundle.
     *
     * @param bundle        the synthetic data bundle
     * @param distributorId the distributor context UUID
     * @return Tribuo dataset ready for training
     */
    public MutableDataset<Regressor> buildDataset(SyntheticDataBundle bundle,
                                                   UUID distributorId) {
        // Build orderRef → orderDate lookup
        Map<UUID, LocalDateTime> orderDates = new HashMap<>();
        for (SyntheticOrder o : bundle.getOrders()) {
            if (o.orderDate() != null) orderDates.put(o.syntheticId(), o.orderDate());
        }

        // Group items by SKU — collect (price, qty, date) observations
        Map<UUID, List<SkuObservation>> bySkuId = new HashMap<>();
        for (SyntheticOrderItem item : bundle.getOrderItems()) {
            if (item.skuId() == null || item.unitPrice() == null || item.quantity() == null) continue;
            double price = item.unitPrice().doubleValue();
            double qty   = item.quantity().doubleValue();
            if (price <= 0 || qty <= 0) continue;
            LocalDateTime date = orderDates.get(item.orderRef());
            bySkuId.computeIfAbsent(item.skuId(), k -> new ArrayList<>())
                    .add(new SkuObservation(item.orderRef(), price, qty, date));
        }

        // Pre-compute per-SKU stats
        Map<UUID, Double> skuMeanPrice    = new HashMap<>();
        Map<UUID, Double> skuWeeklyDemand = new HashMap<>();
        Map<UUID, Double> skuDemandTrend  = new HashMap<>();

        for (Map.Entry<UUID, List<SkuObservation>> entry : bySkuId.entrySet()) {
            UUID skuId = entry.getKey();
            List<SkuObservation> obs = entry.getValue();

            double meanPrice = obs.stream().mapToDouble(o -> o.price).average().orElse(0.0);
            skuMeanPrice.put(skuId, meanPrice);

            double totalQty = obs.stream().mapToDouble(o -> o.qty).sum();
            skuWeeklyDemand.put(skuId, totalQty / 13.0); // bundle ≈ 13 weeks

            // Demand trend: compare average qty in first half vs second half (sorted by date)
            List<SkuObservation> dated = obs.stream()
                    .filter(o -> o.date != null)
                    .sorted(Comparator.comparing(o -> o.date))
                    .toList();
            if (dated.size() >= 4) {
                int mid = dated.size() / 2;
                double firstHalfAvg = dated.subList(0, mid).stream()
                        .mapToDouble(o -> o.qty).average().orElse(0.0);
                double secondHalfAvg = dated.subList(mid, dated.size()).stream()
                        .mapToDouble(o -> o.qty).average().orElse(0.0);
                double trend = firstHalfAvg > 0
                        ? (secondHalfAvg - firstHalfAvg) / firstHalfAvg * 100.0
                        : 0.0;
                skuDemandTrend.put(skuId, trend);
            } else {
                skuDemandTrend.put(skuId, 0.0);
            }
        }

        // Build training examples
        List<PricingFeatures> featuresList = new ArrayList<>();
        List<Double> targetQties    = new ArrayList<>();
        List<Double> observedPrices = new ArrayList<>();

        for (Map.Entry<UUID, List<SkuObservation>> entry : bySkuId.entrySet()) {
            UUID skuId    = entry.getKey();
            double mean   = skuMeanPrice.getOrDefault(skuId, 0.0);
            double weekly = skuWeeklyDemand.getOrDefault(skuId, 0.0);
            double trend  = skuDemandTrend.getOrDefault(skuId, 0.0);

            for (SkuObservation obs : entry.getValue()) {
                double price     = obs.price;
                double costEstimate = price * 0.65;
                double margin    = (price - costEstimate) / price * 100.0;
                int priceTier    = price < 500 ? 0 : price < 2000 ? 1 : 2;
                // priceChangePct30d: how much this observation's price deviates from the SKU mean
                double priceChangePct30d = mean > 0 ? (price - mean) / mean * 100.0 : 0.0;

                PricingFeatures features = new PricingFeatures(
                        skuId,
                        distributorId,
                        price,
                        costEstimate,
                        margin,
                        priceChangePct30d,
                        weekly,
                        trend,
                        30.0,
                        180,
                        mean * 0.95,
                        1,
                        priceTier
                );

                featuresList.add(features);
                targetQties.add(obs.qty);
                observedPrices.add(price);
            }
        }

        log.info("[SyntheticPricingFeatureBuilder] Built {} training examples from {} SKUs",
                featuresList.size(), bySkuId.size());

        return pricingFeatureBuilder.buildDataset(featuresList, targetQties, observedPrices);
    }

    private record SkuObservation(UUID orderRef, double price, double qty, LocalDateTime date) {}
}
