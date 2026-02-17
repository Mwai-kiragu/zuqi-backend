package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.DatasetProvenance;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.RegressionFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds Tribuo ML feature vectors from merchant features.
 *
 * Transforms MerchantFeatures → Tribuo Example for XGBoost training.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 2
 */
@Service
@Slf4j
public class CreditMlFeatureBuilder {

    // Feature normalization statistics (computed from training data)
    // In production, these would be computed during training and stored
    private final Map<String, FeatureStats> featureStats = new HashMap<>();

    // All possible business categories for one-hot encoding
    private static final List<String> ALL_CATEGORIES = List.of(
            "Hardware Store", "General Store", "Supermarket", "Kiosk",
            "Grocery", "Building Materials", "Pharmacy", "Electronics",
            "Clothing", "Restaurant"
    );

    // All possible cities for one-hot encoding
    private static final List<String> ALL_CITIES = List.of(
            "Nairobi", "Mombasa", "Kisumu", "Nakuru", "Eldoret",
            "Thika", "Ruiru", "Machakos", "Nyeri", "Meru"
    );

    // Verification statuses for one-hot encoding
    private static final List<String> VERIFICATION_STATUSES = List.of(
            "VERIFIED", "PENDING", "UNVERIFIED"
    );

    /**
     * Build Tribuo classification example (for default prediction).
     *
     * @param features Merchant features
     * @param didDefault True if merchant defaulted
     * @return Tribuo Example with Label
     */
    public Example<Label> buildClassificationExample(MerchantFeatures features, boolean didDefault) {
        List<Feature> tribuoFeatures = buildFeatureVector(features);

        // Create label
        Label label = new Label(didDefault ? "DEFAULT" : "NO_DEFAULT");

        return new ArrayExample<>(label, tribuoFeatures);
    }

    /**
     * Build Tribuo regression example (for credit limit prediction).
     *
     * @param features Merchant features
     * @param targetCreditLimit Ideal credit limit in KES
     * @return Tribuo Example with Regressor
     */
    public Example<Regressor> buildRegressionExample(MerchantFeatures features,
                                                      BigDecimal targetCreditLimit) {
        List<Feature> tribuoFeatures = buildFeatureVector(features);

        // Create regressor (single output dimension)
        Regressor regressor = new Regressor("credit_limit",
                targetCreditLimit.doubleValue());

        return new ArrayExample<>(regressor, tribuoFeatures);
    }

