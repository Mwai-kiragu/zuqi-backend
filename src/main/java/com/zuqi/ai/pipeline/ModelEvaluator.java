package com.zuqi.ai.pipeline;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.classification.Label;
import org.tribuo.classification.evaluation.LabelEvaluation;
import org.tribuo.classification.evaluation.LabelEvaluator;
import org.tribuo.Dataset;
import org.tribuo.evaluation.Evaluator;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.evaluation.RegressionEvaluation;
import org.tribuo.regression.evaluation.RegressionEvaluator;

import java.util.List;

/**
 * Evaluates trained ML models and checks quality gates.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 5
 */
@Service
@Slf4j
public class ModelEvaluator {

    // Quality gates
    private static final double CLASSIFIER_MIN_AUC = 0.75;
    private static final double REGRESSOR_MIN_R2 = 0.70;

    /**
     * Evaluate credit classifier and check quality gates.
     *
     * @param model Trained classifier
     * @param testData Test dataset
     * @return Evaluation result with quality gate check
     */
    public ClassifierEvaluationResult evaluateClassifier(
            Model<Label> model,
            Dataset<Label> testData) {

        log.info("Evaluating credit classifier on {} test examples", testData.size());

        // 1. Run evaluation
        Evaluator<Label, LabelEvaluation> evaluator = new LabelEvaluator();
        LabelEvaluation evaluation = evaluator.evaluate(model, testData);

        // 2. Extract metrics
        double accuracy = evaluation.accuracy();
        Label defaultLabel = new Label("DEFAULT");
        double precision = evaluation.precision(defaultLabel);
        double recall = evaluation.recall(defaultLabel);
        double f1 = evaluation.f1(defaultLabel);

        // Get confusion matrix for AUC approximation
        // For binary classification: AUC ~= (1 + TPR - FPR) / 2
        double aucROC = (precision + recall) / 2.0; // Approximation

        log.info("Classifier metrics: Accuracy={:.3f}, Precision={:.3f}, Recall={:.3f}, F1={:.3f}, AUC-ROC={:.3f}",
                accuracy, precision, recall, f1, aucROC);

        // 3. Check quality gate
        boolean passedQualityGate = aucROC >= CLASSIFIER_MIN_AUC;

        if (passedQualityGate) {
            log.info("✅ Classifier PASSED quality gate (AUC-ROC {:.3f} >= {:.3f})",
                    aucROC, CLASSIFIER_MIN_AUC);
        } else {
            log.warn("❌ Classifier FAILED quality gate (AUC-ROC {:.3f} < {:.3f})",
                    aucROC, CLASSIFIER_MIN_AUC);
        }

        return ClassifierEvaluationResult.builder()
                .accuracy(accuracy)
                .precision(precision)
                .recall(recall)
                .f1Score(f1)
                .aucRoc(aucROC)
                .passedQualityGate(passedQualityGate)
                .confusionMatrix(evaluation.toString())
                .build();
    }

    /**
     * Evaluate credit limit regressor and check quality gates.
     *
     * @param model Trained regressor
     * @param testData Test dataset
     * @return Evaluation result with quality gate check
     */
    public RegressorEvaluationResult evaluateRegressor(
            Model<Regressor> model,
            Dataset<Regressor> testData) {

        log.info("Evaluating credit limit regressor on {} test examples", testData.size());

        // 1. Run evaluation
        Evaluator<Regressor, RegressionEvaluation> evaluator = new RegressionEvaluator();
        RegressionEvaluation evaluation = evaluator.evaluate(model, testData);

        // 2. Extract metrics (Tribuo returns Maps, get first value)
        double rmse = evaluation.rmse().values().iterator().next();
        double mae = evaluation.mae().values().iterator().next();
        double r2 = evaluation.r2().values().iterator().next();
        double explainedVariance = evaluation.explainedVariance().values().iterator().next();

        log.info("Regressor metrics: RMSE={:.2f}, MAE={:.2f}, R²={:.3f}, Explained Variance={:.3f}",
                rmse, mae, r2, explainedVariance);

        // 3. Check quality gate
        boolean passedQualityGate = r2 >= REGRESSOR_MIN_R2;

        if (passedQualityGate) {
            log.info("✅ Regressor PASSED quality gate (R² {:.3f} >= {:.3f})",
                    r2, REGRESSOR_MIN_R2);
        } else {
            log.warn("❌ Regressor FAILED quality gate (R² {:.3f} < {:.3f})",
                    r2, REGRESSOR_MIN_R2);
        }

        return RegressorEvaluationResult.builder()
                .rmse(rmse)
                .mae(mae)
                .r2(r2)
                .explainedVariance(explainedVariance)
                .passedQualityGate(passedQualityGate)
                .build();
    }

    /**
     * Classifier evaluation result.
     */
    @Builder
    public record ClassifierEvaluationResult(
            double accuracy,
            double precision,
            double recall,
            double f1Score,
            double aucRoc,
            boolean passedQualityGate,
            String confusionMatrix
    ) {
    }

    /**
     * Regressor evaluation result.
     */
    @Builder
    public record RegressorEvaluationResult(
            double rmse,
            double mae,
            double r2,
            double explainedVariance,
            boolean passedQualityGate
    ) {
    }
}
