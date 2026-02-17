package com.zuqi.ai.training;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual test to train initial credit scoring models.
 *
 * Run this test ONCE to create the initial synthetic-trained models.
 *
 * This test:
 * 1. Generates 10,000 synthetic merchants
 * 2. Trains XGBoost credit classifier
 * 3. Trains XGBoost credit limit regressor
 * 4. Evaluates both models
 * 5. Promotes to ACTIVE if quality gates pass
 *
 * Expected duration: 2-5 minutes
 *
 * To run: Remove @Disabled annotation and execute this test class.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 5 "Run training pipeline"
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
@Disabled("Manual test - only run when you want to train initial models")
public class TrainInitialModelsManualTest {

    @Autowired
    private CreditModelTrainingPipeline trainingPipeline;

    @Test
    void trainInitialModels() {
        log.info("=".repeat(80));
        log.info("MANUAL TEST: Training Initial Credit Scoring Models");
        log.info("This will generate 10,000 synthetic merchants and train 2 models");
        log.info("Expected duration: 2-5 minutes");
        log.info("=".repeat(80));

        // Run training pipeline with 10,000 synthetic merchants
        int datasetSize = 10_000;

        CreditModelTrainingPipeline.TrainingPipelineResult result =
                trainingPipeline.runPipeline(datasetSize);

        // Verify pipeline succeeded
        assertThat(result.success())
                .as("Training pipeline should succeed")
                .isTrue();

        // Verify dataset size
        assertThat(result.datasetSize())
                .as("Dataset size should be 10,000")
                .isEqualTo(datasetSize);

        // Verify train/test split
        assertThat(result.trainSize())
                .as("Train set should be 80% of dataset")
                .isEqualTo(8000);

        assertThat(result.testSize())
                .as("Test set should be 20% of dataset")
                .isEqualTo(2000);

        // Verify classifier results
        assertThat(result.classifierEvaluation())
                .as("Classifier evaluation should be present")
                .isNotNull();

        log.info("Classifier Metrics:");
        log.info("  - Accuracy: {:.3f}", result.classifierEvaluation().accuracy());
        log.info("  - Precision: {:.3f}", result.classifierEvaluation().precision());
        log.info("  - Recall: {:.3f}", result.classifierEvaluation().recall());
        log.info("  - F1 Score: {:.3f}", result.classifierEvaluation().f1Score());
        log.info("  - AUC-ROC: {:.3f}", result.classifierEvaluation().aucRoc());
        log.info("  - Passed Quality Gate: {}", result.classifierEvaluation().passedQualityGate());

        assertThat(result.classifierEvaluation().aucRoc())
                .as("Classifier AUC-ROC should be >= 0.75")
                .isGreaterThanOrEqualTo(0.75);

        assertThat(result.classifierEvaluation().passedQualityGate())
                .as("Classifier should pass quality gate")
                .isTrue();

        assertThat(result.classifierModelId())
                .as("Classifier model ID should be assigned")
                .isNotNull();

        // Verify regressor results
        assertThat(result.regressorEvaluation())
                .as("Regressor evaluation should be present")
                .isNotNull();

        log.info("Regressor Metrics:");
        log.info("  - RMSE: {:.2f}", result.regressorEvaluation().rmse());
        log.info("  - MAE: {:.2f}", result.regressorEvaluation().mae());
        log.info("  - R²: {:.3f}", result.regressorEvaluation().r2());
        log.info("  - Explained Variance: {:.3f}", result.regressorEvaluation().explainedVariance());
        log.info("  - Passed Quality Gate: {}", result.regressorEvaluation().passedQualityGate());

        assertThat(result.regressorEvaluation().r2())
                .as("Regressor R² should be >= 0.70")
                .isGreaterThanOrEqualTo(0.70);

        assertThat(result.regressorEvaluation().passedQualityGate())
                .as("Regressor should pass quality gate")
                .isTrue();

        assertThat(result.regressorModelId())
                .as("Regressor model ID should be assigned")
                .isNotNull();

        // Log final summary
        log.info("=".repeat(80));
        log.info("✅ TRAINING COMPLETE");
        log.info("Pipeline Duration: {}ms", result.durationMs());
        log.info("Classifier Model ID: {}", result.classifierModelId());
        log.info("Regressor Model ID: {}", result.regressorModelId());
        log.info("Both models have been promoted to ACTIVE status");
        log.info("You can now switch to HYBRID mode in application.yml");
        log.info("=".repeat(80));
    }

    @Test
    @Disabled("Quick smoke test - smaller dataset")
    void trainWithSmallerDataset() {
        log.info("Quick smoke test with 1,000 merchants");

        CreditModelTrainingPipeline.TrainingPipelineResult result =
                trainingPipeline.runPipeline(1000);

        assertThat(result.success()).isTrue();
        log.info("Smoke test passed: Classifier AUC={:.3f}, Regressor R²={:.3f}",
                result.classifierEvaluation().aucRoc(),
                result.regressorEvaluation().r2());
    }
}