    /**
     * Build complete feature vector from merchant features.
     *
     * Includes:
     * - 24 numeric features (normalized)
     * - One-hot encoded categorical features (category, city, verification)
     *
     * Total: ~24 + 10 + 10 + 3 = ~47 features
     */
    private List<Feature> buildFeatureVector(MerchantFeatures features) {
        List<Feature> featureList = new ArrayList<>();

        // ========== Order Features (10) ==========
        featureList.add(new Feature("total_orders", features.totalOrders().doubleValue()));
        featureList.add(new Feature("order_frequency_per_week", features.orderFrequencyPerWeek()));
        featureList.add(new Feature("avg_order_value", features.avgOrderValue().doubleValue()));
        featureList.add(new Feature("order_trend_slope", features.orderValueTrendSlope12w()));
        featureList.add(new Feature("order_consistency_stddev", features.orderConsistencyStddev()));
        featureList.add(new Feature("cancellation_rate", features.cancellationRate()));
        featureList.add(new Feature("return_rate", features.returnRate()));
        featureList.add(new Feature("days_since_last_order", features.daysSinceLastOrder().doubleValue()));
        featureList.add(new Feature("unique_skus_ordered", features.uniqueSkusOrdered().doubleValue()));
        featureList.add(new Feature("top_sku_concentration", features.topSkuConcentration()));

        // ========== Payment Features (8) ==========
        featureList.add(new Feature("total_payments", features.totalPayments().doubleValue()));
        featureList.add(new Feature("on_time_payment_pct", features.onTimePaymentPct()));
        featureList.add(new Feature("avg_days_to_pay", features.avgDaysToPay()));
        featureList.add(new Feature("worst_days_to_pay", features.worstDaysToPay().doubleValue()));
        featureList.add(new Feature("partial_payment_frequency", features.partialPaymentFrequency()));
        featureList.add(new Feature("consecutive_on_time_streak", features.consecutiveOnTimeStreak().doubleValue()));
        featureList.add(new Feature("total_overdue_amount", features.totalOverdueAmount().doubleValue()));

        // Payment method distribution (3 features: MPESA %, CASH %, BANK %)
        Map<String, Integer> paymentMethods = features.paymentMethodDistribution();
        int totalPaymentMethods = paymentMethods.values().stream().mapToInt(Integer::intValue).sum();
        if (totalPaymentMethods > 0) {
            double mpesaPct = paymentMethods.getOrDefault("MPESA", 0) / (double) totalPaymentMethods;
            double cashPct = paymentMethods.getOrDefault("CASH", 0) / (double) totalPaymentMethods;
            double bankPct = paymentMethods.getOrDefault("BANK_TRANSFER", 0) / (double) totalPaymentMethods;
            featureList.add(new Feature("payment_method_mpesa_pct", mpesaPct));
            featureList.add(new Feature("payment_method_cash_pct", cashPct));
            featureList.add(new Feature("payment_method_bank_pct", bankPct));
        } else {
            featureList.add(new Feature("payment_method_mpesa_pct", 0.0));
            featureList.add(new Feature("payment_method_cash_pct", 0.0));
            featureList.add(new Feature("payment_method_bank_pct", 0.0));
        }

        // ========== Credit Features (6) ==========
        featureList.add(new Feature("current_credit_limit", features.currentCreditLimit().doubleValue()));
        featureList.add(new Feature("current_utilization_ratio", features.currentUtilizationRatio()));
        featureList.add(new Feature("peak_utilization_ratio", features.peakUtilizationRatio()));
        featureList.add(new Feature("utilization_trend_slope", features.utilizationTrendSlope()));
        featureList.add(new Feature("limit_increase_count", features.limitIncreaseCount().doubleValue()));
        featureList.add(new Feature("days_since_last_limit_change", features.daysSinceLastLimitChange().doubleValue()));

        // ========== Profile Features (1 numeric) ==========
        featureList.add(new Feature("relationship_tenure_days", features.relationshipTenureDays().doubleValue()));

        // ========== Categorical Features (One-Hot Encoded) ==========

        // Business category (10 categories)
        String category = features.businessCategoryEncoded();
        for (String possibleCategory : ALL_CATEGORIES) {
            String featureName = "category_" + sanitizeFeatureName(possibleCategory);
            double value = (category != null && category.equals(possibleCategory)) ? 1.0 : 0.0;
            featureList.add(new Feature(featureName, value));
        }

        // Geographic cluster (10 cities)
        String city = features.geographicCluster();
        for (String possibleCity : ALL_CITIES) {
            String featureName = "city_" + sanitizeFeatureName(possibleCity);
            double value = (city != null && city.equals(possibleCity)) ? 1.0 : 0.0;
            featureList.add(new Feature(featureName, value));
        }

        // Verification status (3 statuses)
        String verificationStatus = features.verificationStatus();
        for (String possibleStatus : VERIFICATION_STATUSES) {
            String featureName = "verification_" + sanitizeFeatureName(possibleStatus);
            double value = (verificationStatus != null && verificationStatus.equals(possibleStatus)) ? 1.0 : 0.0;
            featureList.add(new Feature(featureName, value));
        }

        return featureList;
    }

