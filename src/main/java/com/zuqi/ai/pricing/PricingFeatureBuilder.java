package com.zuqi.ai.pricing;

import com.zuqi.ai.demand.TribuoFeatureConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.MutableDataset;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.RegressionFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds Tribuo ML feature vectors from PricingFeatures.
 *
 * 12 features → Example<Regressor> (target: order_quantity at given price)
 *
 * Blueprint: phase2-implementation_plan.md Section 6.1
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PricingFeatureBuilder {

    private final TribuoFeatureConverter converter;

    /**
     * Build a labeled training example.
     *
     * @param features     computed pricing features
     * @param targetQty    observed order quantity (training label)
     * @param observedPrice the price at which targetQty was observed
     */
    public Example<Regressor> buildTrainingExample(PricingFeatures features,
                                                    double targetQty,
                                                    double observedPrice) {
        List<Feature> featureList = buildFeatureList(features, observedPrice);
        Regressor target = new Regressor("qty", targetQty);
        return new ArrayExample<>(target,
                featureList.stream().map(Feature::getName).toArray(String[]::new),
                featureList.stream().mapToDouble(Feature::getValue).toArray());
    }

    /**
     * Build an inference example at a specific candidate price.
     */
    public Example<Regressor> buildInferenceExample(PricingFeatures features,
                                                      double candidatePrice) {
        List<Feature> featureList = buildFeatureList(features, candidatePrice);
        Regressor placeholder = new Regressor("qty", 0.0);
        return new ArrayExample<>(placeholder,
                featureList.stream().map(Feature::getName).toArray(String[]::new),
                featureList.stream().mapToDouble(Feature::getValue).toArray());
    }

    /**
     * Build a MutableDataset from a list of (features, observedQty, observedPrice) triples.
     */
    public MutableDataset<Regressor> buildDataset(
            List<PricingFeatures> featuresList,
            List<Double> targetQties,
            List<Double> observedPrices) {

        RegressionFactory factory = new RegressionFactory();
        MutableDataset<Regressor> dataset = new MutableDataset<>(
                new SimpleDataSourceProvenance("PricingTraining", factory), factory);

        for (int i = 0; i < featuresList.size(); i++) {
            dataset.add(buildTrainingExample(
                    featuresList.get(i), targetQties.get(i), observedPrices.get(i)));
        }
        return dataset;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<Feature> buildFeatureList(PricingFeatures f, double price) {
        List<Feature> list = new ArrayList<>();
        list.add(new Feature("price",                        price));
        list.add(new Feature("cost_price",                   f.costPrice()));
        list.add(new Feature("margin_pct",                   f.marginPct()));
        list.add(new Feature("price_change_pct_30d",         f.priceChangePct30d()));
        list.add(new Feature("demand_at_current_price",      f.demandAtCurrentPrice()));
        list.add(new Feature("demand_trend",                 f.demandTrend()));
        list.add(new Feature("inventory_days_of_supply",     f.inventoryDaysOfSupply()));
        list.add(new Feature("product_age_days",             f.productAgeDays()));
        list.add(new Feature("similar_product_avg_price",    f.similarProductAvgPrice()));
        list.add(new Feature("price_vs_market_ratio",
                f.similarProductAvgPrice() > 0 ? price / f.similarProductAvgPrice() : 1.0));
        list.add(new Feature("product_category_encoded",     f.productCategoryEncoded()));
        list.add(new Feature("price_tier_encoded",           f.priceTierEncoded()));
        return list;
    }
}
