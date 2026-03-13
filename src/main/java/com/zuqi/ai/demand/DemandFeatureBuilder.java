package com.zuqi.ai.demand;

import com.zuqi.ai.feature.DemandFeatures;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds Tribuo ML feature vectors from demand features.
 *
 * Transforms DemandFeatures → Tribuo Example for XGBoost demand forecasting.
 *
 * Features:
 * - 7 lag features (historical quantities)
 * - 7 temporal features (calendar effects)
 * - 4 merchant context features
 * - 4 SKU context features
 * Total: ~22 numeric + categorical encoded = ~35-40 features
 *
 * Blueprint: plan.md Section 6.2 - Demand Forecasting Module
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemandFeatureBuilder {

    private final TribuoFeatureConverter converter;

    // All possible merchant categories
    private static final List<String> ALL_MERCHANT_CATEGORIES = List.of(
            "Hardware Store", "General Store", "Supermarket", "Kiosk",
            "Grocery", "Building Materials", "Pharmacy", "Electronics",
            "Clothing", "Restaurant"
    );

    // All possible product categories
    private static final List<String> ALL_PRODUCT_CATEGORIES = List.of(
            "Beverages", "Snacks", "Household", "Personal Care",
            "Cleaning", "Hardware", "Stationery", "Groceries",
            "Dairy", "Frozen Foods"
    );

    // Size tiers
    private static final List<String> SIZE_TIERS = List.of("SMALL", "MEDIUM", "LARGE");

    // Credit status
    private static final List<String> CREDIT_STATUSES = List.of("GOOD", "MODERATE", "POOR");

    // Price tiers
    private static final List<String> PRICE_TIERS = List.of("LOW", "MEDIUM", "HIGH");

    // Trend directions
    private static final List<String> TREND_DIRECTIONS = List.of("INCREASING", "STABLE", "DECREASING");

    /**
     * Build Tribuo regression example for demand forecasting.
     *
     * @param features Demand features for merchant-SKU
     * @param actualQuantity Actual quantity ordered (training target)
     * @return Tribuo Example with Regressor
     */
    public Example<Regressor> buildRegressionExample(DemandFeatures features,
                                                      BigDecimal actualQuantity) {
        List<Feature> tribuoFeatures = buildFeatureVector(features);

        // Create regressor (single output dimension: predicted quantity)
        Regressor regressor = new Regressor("predicted_quantity",
                actualQuantity.doubleValue());

        return new ArrayExample<>(regressor, tribuoFeatures);
    }

    /**
     * Build complete feature vector from demand features.
     */
    private List<Feature> buildFeatureVector(DemandFeatures features) {
        List<Feature> featureList = new ArrayList<>();

        // ========== Lag Features (7) ==========
        featureList.add(new Feature("qty_1w_ago", converter.safeDouble(features.qty1wAgo())));
        featureList.add(new Feature("qty_2w_ago", converter.safeDouble(features.qty2wAgo())));
        featureList.add(new Feature("qty_3w_ago", converter.safeDouble(features.qty3wAgo())));
        featureList.add(new Feature("qty_4w_ago", converter.safeDouble(features.qty4wAgo())));
        featureList.add(new Feature("rolling_avg_4w", converter.safeDouble(features.rollingAvg4w())));
        featureList.add(new Feature("rolling_avg_12w", converter.safeDouble(features.rollingAvg12w())));

        // Trend direction (encoded as numeric: INCREASING=1, STABLE=0, DECREASING=-1)
        double trendValue = converter.encodeTrend(features.trendDirection());
        featureList.add(new Feature("trend_direction_numeric", trendValue));

        // ========== Temporal Features (7 numeric + boolean flags) ==========
        featureList.add(new Feature("day_of_week", features.dayOfWeek().doubleValue()));
        featureList.add(new Feature("week_of_month", features.weekOfMonth().doubleValue()));
        featureList.add(new Feature("month_of_year", features.monthOfYear().doubleValue()));
        featureList.add(new Feature("is_holiday", features.isHoliday() ? 1.0 : 0.0));
        featureList.add(new Feature("is_payday_week", features.isPaydayWeek() ? 1.0 : 0.0));
        featureList.add(new Feature("is_ramadan", features.isRamadan() ? 1.0 : 0.0));
        featureList.add(new Feature("is_christmas_season", features.isChristmasSeason() ? 1.0 : 0.0));

        // ========== Merchant Context Features (1 numeric + categorical) ==========
        featureList.add(new Feature("merchant_tenure_days", features.merchantTenureDays().doubleValue()));

        // Merchant category (one-hot encoded)
        String merchantCategory = features.merchantCategory();
        for (String category : ALL_MERCHANT_CATEGORIES) {
            String featureName = "merchant_category_" + converter.sanitizeFeatureName(category);
            double value = (merchantCategory != null && merchantCategory.equals(category)) ? 1.0 : 0.0;
            featureList.add(new Feature(featureName, value));
        }

        // Merchant size tier (one-hot encoded)
        String sizeTier = features.merchantSizeTier();
        for (String tier : SIZE_TIERS) {
            String featureName = "size_tier_" + converter.sanitizeFeatureName(tier);
            double value = (sizeTier != null && sizeTier.equals(tier)) ? 1.0 : 0.0;
            featureList.add(new Feature(featureName, value));
        }

        // Merchant credit status (one-hot encoded)
        String creditStatus = features.merchantCreditStatus();
        for (String status : CREDIT_STATUSES) {
            String featureName = "credit_status_" + converter.sanitizeFeatureName(status);
            double value = (creditStatus != null && creditStatus.equals(status)) ? 1.0 : 0.0;
            featureList.add(new Feature(featureName, value));
        }

        // ========== SKU Context Features (1 numeric + categorical) ==========
        Integer shelfLife = features.typicalShelfLifeDays();
        featureList.add(new Feature("typical_shelf_life_days",
                shelfLife != null ? shelfLife.doubleValue() : 0.0));

        // Product category (one-hot encoded)
        String productCategory = features.productCategory();
        for (String category : ALL_PRODUCT_CATEGORIES) {
            String featureName = "product_category_" + converter.sanitizeFeatureName(category);
            double value = (productCategory != null && productCategory.equals(category)) ? 1.0 : 0.0;
            featureList.add(new Feature(featureName, value));
        }

        // Price tier (one-hot encoded)
        String priceTier = features.priceTier();
        for (String tier : PRICE_TIERS) {
            String featureName = "price_tier_" + converter.sanitizeFeatureName(tier);
            double value = (priceTier != null && priceTier.equals(tier)) ? 1.0 : 0.0;
            featureList.add(new Feature(featureName, value));
        }

        // Promotional status
        featureList.add(new Feature("is_promotional", features.isPromotional() ? 1.0 : 0.0));

        return featureList;
    }

    /**
     * Build a complete Tribuo dataset for demand forecasting training.
     *
     * @param features List of demand features
     * @param actualQuantities List of actual quantities ordered
     * @return MutableDataset ready for XGBoost training
     */
    public MutableDataset<Regressor> buildRegressionDataset(
            List<DemandFeatures> features,
            List<BigDecimal> actualQuantities) {

        if (features.size() != actualQuantities.size()) {
            throw new IllegalArgumentException("Features and quantities must have same size");
        }

        // Create dataset with DataProvenance
        SimpleDataSourceProvenance provenance = new SimpleDataSourceProvenance(
                "Demand forecasting training data",
                new RegressionFactory()
        );
        MutableDataset<Regressor> dataset = new MutableDataset<>(provenance, new RegressionFactory());

        for (int i = 0; i < features.size(); i++) {
            Example<Regressor> example = buildRegressionExample(features.get(i), actualQuantities.get(i));
            dataset.add(example);
        }

        log.info("Built demand forecasting dataset with {} examples", dataset.size());
        return dataset;
    }

    /**
     * Get feature names in order (for model interpretation).
     *
     * @return List of all feature names
     */
    public List<String> getFeatureNames() {
        List<String> names = new ArrayList<>();

        // Lag features
        names.addAll(List.of(
                "qty_1w_ago", "qty_2w_ago", "qty_3w_ago", "qty_4w_ago",
                "rolling_avg_4w", "rolling_avg_12w", "trend_direction_numeric"
        ));

        // Temporal features
        names.addAll(List.of(
                "day_of_week", "week_of_month", "month_of_year",
                "is_holiday", "is_payday_week", "is_ramadan", "is_christmas_season"
        ));

        // Merchant context
        names.add("merchant_tenure_days");
        for (String category : ALL_MERCHANT_CATEGORIES) {
            names.add("merchant_category_" + converter.sanitizeFeatureName(category));
        }
        for (String tier : SIZE_TIERS) {
            names.add("size_tier_" + converter.sanitizeFeatureName(tier));
        }
        for (String status : CREDIT_STATUSES) {
            names.add("credit_status_" + converter.sanitizeFeatureName(status));
        }

        // SKU context
        names.add("typical_shelf_life_days");
        for (String category : ALL_PRODUCT_CATEGORIES) {
            names.add("product_category_" + converter.sanitizeFeatureName(category));
        }
        for (String tier : PRICE_TIERS) {
            names.add("price_tier_" + converter.sanitizeFeatureName(tier));
        }
        names.add("is_promotional");

        return names;
    }

    /**
     * Get total feature count.
     *
     * @return Total number of features
     */
    public int getFeatureCount() {
        return 7 + 7 + 1 + // Lag + Temporal + Merchant tenure
                ALL_MERCHANT_CATEGORIES.size() + SIZE_TIERS.size() + CREDIT_STATUSES.size() + // Merchant categories
                1 + ALL_PRODUCT_CATEGORIES.size() + PRICE_TIERS.size() + 1; // SKU features
    }

}
