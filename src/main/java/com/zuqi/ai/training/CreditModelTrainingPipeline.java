package com.zuqi.ai.training;

import com.zuqi.ai.credit.CreditLimitRegressor;
import com.zuqi.ai.credit.CreditMlFeatureBuilder;
import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.domain.ai.AIModelRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tribuo.Dataset;
import org.tribuo.Model;
import org.tribuo.classification.Label;
import org.tribuo.regression.Regressor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates end-to-end ML training pipeline for credit scoring models.
 *
 * Pipeline flow:
 * 1. Generate synthetic merchant data
 * 2. Split into train/test (80/20)
 * 3. Train XGBoost classifier
 * 4. Evaluate classifier (quality gate: AUC > 0.75)
 * 5. Train XGBoost regressor
 * 6. Evaluate regressor (quality gate: R² > 0.70)
 * 7. Promote models to ACTIVE if quality gates pass
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 5
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditModelTrainingPipeline {

    private final SyntheticMerchantDataGenerator syntheticDataGenerator;
    private final CreditClassifierTrainer classifierTrainer;
    private final CreditLimitRegressorTrainer regressorTrainer;
    private final ModelEvaluator modelEvaluator;
    private final CreditMlFeatureBuilder featureBuilder;
    private final CreditLimitRegressor creditLimitRegressor;
    private final ModelRegistry modelRegistry;

    /**
     * Run complete training pipeline for both models.
     *
     * @param datasetSize Number of synthetic merchants to generate (minimum 1000)
     * @return Pipeline result with both model evaluations
     */
    @Transactional
    public TrainingPipelineResult runPipeline(int datasetSize) {
        log.info("=".repeat(80));
        log.info("Starting Credit Model Training Pipeline");
        log.info("Dataset size: {}", datasetSize);
        log.info("=".repeat(80));

        long pipelineStartTime = System.currentTimeMillis();

        try {
            // Step 1: Generate synthetic data
            log.info("Step 1/7: Generating synthetic merchant data...");
            List<SyntheticMerchant> syntheticMerchants = syntheticDataGenerator.generateDataset(datasetSize);

            // Validate dataset
            SyntheticMerchantDataGenerator.DatasetQualityReport qualityReport =
                    syntheticDataGenerator.validateDataset(syntheticMerchants);

            if (!qualityReport.isValid()) {
                throw new IllegalStateException("Synthetic dataset failed quality validation: " + qualityReport);
            }

            log.info("✅ Step 1 complete: Generated {} merchants with {:.1f}% default rate",
                    syntheticMerchants.size(), qualityReport.defaultRate() * 100);

            // Step 2: Split into train/test (80/20)
            log.info("Step 2/7: Splitting data into train/test sets (80/20)...");
            int trainSize = (int) (syntheticMerchants.size() * 0.8);
            List<SyntheticMerchant> trainData = syntheticMerchants.subList(0, trainSize);
            List<SyntheticMerchant> testData = syntheticMerchants.subList(trainSize, syntheticMerchants.size());

            log.info("✅ Step 2 complete: Train={} examples, Test={} examples",
                    trainData.size(), testData.size());

            // Step 3: Train classifier
            log.info("Step 3/7: Training XGBoost credit classifier...");
            classifierTrainer.validateDataset(trainData);
            Model<Label> classifierModel = classifierTrainer.train(trainData);
            log.info("✅ Step 3 complete: Classifier trained");

            // Step 4: Evaluate classifier
            log.info("Step 4/7: Evaluating credit classifier...");
            Dataset<Label> classifierTestDataset = buildClassifierTestDataset(testData);
            ModelEvaluator.ClassifierEvaluationResult classifierEval =
                    modelEvaluator.evaluateClassifier(classifierModel, classifierTestDataset);

            if (classifierEval.passedQualityGate()) {
                log.info("✅ Step 4 complete: Classifier evaluation PASSED quality gate");
            } else {
                log.warn("⚠️ Step 4 complete: Classifier evaluation FAILED quality gate");
            }

            // Step 5: Train regressor
            log.info("Step 5/7: Training XGBoost credit limit regressor...");
            regressorTrainer.validateDataset(trainData);
            Model<Regressor> regressorModel = regressorTrainer.train(trainData);
            log.info("✅ Step 5 complete: Regressor trained");

            // Step 6: Evaluate regressor
            log.info("Step 6/7: Evaluating credit limit regressor...");
            Dataset<Regressor> regressorTestDataset = buildRegressorTestDataset(testData);
            ModelEvaluator.RegressorEvaluationResult regressorEval =
                    modelEvaluator.evaluateRegressor(regressorModel, regressorTestDataset);

            if (regressorEval.passedQualityGate()) {
                log.info("✅ Step 6 complete: Regressor evaluation PASSED quality gate");
            } else {
                log.warn("⚠️ Step 6 complete: Regressor evaluation FAILED quality gate");
            }

            // Step 7: Promote models to ACTIVE if quality gates pass
            log.info("Step 7/7: Promoting models to production...");
            UUID classifierId = null;
            UUID regressorId = null;

            if (classifierEval.passedQualityGate()) {
                classifierId = promoteClassifier(classifierModel, classifierEval, trainData.size());
                log.info("✅ Classifier promoted to ACTIVE: {}", classifierId);
            } else {
                log.warn("❌ Classifier NOT promoted (failed quality gate)");
            }

            if (regressorEval.passedQualityGate()) {
                regressorId = promoteRegressor(regressorModel, regressorEval, trainData.size());
                log.info("✅ Regressor promoted to ACTIVE: {}", regressorId);
            } else {
                log.warn("❌ Regressor NOT promoted (failed quality gate)");
            }

            log.info("✅ Step 7 complete: Model promotion finished");

            long pipelineDuration = System.currentTimeMillis() - pipelineStartTime;

            log.info("=".repeat(80));
            log.info("Training Pipeline Completed Successfully in {}ms", pipelineDuration);
            log.info("Classifier: {} (AUC={:.3f})",
                    classifierEval.passedQualityGate() ? "PROMOTED" : "NOT PROMOTED",
                    classifierEval.aucRoc());
            log.info("Regressor: {} (R²={:.3f})",
                    regressorEval.passedQualityGate() ? "PROMOTED" : "NOT PROMOTED",
                    regressorEval.r2());
            log.info("=".repeat(80));

            return TrainingPipelineResult.builder()
                    .success(true)
                    .datasetSize(datasetSize)
                    .trainSize(trainData.size())
                    .testSize(testData.size())
                    .classifierEvaluation(classifierEval)
                    .regressorEvaluation(regressorEval)
                    .classifierModelId(classifierId)
                    .regressorModelId(regressorId)
                    .durationMs(pipelineDuration)
                    .build();

        } catch (Exception e) {
            long pipelineDuration = System.currentTimeMillis() - pipelineStartTime;
            log.error("Training pipeline failed after {}ms: {}", pipelineDuration, e.getMessage(), e);

            return TrainingPipelineResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .durationMs(pipelineDuration)
                    .build();
        }
    }

    /**
     * Run training pipeline with pre-generated merchant data.
     *
     * This overload is used for retraining with real merchant outcomes
     * blended with synthetic data.
     *
     * @param merchants Pre-generated merchant data (real + synthetic)
     * @return Pipeline result
     */
    @Transactional
    public TrainingPipelineResult runPipeline(List<SyntheticMerchant> merchants) {
        log.info("=".repeat(80));
        log.info("Starting Credit Model Training Pipeline (with pre-generated data)");
        log.info("Dataset size: {}", merchants.size());
        log.info("=".repeat(80));

        long pipelineStartTime = System.currentTimeMillis();

        try {
            // Step 1: Validate dataset
            log.info("Step 1/7: Validating merchant data...");
            SyntheticMerchantDataGenerator.DatasetQualityReport qualityReport =
                    syntheticDataGenerator.validateDataset(merchants);

            if (!qualityReport.isValid()) {
                throw new IllegalStateException("Dataset failed quality validation: " + qualityReport);
            }

            log.info("✅ Step 1 complete: Validated {} merchants with {:.1f}% default rate",
                    merchants.size(), qualityReport.defaultRate() * 100);

            // Step 2: Split into train/test (80/20)
            log.info("Step 2/7: Splitting data into train/test sets (80/20)...");
            int trainSize = (int) (merchants.size() * 0.8);
            List<SyntheticMerchant> trainData = merchants.subList(0, trainSize);
            List<SyntheticMerchant> testData = merchants.subList(trainSize, merchants.size());

            log.info("✅ Step 2 complete: Train={} examples, Test={} examples",
                    trainData.size(), testData.size());

            // Step 3: Train classifier
            log.info("Step 3/7: Training XGBoost credit classifier...");
            classifierTrainer.validateDataset(trainData);
            Model<Label> classifierModel = classifierTrainer.train(trainData);
            log.info("✅ Step 3 complete: Classifier trained");

            // Step 4: Evaluate classifier
            log.info("Step 4/7: Evaluating credit classifier...");
            Dataset<Label> classifierTestDataset = buildClassifierTestDataset(testData);
            ModelEvaluator.ClassifierEvaluationResult classifierEval =
                    modelEvaluator.evaluateClassifier(classifierModel, classifierTestDataset);

            if (classifierEval.passedQualityGate()) {
                log.info("✅ Step 4 complete: Classifier evaluation PASSED quality gate");
            } else {
                log.warn("⚠️ Step 4 complete: Classifier evaluation FAILED quality gate");
            }

            // Step 5: Train regressor
            log.info("Step 5/7: Training XGBoost credit limit regressor...");
            regressorTrainer.validateDataset(trainData);
            Model<Regressor> regressorModel = regressorTrainer.train(trainData);
            log.info("✅ Step 5 complete: Regressor trained");

            // Step 6: Evaluate regressor
            log.info("Step 6/7: Evaluating credit limit regressor...");
            Dataset<Regressor> regressorTestDataset = buildRegressorTestDataset(testData);
            ModelEvaluator.RegressorEvaluationResult regressorEval =
                    modelEvaluator.evaluateRegressor(regressorModel, regressorTestDataset);

            if (regressorEval.passedQualityGate()) {
                log.info("✅ Step 6 complete: Regressor evaluation PASSED quality gate");
            } else {
                log.warn("⚠️ Step 6 complete: Regressor evaluation FAILED quality gate");
            }

            // Step 7: Promote models to ACTIVE if quality gates pass
            log.info("Step 7/7: Promoting models to production...");
            UUID classifierId = null;
            UUID regressorId = null;

            if (classifierEval.passedQualityGate()) {
                classifierId = promoteClassifier(classifierModel, classifierEval, trainData.size());
                log.info("✅ Classifier promoted to ACTIVE: {}", classifierId);
            } else {
                log.warn("❌ Classifier NOT promoted (failed quality gate)");
            }

            if (regressorEval.passedQualityGate()) {
                regressorId = promoteRegressor(regressorModel, regressorEval, trainData.size());
                log.info("✅ Regressor promoted to ACTIVE: {}", regressorId);
            } else {
                log.warn("❌ Regressor NOT promoted (failed quality gate)");
            }

            long durationMs = System.currentTimeMillis() - pipelineStartTime;

            log.info("=".repeat(80));
            log.info("Training Pipeline COMPLETE");
            log.info("Duration: {}ms ({:.1f}s)", durationMs, durationMs / 1000.0);
            log.info("=".repeat(80));

            return TrainingPipelineResult.builder()
                    .success(true)
                    .errorMessage(null)
                    .datasetSize(merchants.size())
                    .trainSize(trainData.size())
                    .testSize(testData.size())
                    .classifierEvaluation(classifierEval)
                    .regressorEvaluation(regressorEval)
                    .classifierModelId(classifierId)
                    .regressorModelId(regressorId)
                    .durationMs(durationMs)
                    .build();

        } catch (Exception e) {
            log.error("Training pipeline failed: {}", e.getMessage(), e);

            long durationMs = System.currentTimeMillis() - pipelineStartTime;

            return TrainingPipelineResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .datasetSize(merchants.size())
                    .durationMs(durationMs)
                    .build();
        }
    }

    /**
     * Build Tribuo test dataset for classifier.
     */
    private Dataset<Label> buildClassifierTestDataset(List<SyntheticMerchant> testData) {
        List<MerchantFeatures> features = testData.stream()
                .map(SyntheticMerchant::features)
                .toList();

        List<Boolean> labels = testData.stream()
                .map(SyntheticMerchant::didDefault)
                .toList();

        return featureBuilder.buildClassificationDataset(features, labels);
    }

    /**
     * Build Tribuo test dataset for regressor.
     */
    private Dataset<Regressor> buildRegressorTestDataset(List<SyntheticMerchant> testData) {
        List<MerchantFeatures> features = testData.stream()
                .map(SyntheticMerchant::features)
                .toList();

        List<BigDecimal> targetLimits = testData.stream()
                .map(m -> creditLimitRegressor.calculateIdealLimit(
                        m.features(),
                        m.defaultProbability()
                ))
                .toList();

        return featureBuilder.buildRegressionDataset(features, targetLimits);
    }

    /**
     * Promote classifier to ACTIVE in model registry.
     */
    private UUID promoteClassifier(Model<Label> model,
                                     ModelEvaluator.ClassifierEvaluationResult eval,
                                     int trainingSize) {
        String modelName = "credit_classifier";

        try {
            // 1. Register model in TRAINING status
            Map<String, Object> hyperparameters = Map.of(
                "algorithm", "xgboost_classification",
                "num_rounds", 100,
                "max_depth", 6,
                "eta", 0.1,
                "subsample", 0.8,
                "colsample_bytree", 0.8,
                "min_child_weight", 3,
                "gamma", 0.1
            );

            AIModelRegistry registry = modelRegistry.registerModel(
                modelName,
                "xgboost_classification",
                hyperparameters,
                "training_pipeline"
            );

            // 2. Serialize model
            byte[] modelBinary = serializeModel(model);

            // 3. Update with training results
            Map<String, Object> performanceMetrics = Map.of(
                "accuracy", eval.accuracy(),
                "precision", eval.precision(),
                "recall", eval.recall(),
                "f1_score", eval.f1Score(),
                "auc_roc", eval.aucRoc(),
                "training_samples", trainingSize
            );

            Map<String, Object> featureColumns = Map.of(
                "feature_names", featureBuilder.getFeatureNames(),
                "feature_count", featureBuilder.getFeatureCount()
            );

            modelRegistry.updateModelAfterTraining(
                registry.getId(),
                performanceMetrics,
                modelBinary,
                featureColumns
            );

            // 4. Promote to ACTIVE if passed quality gate
            modelRegistry.promoteToActive(registry.getId());

            log.info("Successfully promoted classifier to ACTIVE: {} v{} (AUC={:.3f})",
                    modelName, registry.getModelVersion(), eval.aucRoc());

            return registry.getId();

        } catch (Exception e) {
            log.error("Failed to promote classifier: {}", e.getMessage(), e);
            throw new RuntimeException("Classifier promotion failed", e);
        }
    }

    /**
     * Promote regressor to ACTIVE in model registry.
     */
    private UUID promoteRegressor(Model<Regressor> model,
                                    ModelEvaluator.RegressorEvaluationResult eval,
                                    int trainingSize) {
        String modelName = "credit_limit_regressor";

        try {
            // 1. Register model in TRAINING status
            Map<String, Object> hyperparameters = Map.of(
                "algorithm", "xgboost_regression",
                "num_rounds", 100,
                "max_depth", 6,
                "eta", 0.1,
                "subsample", 0.8,
                "colsample_bytree", 0.8,
                "min_child_weight", 3,
                "gamma", 0.1
            );

            AIModelRegistry registry = modelRegistry.registerModel(
                modelName,
                "xgboost_regression",
                hyperparameters,
                "training_pipeline"
            );

            // 2. Serialize model
            byte[] modelBinary = serializeModel(model);

            // 3. Update with training results
            Map<String, Object> performanceMetrics = Map.of(
                "rmse", eval.rmse(),
                "mae", eval.mae(),
                "r2", eval.r2(),
                "explained_variance", eval.explainedVariance(),
                "training_samples", trainingSize
            );

            Map<String, Object> featureColumns = Map.of(
                "feature_names", featureBuilder.getFeatureNames(),
                "feature_count", featureBuilder.getFeatureCount()
            );

            modelRegistry.updateModelAfterTraining(
                registry.getId(),
                performanceMetrics,
                modelBinary,
                featureColumns
            );

            // 4. Promote to ACTIVE if passed quality gate
            modelRegistry.promoteToActive(registry.getId());

            log.info("Successfully promoted regressor to ACTIVE: {} v{} (R²={:.3f})",
                    modelName, registry.getModelVersion(), eval.r2());

            return registry.getId();

        } catch (Exception e) {
            log.error("Failed to promote regressor: {}", e.getMessage(), e);
            throw new RuntimeException("Regressor promotion failed", e);
        }
    }

    /**
     * Serialize Tribuo model to byte array.
     */
    private byte[] serializeModel(Model<?> model) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to serialize model: {}", e.getMessage(), e);
            throw new RuntimeException("Model serialization failed", e);
        }
    }

    /**
     * Training pipeline result.
     */
    @lombok.Builder
    public record TrainingPipelineResult(
            boolean success,
            String errorMessage,
            int datasetSize,
            int trainSize,
            int testSize,
            ModelEvaluator.ClassifierEvaluationResult classifierEvaluation,
            ModelEvaluator.RegressorEvaluationResult regressorEvaluation,
            UUID classifierModelId,
            UUID regressorModelId,
            long durationMs
    ) {
    }
}
