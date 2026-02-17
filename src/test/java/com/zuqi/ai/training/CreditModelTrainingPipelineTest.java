package com.zuqi.ai.training;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test complete ML training pipeline.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 5
 */
@SpringBootTest
@ActiveProfiles("test")
class CreditModelTrainingPipelineTest {

    @Autowired
    private CreditModelTrainingPipeline trainingPipeline;

    @Test
    void testRunPipelineWithMinimumDataset() {
        // Run pipeline with 1000 merchants (minimum)
        CreditModelTrainingPipeline.TrainingPipelineResult result =
                trainingPipeline.runPipeline(1000);

        // Verify pipeline completed
        assertThat(result).isNotNull();
        if (!result.success()) {
            System.out.println("Pipeline failed with error: " + result.errorMessage());
        }
        assertThat(result.success()).as("Pipeline should succeed. Error: " + result.errorMessage()).isTrue();
        assertThat(result.errorMessage()).isNull();

        // Verify dataset split
        assertThat(result.datasetSize()).isEqualTo(1000);
        assertThat(result.trainSize()).isEqualTo(800); // 80%
        assertThat(result.testSize()).isEqualTo(200);  // 20%

        // Verify evaluations were run
        assertThat(result.classifierEvaluation()).isNotNull();
        assertThat(result.regressorEvaluation()).isNotNull();

        // Verify classifier evaluation
        ModelEvaluator.ClassifierEvaluationResult classifierEval = result.classifierEvaluation();
        assertThat(classifierEval.accuracy()).isBetween(0.0, 1.0);
        assertThat(classifierEval.aucRoc()).isBetween(0.0, 1.0);

        // Verify regressor evaluation
        ModelEvaluator.RegressorEvaluationResult regressorEval = result.regressorEvaluation();
        assertThat(regressorEval.rmse()).isGreaterThan(0.0);
        assertThat(regressorEval.r2()).isBetween(-1.0, 1.0); // R² can be negative for bad models

        // Verify duration is reasonable
        assertThat(result.durationMs()).isGreaterThan(0);
        assertThat(result.durationMs()).isLessThan(60_000); // < 1 minute

        // Log results
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Training Pipeline Results");
        System.out.println("=".repeat(80));
        System.out.println("Dataset: " + result.datasetSize() + " merchants");
        System.out.println("Train/Test Split: " + result.trainSize() + "/" + result.testSize());
        System.out.println("\nClassifier Metrics:");
        System.out.println("  Accuracy:  " + String.format("%.3f", classifierEval.accuracy()));
        System.out.println("  Precision: " + String.format("%.3f", classifierEval.precision()));
        System.out.println("  Recall:    " + String.format("%.3f", classifierEval.recall()));
        System.out.println("  F1 Score:  " + String.format("%.3f", classifierEval.f1Score()));
        System.out.println("  AUC-ROC:   " + String.format("%.3f", classifierEval.aucRoc()));
        System.out.println("  Quality Gate: " + (classifierEval.passedQualityGate() ? "✅ PASSED" : "❌ FAILED"));

        System.out.println("\nRegressor Metrics:");
        System.out.println("  RMSE:              " + String.format("%.2f KES", regressorEval.rmse()));
        System.out.println("  MAE:               " + String.format("%.2f KES", regressorEval.mae()));
        System.out.println("  R²:                " + String.format("%.3f", regressorEval.r2()));
        System.out.println("  Explained Variance: " + String.format("%.3f", regressorEval.explainedVariance()));
        System.out.println("  Quality Gate: " + (regressorEval.passedQualityGate() ? "✅ PASSED" : "❌ FAILED"));

        System.out.println("\nPipeline Duration: " + result.durationMs() + "ms");
        System.out.println("=".repeat(80) + "\n");
    }

