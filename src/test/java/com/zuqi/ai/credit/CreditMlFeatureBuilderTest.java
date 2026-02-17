package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.training.SyntheticMerchant;
import com.zuqi.ai.training.SyntheticMerchantDataGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.regression.Regressor;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test ML feature vector building.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 2
 */
@SpringBootTest
@ActiveProfiles("test")
class CreditMlFeatureBuilderTest {

    @Autowired
    private CreditMlFeatureBuilder featureBuilder;

    @Autowired
    private SyntheticMerchantDataGenerator syntheticDataGenerator;

    @Test
    void testBuildClassificationExample() {
        // Generate a synthetic merchant
        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(1);
        SyntheticMerchant merchant = merchants.get(0);

        // Build Tribuo example
        Example<Label> example = featureBuilder.buildClassificationExample(
                merchant.features(),
                merchant.didDefault()
        );

        // Verify
        assertThat(example).isNotNull();
        assertThat(example.getOutput().getLabel()).isIn("DEFAULT", "NO_DEFAULT");
        assertThat(example.size()).isGreaterThan(40); // ~47 features expected

        // Print for debugging
        System.out.println("Classification example created:");
        System.out.println("  Label: " + example.getOutput().getLabel());
        System.out.println("  Feature count: " + example.size());
    }

    @Test
    void testBuildRegressionExample() {
        // Generate a synthetic merchant
        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(1);
        SyntheticMerchant merchant = merchants.get(0);

        BigDecimal targetLimit = BigDecimal.valueOf(250_000);

        // Build Tribuo regression example
        Example<Regressor> example = featureBuilder.buildRegressionExample(
                merchant.features(),
                targetLimit
        );

        // Verify
        assertThat(example).isNotNull();
        assertThat(example.getOutput().getNames()).contains("credit_limit");
        assertThat(example.getOutput().getValues()[0]).isEqualTo(250_000.0);
        assertThat(example.size()).isGreaterThan(40);

        System.out.println("Regression example created:");
        System.out.println("  Target limit: " + example.getOutput().getValues()[0]);
        System.out.println("  Feature count: " + example.size());
    }

    @Test
    void testBuildClassificationDataset() {
        // Generate 100 synthetic merchants
        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(100);

        List<MerchantFeatures> features = merchants.stream()
                .map(SyntheticMerchant::features)
                .collect(Collectors.toList());

        List<Boolean> labels = merchants.stream()
                .map(SyntheticMerchant::didDefault)
                .collect(Collectors.toList());

        // Build dataset
        MutableDataset<Label> dataset = featureBuilder.buildClassificationDataset(features, labels);

        // Verify
        assertThat(dataset.size()).isEqualTo(100);
        assertThat(dataset.getOutputs()).hasSizeGreaterThan(0);

        // Check label distribution
        long defaultCount = dataset.getData().stream()
                .filter(ex -> ex.getOutput().getLabel().equals("DEFAULT"))
                .count();
        long noDefaultCount = dataset.getData().stream()
                .filter(ex -> ex.getOutput().getLabel().equals("NO_DEFAULT"))
                .count();

        System.out.println("Classification dataset built:");
        System.out.println("  Total examples: " + dataset.size());
        System.out.println("  DEFAULT: " + defaultCount);
        System.out.println("  NO_DEFAULT: " + noDefaultCount);
        System.out.println("  Default rate: " + String.format("%.1f%%", (defaultCount * 100.0 / dataset.size())));

        assertThat(defaultCount + noDefaultCount).isEqualTo(100);
    }

    @Test
    void testBuildRegressionDataset() {
        // Generate 50 synthetic merchants
        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(50);

        List<MerchantFeatures> features = merchants.stream()
                .map(SyntheticMerchant::features)
                .collect(Collectors.toList());

        List<BigDecimal> targetLimits = features.stream()
                .map(f -> f.avgOrderValue().multiply(BigDecimal.valueOf(8)))  // 8x monthly order value
                .collect(Collectors.toList());

        // Build dataset
        MutableDataset<Regressor> dataset = featureBuilder.buildRegressionDataset(features, targetLimits);

        // Verify
        assertThat(dataset.size()).isEqualTo(50);

        System.out.println("Regression dataset built:");
        System.out.println("  Total examples: " + dataset.size());
    }

    @Test
    void testFeatureNames() {
        List<String> featureNames = featureBuilder.getFeatureNames();

        assertThat(featureNames).isNotEmpty();
        assertThat(featureNames).contains("total_orders");
        assertThat(featureNames).contains("on_time_payment_pct");
        assertThat(featureNames).contains("current_credit_limit");
        assertThat(featureNames).contains("relationship_tenure_days");

        // Check categorical features
        assertThat(featureNames.stream()
                .anyMatch(name -> name.startsWith("category_"))).isTrue();
        assertThat(featureNames.stream()
                .anyMatch(name -> name.startsWith("city_"))).isTrue();
        assertThat(featureNames.stream()
                .anyMatch(name -> name.startsWith("verification_"))).isTrue();

        System.out.println("Total features: " + featureNames.size());
        System.out.println("Feature names: " + featureNames);
    }

    @Test
    void testFeatureCount() {
        int count = featureBuilder.getFeatureCount();

        // 27 numeric + 10 categories + 10 cities + 3 verification = 50 features
        assertThat(count).isEqualTo(50);
        System.out.println("Expected feature count: " + count);
    }

    @Test
    void testNoNullFeatures() {
        // Generate a synthetic merchant
        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(10);

        for (SyntheticMerchant merchant : merchants) {
            Example<Label> example = featureBuilder.buildClassificationExample(
                    merchant.features(),
                    merchant.didDefault()
            );

            // Check no null or NaN values
            for (Feature feature : example) {
                assertThat(feature.getValue()).isNotNull();
                assertThat(Double.isNaN(feature.getValue())).isFalse();
                assertThat(Double.isInfinite(feature.getValue())).isFalse();
            }
        }

        System.out.println("All 10 examples have valid feature values (no nulls, NaN, or Infinity)");
    }

    @Test
    void testOneHotEncoding() {
        // Generate a synthetic merchant
        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(1);
        SyntheticMerchant merchant = merchants.get(0);

        Example<Label> example = featureBuilder.buildClassificationExample(
                merchant.features(),
                merchant.didDefault()
        );

        // Count one-hot encoded features (should be exactly 1.0 for category, city, verification)
        long categoryOnes = 0;
        long cityOnes = 0;
        long verificationOnes = 0;

        for (Feature f : example) {
            if (f.getName().startsWith("category_") && f.getValue() == 1.0) {
                categoryOnes++;
            }
            if (f.getName().startsWith("city_") && f.getValue() == 1.0) {
                cityOnes++;
            }
            if (f.getName().startsWith("verification_") && f.getValue() == 1.0) {
                verificationOnes++;
            }
        }

        System.out.println("One-hot encoding validation:");
        System.out.println("  Category features with value 1.0: " + categoryOnes);
        System.out.println("  City features with value 1.0: " + cityOnes);
        System.out.println("  Verification features with value 1.0: " + verificationOnes);

        assertThat(categoryOnes).isEqualTo(1); // Exactly one category active
        assertThat(cityOnes).isEqualTo(1);     // Exactly one city active
        assertThat(verificationOnes).isEqualTo(1); // Exactly one status active
    }
}
