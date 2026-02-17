package com.zuqi.ai.demand;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual test to train initial demand forecasting model.
 *
 * Run this test ONCE to create the initial synthetic-trained model.
 *
 * This test:
 * 1. Generates 50 merchants × 20 products × 26 weeks of order data
 * 2. Builds ~13,000 training examples
 * 3. Trains XGBoost demand forecasting model
 * 4. Evaluates model (quality gate: R² ≥ 0.70)
 * 5. Promotes to ACTIVE if quality gate passes
 *
 * Expected duration: 2-5 minutes
 *
 * To run: Remove @Disabled annotation and execute this test class.
 *
 * Blueprint: plan.md Section 6.2 - Demand Forecasting Module
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
@Disabled("Manual test - only run when you want to train initial demand forecasting model")
public class TrainDemandModelManualTest {

    @Autowired
    private DemandModelTrainingPipeline trainingPipeline;

    @Test
    void trainInitialDemandForecastingModel() {
        log.info("=".repeat(80));
        log.info("MANUAL TEST: Training Initial Demand Forecasting Model");
        log.info("This will generate synthetic order data and train the model");
        log.info("Expected duration: 2-5 minutes");
        log.info("=".repeat(80));

        // Configuration
        int numMerchants = 50;   // 50 synthetic merchants
        int numProducts = 20;    // 20 synthetic products
        int numWeeks = 26;       // 26 weeks (6 months) of historical data

        log.info("Training configuration:");
        log.info("  - Merchants: {}", numMerchants);
        log.info("  - Products: {}", numProducts);
        log.info("  - Weeks: {}", numWeeks);
        log.info("  - Expected sequences: ~{}", (int)(numMerchants * numProducts * 0.6)); // 60% sparsity
        log.info("  - Expected training examples: ~{}", (int)(numMerchants * numProducts * 0.6 * (numWeeks - 5)));

        // Run training pipeline
        DemandModelTrainingPipeline.TrainingPipelineResult result =
                trainingPipeline.runPipeline(numMerchants, numProducts, numWeeks);

        // Verify pipeline succeeded
        assertThat(result.success())
                .as("Training pipeline should succeed")
                .isTrue();

        // Verify sequences generated
        assertThat(result.numSequences())
                .as("Should generate order sequences")
                .isGreaterThan(0);

        log.info("Generated {} order sequences", result.numSequences());

        // Verify training examples created
        assertThat(result.numTrainingExamples())
                .as("Should create training examples")
                .isGreaterThan(1000); // At least 1000 examples

        log.info("Built {} training examples", result.numTrainingExamples());

        // Verify train/test split
        assertThat(result.trainSize())
                .as("Train set should be ~80% of examples")
                .isGreaterThan((int)(result.numTrainingExamples() * 0.75));

        assertThat(result.testSize())
                .as("Test set should be ~20% of examples")
                .isGreaterThan((int)(result.numTrainingExamples() * 0.15));

        log.info("Train/Test split: {} / {}", result.trainSize(), result.testSize());

        // Verify model evaluation
        assertThat(result.evaluation())
                .as("Model evaluation should be present")
                .isNotNull();

        log.info("Model Metrics:");
        log.info("  - RMSE: {:.2f}", result.evaluation().rmse());
        log.info("  - MAE: {:.2f}", result.evaluation().mae());
        log.info("  - R²: {:.3f}", result.evaluation().r2());
        log.info("  - Explained Variance: {:.3f}", result.evaluation().explainedVariance());
        log.info("  - Passed Quality Gate: {}", result.evaluation().passedQualityGate());

        // Verify R² meets quality gate
        assertThat(result.evaluation().r2())
                .as("R² should be >= 0.70 (explains 70%+ variance)")
                .isGreaterThanOrEqualTo(0.70);

        // Verify quality gate passed
        assertThat(result.evaluation().passedQualityGate())
                .as("Model should pass quality gate")
                .isTrue();

        // Verify model was promoted
        assertThat(result.modelId())
                .as("Model ID should be assigned (promoted to ACTIVE)")
                .isNotNull();

        // Log final summary
        log.info("=".repeat(80));
        log.info("✅ DEMAND FORECASTING MODEL TRAINING COMPLETE");
        log.info("Pipeline Duration: {}ms", result.durationMs());
        log.info("Model ID: {}", result.modelId());
        log.info("R² Score: {:.3f} ({})",
                result.evaluation().r2(),
                result.evaluation().passedQualityGate() ? "PASSED" : "FAILED");
        log.info("Model has been promoted to ACTIVE status");
        log.info("You can now use the demand forecasting API");
        log.info("=".repeat(80));
    }

    @Test
    @Disabled("Quick smoke test - smaller dataset")
    void trainWithSmallerDataset() {
        log.info("Quick smoke test with smaller dataset");

        // Smaller dataset for quick testing
        int numMerchants = 10;
        int numProducts = 5;
        int numWeeks = 12;

        DemandModelTrainingPipeline.TrainingPipelineResult result =
                trainingPipeline.runPipeline(numMerchants, numProducts, numWeeks);

        assertThat(result.success()).isTrue();

        log.info("Smoke test passed: R²={:.3f}, MAE={:.2f}, RMSE={:.2f}",
                result.evaluation().r2(),
                result.evaluation().mae(),
                result.evaluation().rmse());
    }

    @Test
    @Disabled("Large dataset test - for production-scale validation")
    void trainWithLargeDataset() {
        log.info("Large dataset test - production scale");

        // Production-scale dataset
        int numMerchants = 100;
        int numProducts = 50;
        int numWeeks = 52;  // 1 year of data

        DemandModelTrainingPipeline.TrainingPipelineResult result =
                trainingPipeline.runPipeline(numMerchants, numProducts, numWeeks);

        assertThat(result.success()).isTrue();

        log.info("Large dataset test passed:");
        log.info("  - Sequences: {}", result.numSequences());
        log.info("  - Examples: {}", result.numTrainingExamples());
        log.info("  - R²: {:.3f}", result.evaluation().r2());
        log.info("  - Duration: {}ms", result.durationMs());
    }
}
