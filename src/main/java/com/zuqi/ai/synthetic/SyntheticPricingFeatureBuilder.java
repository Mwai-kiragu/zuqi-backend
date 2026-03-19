package com.zuqi.ai.synthetic;

import com.zuqi.ai.pricing.PricingFeatureBuilder;
import com.zuqi.ai.pricing.PricingFeatures;
import com.zuqi.ai.synthetic.dto.SyntheticOrder;
import com.zuqi.ai.synthetic.dto.SyntheticOrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.regression.Regressor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds pricing training data from the synthetic bundle.
 *
 * Each (skuId, observedPrice, observedQty) triple becomes one training example.
 * Products with varied prices across orders produce rich price-demand curves.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SyntheticPricingFeatureBuilder {

    private final PricingFeatureBuilder pricingFeatureBuilder;

    /**
     * Builds a training dataset from all order items in the bundle.
     * Each unique (skuId, unitPrice) combination contributes one example.
     *
     * @param bundle           the synthetic data bundle
     * @param distributorId    the distributor context UUID (used as a placeholder)
     * @return Tribuo dataset ready for training
     */
    public MutableDataset<Regressor> buildDataset(SyntheticDataBundle bundle,
                                                   UUID distributorId) {
        List<PricingFeatures> featuresList = new ArrayList<>();
        List<Double> targetQties          = new ArrayList<>();
        List<Double> observedPrices       = new ArrayList<>();

        List<SyntheticOrderItem> items = bundle.getOrderItems();

        for (SyntheticOrderItem item : items) {
            if (item.skuId() == null || item.unitPrice() == null || item.quantity() == null) {
                continue;
            }

            double price = item.unitPrice().doubleValue();
            double qty   = item.quantity().doubleValue();
            if (price <= 0 || qty <= 0) continue;

            // Build a simple feature record from item data
            // Use price-based heuristics for features we can't derive from items alone
            double costEstimate = price * 0.65; // assume 35% margin
            double margin       = (price - costEstimate) / price * 100.0;
            int priceTier = price < 500 ? 0 : price < 2000 ? 1 : 2;

            // Avg weekly demand for this SKU from the bundle
            double skuWeeklyDemand = computeAvgWeeklyDemand(bundle, item.skuId());

            PricingFeatures features = new PricingFeatures(
                    item.skuId(),
                    distributorId,
                    price,
                    costEstimate,
                    margin,
                    0.0,                 // priceChangePct30d — not available in synthetic
                    skuWeeklyDemand,
                    0.0,                 // demandTrend — not available per-item
                    30.0,                // inventoryDaysOfSupply — synthetic default
                    180,                 // productAgeDays — synthetic default
                    price * 0.95,        // similarProductAvgPrice — ≈ own price
                    1,                   // categoryEncoded — placeholder
                    priceTier
            );

            featuresList.add(features);
            targetQties.add(qty);
            observedPrices.add(price);
        }

        log.info("[SyntheticPricingFeatureBuilder] Built {} training examples from {} order items",
                featuresList.size(), items.size());

        return pricingFeatureBuilder.buildDataset(featuresList, targetQties, observedPrices);
    }

    private double computeAvgWeeklyDemand(SyntheticDataBundle bundle, UUID skuId) {
        double totalQty = bundle.getOrderItems().stream()
                .filter(i -> skuId.equals(i.skuId()) && i.quantity() != null)
                .mapToDouble(i -> i.quantity().doubleValue())
                .sum();
        // Assume bundle covers ~13 weeks
        return totalQty / 13.0;
    }
}
