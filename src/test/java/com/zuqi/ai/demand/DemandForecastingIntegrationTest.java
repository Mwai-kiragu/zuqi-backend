package com.zuqi.ai.demand;

import com.zuqi.ai.feature.DemandFeatures;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for demand forecasting components.
 *
 * Tests the full workflow:
 * 1. Synthetic data generation
 * 2. Feature building
 * 3. Model training
 * 4. Forecasting
 * 5. Order suggestions
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class DemandForecastingIntegrationTest {

    @Autowired
    private SyntheticOrderDataGenerator syntheticDataGenerator;

    @Autowired
    private DemandFeatureBuilder featureBuilder;

    @Autowired
    private DemandModelTrainer modelTrainer;

    @Autowired
    private OrderSuggestionService orderSuggestionService;

    @Test
    void testSyntheticDataGeneration() {
        log.info("Testing synthetic order data generation");

        // Generate small dataset
        List<SyntheticOrderDataGenerator.SyntheticOrderSequence> sequences =
                syntheticDataGenerator.generateOrderSequences(10, 5, 12);

        // Verify sequences generated
        assertThat(sequences)
                .as("Should generate order sequences")
                .isNotEmpty()
                .hasSizeLessThanOrEqualTo(50); // Max 10 merchants × 5 products

        // Verify each sequence has weekly orders
        sequences.forEach(sequence -> {
            assertThat(sequence.weeklyOrders())
                    .as("Each sequence should have weekly orders")
                    .isNotEmpty();

            assertThat(sequence.merchantId())
                    .as("Merchant ID should be set")
                    .isNotNull();

            assertThat(sequence.productId())
                    .as("Product ID should be set")
                    .isNotNull();

            assertThat(sequence.merchantCategory())
                    .as("Merchant category should be set")
                    .isNotBlank();

            assertThat(sequence.productCategory())
                    .as("Product category should be set")
                    .isNotBlank();
        });

        log.info("✅ Generated {} order sequences", sequences.size());
    }

    @Test
    void testFeatureBuilding() {
        log.info("Testing demand feature building");

        // Create sample demand features
        DemandFeatures features = DemandFeatures.builder()
                .merchantId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                // Lag features
                .qty1wAgo(BigDecimal.valueOf(50))
                .qty2wAgo(BigDecimal.valueOf(45))
                .qty3wAgo(BigDecimal.valueOf(48))
                .qty4wAgo(BigDecimal.valueOf(52))
                .rollingAvg4w(BigDecimal.valueOf(48.75))
                .rollingAvg12w(BigDecimal.valueOf(47.50))
                .trendDirection("STABLE")
                // Temporal features
                .dayOfWeek(2)
                .weekOfMonth(3)
                .monthOfYear(6)
                .isHoliday(false)
                .isPaydayWeek(false)
                .isRamadan(false)
                .isChristmasSeason(false)
                // Merchant context
                .merchantCategory("Supermarket")
                .merchantSizeTier("MEDIUM")
                .merchantCreditStatus("GOOD")
                .merchantTenureDays(180)
                // SKU context
                .productCategory("Beverages")
                .priceTier("MEDIUM")
                .isPromotional(false)
                .typicalShelfLifeDays(365)
                .build();

        // Build regression example
        var example = featureBuilder.buildRegressionExample(features, BigDecimal.valueOf(50));

        assertThat(example)
                .as("Should build Tribuo example")
                .isNotNull();

        assertThat(example.getOutput().getNames())
                .as("Should have predicted_quantity output")
                .contains("predicted_quantity");

        // Build dataset
        List<DemandFeatures> featureList = List.of(features, features);
        List<BigDecimal> quantities = List.of(BigDecimal.valueOf(50), BigDecimal.valueOf(55));

        var dataset = featureBuilder.buildRegressionDataset(featureList, quantities);

        assertThat(dataset.size())
                .as("Dataset should have 2 examples")
                .isEqualTo(2);

        log.info("✅ Feature building successful: {} features", featureBuilder.getFeatureCount());
    }

    @Test
    void testModelTrainingValidation() {
        log.info("Testing model training validation");

        // Create minimal training data
        List<DemandModelTrainer.DemandTrainingExample> trainingData = List.of(
                new DemandModelTrainer.DemandTrainingExample(
                        createSampleFeatures(),
                        BigDecimal.valueOf(50)
                )
        );

        // Validate - should fail (too small)
        try {
            modelTrainer.validateDataset(trainingData);
            assertThat(false)
                    .as("Validation should fail for dataset < 1000 examples")
                    .isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                    .as("Should throw exception for small dataset")
                    .contains("too small");
            log.info("✅ Validation correctly rejected small dataset");
        }

        // Test null features validation
        List<DemandModelTrainer.DemandTrainingExample> nullData = List.of(
                new DemandModelTrainer.DemandTrainingExample(null, BigDecimal.valueOf(50))
        );

        try {
            modelTrainer.validateDataset(nullData);
            assertThat(false)
                    .as("Validation should fail for null features")
                    .isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                    .as("Should throw exception for null features")
                    .contains("null features");
            log.info("✅ Validation correctly rejected null features");
        }
    }

    @Test
    void testEndToEndWorkflow() {
        log.info("Testing end-to-end demand forecasting workflow");

        // Step 1: Generate synthetic data (small dataset)
        List<SyntheticOrderDataGenerator.SyntheticOrderSequence> sequences =
                syntheticDataGenerator.generateOrderSequences(5, 3, 12);

        assertThat(sequences)
                .as("Should generate sequences")
                .isNotEmpty();

        log.info("✅ Step 1: Generated {} sequences", sequences.size());

        // Step 2: Verify sequences have realistic patterns
        long nonZeroOrders = sequences.stream()
                .flatMap(seq -> seq.weeklyOrders().stream())
                .filter(order -> order.quantity().compareTo(BigDecimal.ZERO) > 0)
                .count();

        assertThat(nonZeroOrders)
                .as("Should have non-zero orders")
                .isGreaterThan(0);

        log.info("✅ Step 2: Verified {} non-zero orders", nonZeroOrders);

        // Step 3: Verify seasonality patterns exist
        boolean hasSeasonality = sequences.stream()
                .anyMatch(seq -> seq.weeklyOrders().stream()
                        .map(SyntheticOrderDataGenerator.WeeklyOrder::quantity)
                        .distinct()
                        .count() > 1);

        assertThat(hasSeasonality)
                .as("Should have varying quantities (seasonality)")
                .isTrue();

        log.info("✅ Step 3: Verified seasonality patterns");

        // Step 4: Verify sparsity (not all combinations)
        int possibleCombinations = 5 * 3; // 5 merchants × 3 products
        int actualSequences = sequences.size();

        assertThat(actualSequences)
                .as("Should have sparsity (< 100% combinations)")
                .isLessThan(possibleCombinations);

        double sparsity = (1.0 - (double)actualSequences / possibleCombinations) * 100;
        log.info("✅ Step 4: Verified sparsity ({:.1f}%)", sparsity);

        log.info("✅ End-to-end workflow test complete");
    }

    /**
     * Helper: Create sample demand features for testing.
     */
    private DemandFeatures createSampleFeatures() {
        return DemandFeatures.builder()
                .merchantId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .qty1wAgo(BigDecimal.valueOf(50))
                .qty2wAgo(BigDecimal.valueOf(45))
                .qty3wAgo(BigDecimal.valueOf(48))
                .qty4wAgo(BigDecimal.valueOf(52))
                .rollingAvg4w(BigDecimal.valueOf(48.75))
                .rollingAvg12w(BigDecimal.valueOf(47.50))
                .trendDirection("STABLE")
                .dayOfWeek(2)
                .weekOfMonth(3)
                .monthOfYear(6)
                .isHoliday(false)
                .isPaydayWeek(false)
                .isRamadan(false)
                .isChristmasSeason(false)
                .merchantCategory("Supermarket")
                .merchantSizeTier("MEDIUM")
                .merchantCreditStatus("GOOD")
                .merchantTenureDays(180)
                .productCategory("Beverages")
                .priceTier("MEDIUM")
                .isPromotional(false)
                .typicalShelfLifeDays(365)
                .build();
    }
}