    /**
     * Build a complete Tribuo dataset for training.
     *
     * @param features List of merchant features
     * @param labels List of labels (true = DEFAULT)
     * @return MutableDataset ready for XGBoost training
     */
    public MutableDataset<Label> buildClassificationDataset(List<MerchantFeatures> features,
                                                             List<Boolean> labels) {
        if (features.size() != labels.size()) {
            throw new IllegalArgumentException("Features and labels must have same size");
        }

        // Create dataset with DataProvenance and LabelFactory (Tribuo 4.3+)
        SimpleDataSourceProvenance provenance = new SimpleDataSourceProvenance(
                "Synthetic merchant credit data",
                new LabelFactory()
        );
        MutableDataset<Label> dataset = new MutableDataset<>(provenance, new LabelFactory());

        for (int i = 0; i < features.size(); i++) {
            Example<Label> example = buildClassificationExample(features.get(i), labels.get(i));
            dataset.add(example);
        }

        log.info("Built classification dataset with {} examples", dataset.size());
        return dataset;
    }

    /**
     * Build a complete Tribuo dataset for regression.
     *
     * @param features List of merchant features
     * @param targetLimits List of target credit limits
     * @return MutableDataset ready for XGBoost regression training
     */
    public MutableDataset<Regressor> buildRegressionDataset(List<MerchantFeatures> features,
                                                             List<BigDecimal> targetLimits) {
        if (features.size() != targetLimits.size()) {
            throw new IllegalArgumentException("Features and target limits must have same size");
        }

        // Create dataset with DataProvenance and RegressionFactory (Tribuo 4.3+)
        SimpleDataSourceProvenance provenance = new SimpleDataSourceProvenance(
                "Synthetic merchant credit limit data",
                new RegressionFactory()
        );
        MutableDataset<Regressor> dataset = new MutableDataset<>(provenance, new RegressionFactory());

        for (int i = 0; i < features.size(); i++) {
            Example<Regressor> example = buildRegressionExample(features.get(i), targetLimits.get(i));
            dataset.add(example);
        }

        log.info("Built regression dataset with {} examples", dataset.size());
        return dataset;
    }

    /**
     * Get feature names in order (for model interpretation).
     *
     * @return List of all feature names
     */
    public List<String> getFeatureNames() {
        List<String> names = new ArrayList<>();

        // Numeric features
        names.addAll(List.of(
                "total_orders", "order_frequency_per_week", "avg_order_value",
                "order_trend_slope", "order_consistency_stddev", "cancellation_rate",
                "return_rate", "days_since_last_order", "unique_skus_ordered",
                "top_sku_concentration",
                "total_payments", "on_time_payment_pct", "avg_days_to_pay",
                "worst_days_to_pay", "partial_payment_frequency",
                "consecutive_on_time_streak", "total_overdue_amount",
                "payment_method_mpesa_pct", "payment_method_cash_pct",
                "payment_method_bank_pct",
                "current_credit_limit", "current_utilization_ratio",
                "peak_utilization_ratio", "utilization_trend_slope",
                "limit_increase_count", "days_since_last_limit_change",
                "relationship_tenure_days"
        ));

        // Category features
        for (String category : ALL_CATEGORIES) {
            names.add("category_" + sanitizeFeatureName(category));
        }

        // City features
        for (String city : ALL_CITIES) {
            names.add("city_" + sanitizeFeatureName(city));
        }

        // Verification features
        for (String status : VERIFICATION_STATUSES) {
            names.add("verification_" + sanitizeFeatureName(status));
        }

        return names;
    }

    /**
     * Get feature count.
     *
     * @return Total number of features
     */
    public int getFeatureCount() {
        return 27 + ALL_CATEGORIES.size() + ALL_CITIES.size() + VERIFICATION_STATUSES.size();
    }

    /**
     * Sanitize feature name for Tribuo compatibility.
     */
    private String sanitizeFeatureName(String name) {
        return name.toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replaceAll("[^a-z0-9_]", "");
    }

    /**
     * Feature statistics for normalization.
     */
    public record FeatureStats(double mean, double stddev, double min, double max) {
    }

    /**
     * Compute feature statistics from training data (for normalization).
     *
     * In production, call this during training and store the stats.
     */
    public void computeFeatureStats(List<MerchantFeatures> trainingFeatures) {
        // TODO: Implement feature normalization statistics computation
        // For now, XGBoost handles scaling internally, so this is optional
        log.info("Feature statistics computation not yet implemented (XGBoost handles scaling)");
    }
}