    @Test
    void testRunPipelineWithLargerDataset() {
        // Run with 2000 merchants for better model quality
        CreditModelTrainingPipeline.TrainingPipelineResult result =
                trainingPipeline.runPipeline(2000);

        assertThat(result.success()).isTrue();
        assertThat(result.datasetSize()).isEqualTo(2000);
        assertThat(result.trainSize()).isEqualTo(1600);
        assertThat(result.testSize()).isEqualTo(400);

        // With more data, models should perform better
        assertThat(result.classifierEvaluation()).isNotNull();
        assertThat(result.regressorEvaluation()).isNotNull();

        System.out.println("\nLarge Dataset Results:");
        System.out.println("Classifier AUC-ROC: " + String.format("%.3f", result.classifierEvaluation().aucRoc()));
        System.out.println("Regressor R²: " + String.format("%.3f", result.regressorEvaluation().r2()));
        System.out.println("Duration: " + result.durationMs() + "ms");
    }

    @Test
    void testClassifierQualityGate() {
        CreditModelTrainingPipeline.TrainingPipelineResult result =
                trainingPipeline.runPipeline(1000);

        ModelEvaluator.ClassifierEvaluationResult eval = result.classifierEvaluation();

        // Quality gate: AUC-ROC >= 0.75
        if (eval.passedQualityGate()) {
            assertThat(eval.aucRoc()).isGreaterThanOrEqualTo(0.75);
            assertThat(result.classifierModelId()).isNotNull();
            System.out.println("✅ Classifier passed quality gate: AUC-ROC=" + String.format("%.3f", eval.aucRoc()));
        } else {
            assertThat(eval.aucRoc()).isLessThan(0.75);
            System.out.println("❌ Classifier failed quality gate: AUC-ROC=" + String.format("%.3f", eval.aucRoc()));
        }
    }

    @Test
    void testRegressorQualityGate() {
        CreditModelTrainingPipeline.TrainingPipelineResult result =
                trainingPipeline.runPipeline(1000);

        ModelEvaluator.RegressorEvaluationResult eval = result.regressorEvaluation();

        // Quality gate: R² >= 0.70
        if (eval.passedQualityGate()) {
            assertThat(eval.r2()).isGreaterThanOrEqualTo(0.70);
            assertThat(result.regressorModelId()).isNotNull();
            System.out.println("✅ Regressor passed quality gate: R²=" + String.format("%.3f", eval.r2()));
        } else {
            assertThat(eval.r2()).isLessThan(0.70);
            System.out.println("❌ Regressor failed quality gate: R²=" + String.format("%.3f", eval.r2()));
        }
    }

    @Test
    void testPipelinePerformance() {
        // Verify pipeline completes in reasonable time
        long startTime = System.currentTimeMillis();

        CreditModelTrainingPipeline.TrainingPipelineResult result =
                trainingPipeline.runPipeline(1000);

        long duration = System.currentTimeMillis() - startTime;

        assertThat(result.success()).isTrue();
        assertThat(duration).isLessThan(30_000); // Should complete in < 30 seconds

        System.out.println("Pipeline completed in " + duration + "ms");
    }

    @Test
    void testTrainTestSplit() {
        CreditModelTrainingPipeline.TrainingPipelineResult result =
                trainingPipeline.runPipeline(1500);

        // Verify 80/20 split
        int expectedTrain = 1200; // 80% of 1500
        int expectedTest = 300;   // 20% of 1500

        assertThat(result.trainSize()).isEqualTo(expectedTrain);
        assertThat(result.testSize()).isEqualTo(expectedTest);
        assertThat(result.trainSize() + result.testSize()).isEqualTo(1500);

        System.out.println("Train/Test split verified: " + result.trainSize() + "/" + result.testSize());
    }

    @Test
    void testModelPromotionLogic() {
        CreditModelTrainingPipeline.TrainingPipelineResult result =
                trainingPipeline.runPipeline(1000);

        // If classifier passed quality gate, should have model ID
        if (result.classifierEvaluation().passedQualityGate()) {
            assertThat(result.classifierModelId()).isNotNull();
        } else {
            // Note: Model ID may still be assigned even if not promoted
            // This depends on implementation
        }

        // If regressor passed quality gate, should have model ID
        if (result.regressorEvaluation().passedQualityGate()) {
            assertThat(result.regressorModelId()).isNotNull();
        }

        System.out.println("Classifier promoted: " + (result.classifierModelId() != null));
        System.out.println("Regressor promoted: " + (result.regressorModelId() != null));
    }
}
