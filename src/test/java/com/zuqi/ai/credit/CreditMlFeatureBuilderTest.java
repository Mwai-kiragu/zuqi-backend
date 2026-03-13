package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatures;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    // ── Test helpers ───────────────────────────────────────────────────────

    private MerchantFeatures buildTestFeatures() {
        return MerchantFeatures.builder()
                .merchantId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .totalOrders(150)
                .orderFrequencyPerWeek(2.5)
                .avgOrderValue(BigDecimal.valueOf(25_000))
                .orderValueTrendSlope12w(0.05)
                .orderConsistencyStddev(5_000.0)
                .cancellationRate(0.03)
                .returnRate(0.02)
                .daysSinceLastOrder(5)
                .uniqueSkusOrdered(12)
                .topSkuConcentration(0.35)
                .totalPayments(140)
                .onTimePaymentPct(0.88)
                .avgDaysToPay(12.0)
                .worstDaysToPay(30)
                .partialPaymentFrequency(0.05)
                .paymentMethodDistribution(Map.of("MPESA", 60, "CASH", 30, "BANK_TRANSFER", 10))
                .consecutiveOnTimeStreak(8)
                .totalOverdueAmount(BigDecimal.ZERO)
                .currentCreditLimit(BigDecimal.valueOf(200_000))
                .currentUtilizationRatio(0.45)
                .peakUtilizationRatio(0.70)
                .utilizationTrendSlope(-0.02)
                .limitIncreaseCount(1)
                .daysSinceLastLimitChange(90)
                .businessCategoryEncoded("General Store")
                .relationshipTenureDays(450)
                .verificationStatus("VERIFIED")
                .geographicCluster("Nairobi")
                .build();
    }

    // ── Tests ──────────────────────────────────────────────────────────────

    @Test
    void testBuildClassificationExample() {
        MerchantFeatures features = buildTestFeatures();
        boolean didDefault = false;

        Example<Label> example = featureBuilder.buildClassificationExample(features, didDefault);

        assertThat(example).isNotNull();
        assertThat(example.getOutput().getLabel()).isIn("DEFAULT", "NO_DEFAULT");
        assertThat(example.size()).isGreaterThan(40); // ~47 features expected

        System.out.println("Classification example created:");
        System.out.println("  Label: " + example.getOutput().getLabel());
        System.out.println("  Feature count: " + example.size());
    }

    @Test
    void testBuildRegressionExample() {
        MerchantFeatures features = buildTestFeatures();
        BigDecimal targetLimit = BigDecimal.valueOf(250_000);

        Example<Regressor> example = featureBuilder.buildRegressionExample(features, targetLimit);

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
        List<MerchantFeatures> features = new ArrayList<>();
        List<Boolean> labels = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            features.add(buildTestFeatures());
            labels.add(i % 6 == 0); // ~17% default rate
        }

        MutableDataset<Label> dataset = featureBuilder.buildClassificationDataset(features, labels);

        assertThat(dataset.size()).isEqualTo(100);
        assertThat(dataset.getOutputs()).hasSizeGreaterThan(0);

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

        assertThat(defaultCount + noDefaultCount).isEqualTo(100);
    }

    @Test
    void testBuildRegressionDataset() {
        List<MerchantFeatures> features = new ArrayList<>();
        List<BigDecimal> targetLimits = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            MerchantFeatures f = buildTestFeatures();
            features.add(f);
            targetLimits.add(f.avgOrderValue().multiply(BigDecimal.valueOf(8)));
        }

        MutableDataset<Regressor> dataset = featureBuilder.buildRegressionDataset(features, targetLimits);

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
        for (int i = 0; i < 10; i++) {
            MerchantFeatures features = buildTestFeatures();
            Example<Label> example = featureBuilder.buildClassificationExample(features, i % 5 == 0);

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
        MerchantFeatures features = buildTestFeatures();

        Example<Label> example = featureBuilder.buildClassificationExample(features, false);

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

        assertThat(categoryOnes).isEqualTo(1);
        assertThat(cityOnes).isEqualTo(1);
        assertThat(verificationOnes).isEqualTo(1);
    }
}
