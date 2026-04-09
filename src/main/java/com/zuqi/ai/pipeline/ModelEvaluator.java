package com.zuqi.ai.pipeline;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Model;
import org.tribuo.classification.Label;
import org.tribuo.classification.evaluation.LabelEvaluation;
import org.tribuo.classification.evaluation.LabelEvaluator;
import org.tribuo.Dataset;
import org.tribuo.evaluation.Evaluator;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.evaluation.RegressionEvaluation;
import org.tribuo.regression.evaluation.RegressionEvaluator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates trained ML models and checks quality gates.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 5
 */
@Service
@Slf4j
public class ModelEvaluator {

    // Quality gates
    /** Macro-F1 threshold for multi-class XGBoost classifiers (churn, stockout, payment distress). */
    private static final double CLASSIFIER_MIN_F1          = 0.60;
    private static final double REGRESSOR_MIN_R2            = 0.20;
    private static final double SEGMENTATION_MIN_SILHOUETTE = 0.30;
    /** F1 for the ANOMALY class on LibSVM one-class detectors (shrinkage, payment anomaly). */
    private static final double ANOMALY_MIN_F1              = 0.50;

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
        return evaluateClassifier(model, testData, null);
    }

    public ClassifierEvaluationResult evaluateClassifier(
            Model<Label> model,
            Dataset<Label> testData,
            String positiveClassName) {

        log.info("Evaluating classifier on {} test examples (positiveClass={})",
                testData.size(), positiveClassName != null ? positiveClassName : "macro");

        // 1. Run evaluation
        Evaluator<Label, LabelEvaluation> evaluator = new LabelEvaluator();
        LabelEvaluation evaluation = evaluator.evaluate(model, testData);

        // 2. Extract metrics — use named positive class when provided, else macro averages
        double accuracy = evaluation.accuracy();
        double precision, recall, f1;
        if (positiveClassName != null) {
            Label positiveLabel = new Label(positiveClassName);
            precision = evaluation.precision(positiveLabel);
            recall    = evaluation.recall(positiveLabel);
            f1        = evaluation.f1(positiveLabel);
        } else {
            precision = evaluation.macroAveragedPrecision();
            recall    = evaluation.macroAveragedRecall();
            f1        = evaluation.macroAveragedF1();
        }

        // aucRoc kept as an informational metric (stored in model registry, used for logging).
        // It is NOT the quality gate — see passedQualityGate below.
        double aucRoc = (precision + recall) / 2.0;

        log.info("Classifier metrics: Accuracy={:.3f}, Precision={:.3f}, Recall={:.3f}, F1={:.3f}, AUC(approx)={:.3f}",
                accuracy, precision, recall, f1, aucRoc);

        // 3. Quality gate: macro-F1 ≥ 0.60 (was formerly the (precision+recall)/2 AUC proxy ≥ 0.75)
        boolean passedQualityGate = f1 >= CLASSIFIER_MIN_F1;

        if (passedQualityGate) {
            log.info("✅ Classifier PASSED quality gate (macro-F1 {:.3f} >= {:.3f})",
                    f1, CLASSIFIER_MIN_F1);
        } else {
            log.warn("❌ Classifier FAILED quality gate (macro-F1 {:.3f} < {:.3f})",
                    f1, CLASSIFIER_MIN_F1);
        }

        return ClassifierEvaluationResult.builder()
                .accuracy(accuracy)
                .precision(precision)
                .recall(recall)
                .f1Score(f1)
                .aucRoc(aucRoc)
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
     * Evaluate a LibSVM one-class anomaly detector using F1 score.
     *
     * <p>One-class classifiers are evaluated on a held-out set containing both normal and
     * synthetically generated anomalous examples. FPR and TPR are computed separately by
     * the training pipeline; this method derives precision/recall/F1 from those counts.
     *
     * <p>F1 is computed for the ANOMALY class:
     * <ul>
     *   <li>TP = truePositiveRate × anomalousTestSize</li>
     *   <li>FP = falsePositiveRate × normalTestSize</li>
     *   <li>FN = (1 − truePositiveRate) × anomalousTestSize</li>
     *   <li>F1 = 2·TP / (2·TP + FP + FN)</li>
     * </ul>
     * Quality gate: F1 ≥ 0.50.
     *
     * @param falsePositiveRate fraction of normal examples wrongly flagged as anomaly
     * @param truePositiveRate  fraction of anomalous examples correctly detected
     * @param normalTestSize    number of normal examples in test set
     * @param anomalousTestSize number of anomalous examples in test set
     * @return evaluation result with quality gate outcome
     */
    public AnomalyEvaluationResult evaluateAnomalyDetector(
            double falsePositiveRate,
            double truePositiveRate,
            int normalTestSize,
            int anomalousTestSize) {

        double tp = truePositiveRate  * anomalousTestSize;
        double fp = falsePositiveRate * normalTestSize;

        double precision = (tp + fp) > 0 ? tp / (tp + fp) : 0.0;
        double recall    = truePositiveRate; // recall for ANOMALY class = TPR
        double f1        = (precision + recall) > 0
                ? 2.0 * precision * recall / (precision + recall)
                : 0.0;

        log.info("Anomaly detector metrics: FPR={:.3f}, TPR={:.3f}, Precision={:.3f}, Recall={:.3f}, F1={:.3f}",
                falsePositiveRate, truePositiveRate, precision, recall, f1);

        boolean passedQualityGate = f1 >= ANOMALY_MIN_F1;
        if (passedQualityGate) {
            log.info("✅ Anomaly detector PASSED quality gate (F1 {:.3f} >= {:.3f})",
                    f1, ANOMALY_MIN_F1);
        } else {
            log.warn("❌ Anomaly detector FAILED quality gate (F1 {:.3f} < {:.3f})",
                    f1, ANOMALY_MIN_F1);
        }

        return AnomalyEvaluationResult.builder()
                .falsePositiveRate(falsePositiveRate)
                .truePositiveRate(truePositiveRate)
                .precision(precision)
                .recall(recall)
                .f1Score(f1)
                .passedQualityGate(passedQualityGate)
                .build();
    }

    /**
     * Evaluate a K-Means clustering model using silhouette score.
     *
     * <p>Silhouette score s(i) = (b(i) - a(i)) / max(a(i), b(i)) where:
     * <ul>
     *   <li>a(i) — mean Euclidean distance from point i to all other points in its cluster</li>
     *   <li>b(i) — mean Euclidean distance from point i to all points in the nearest other cluster</li>
     * </ul>
     * Mean silhouette ∈ [-1, 1]. Quality gate: mean silhouette ≥ 0.30.
     *
     * @param clusterAssignments cluster ID for each example (same order as featureMatrix)
     * @param featureMatrix      raw feature vectors, shape [n_examples][n_features]
     * @param numClusters        expected number of clusters (used for validation)
     * @return evaluation result with quality gate outcome
     */
    public SegmentationEvaluationResult evaluateSegmentation(
            int[] clusterAssignments,
            double[][] featureMatrix,
            int numClusters) {

        int n = clusterAssignments.length;
        log.info("Evaluating segmentation: n={}, k={}", n, numClusters);

        if (n == 0) {
            log.warn("Empty dataset for segmentation evaluation");
            return SegmentationEvaluationResult.builder()
                    .silhouetteScore(0.0).passedQualityGate(false)
                    .numClusters(0).minClusterSize(0).build();
        }

        // Group indices by cluster
        Map<Integer, List<Integer>> clusters = new HashMap<>();
        for (int i = 0; i < n; i++) {
            clusters.computeIfAbsent(clusterAssignments[i], k -> new java.util.ArrayList<>()).add(i);
        }

        int minClusterSize = clusters.values().stream()
                .mapToInt(List::size).min().orElse(0);
        int actualClusters = clusters.size();

        // Compute silhouette for each point
        double[] silhouettes = new double[n];
        for (int i = 0; i < n; i++) {
            int ci = clusterAssignments[i];
            List<Integer> sameCluster = clusters.get(ci);

            double a = meanDistanceTo(featureMatrix[i], sameCluster, featureMatrix, i);

            double b = Double.MAX_VALUE;
            for (Map.Entry<Integer, List<Integer>> entry : clusters.entrySet()) {
                if (entry.getKey() == ci) continue;
                double d = meanDistanceTo(featureMatrix[i], entry.getValue(), featureMatrix, -1);
                if (d < b) b = d;
            }

            if (sameCluster.size() == 1) {
                silhouettes[i] = 0.0; // singleton cluster: silhouette undefined, treat as 0
            } else {
                double maxAB = Math.max(a, b);
                silhouettes[i] = maxAB == 0.0 ? 0.0 : (b - a) / maxAB;
            }
        }

        double meanSilhouette = Arrays.stream(silhouettes).average().orElse(0.0);
        boolean passedQualityGate = meanSilhouette >= SEGMENTATION_MIN_SILHOUETTE;

        log.info("Segmentation metrics: silhouette={:.3f}, clusters={}, minClusterSize={}",
                meanSilhouette, actualClusters, minClusterSize);
        if (passedQualityGate) {
            log.info("✅ Segmentation PASSED quality gate (silhouette {:.3f} >= {:.3f})",
                    meanSilhouette, SEGMENTATION_MIN_SILHOUETTE);
        } else {
            log.warn("❌ Segmentation FAILED quality gate (silhouette {:.3f} < {:.3f})",
                    meanSilhouette, SEGMENTATION_MIN_SILHOUETTE);
        }

        return SegmentationEvaluationResult.builder()
                .silhouetteScore(meanSilhouette)
                .passedQualityGate(passedQualityGate)
                .numClusters(actualClusters)
                .minClusterSize(minClusterSize)
                .build();
    }

    private double meanDistanceTo(double[] point, List<Integer> indices,
                                   double[][] matrix, int excludeIdx) {
        if (indices.isEmpty()) return 0.0;
        double sum = 0.0;
        int count = 0;
        for (int idx : indices) {
            if (idx == excludeIdx) continue;
            sum += euclidean(point, matrix[idx]);
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private double euclidean(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
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
            /** Informational only — stored in model registry metrics. Gate uses f1Score, not this. */
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

    /**
     * Anomaly detector (LibSVM one-class) evaluation result.
     */
    @Builder
    public record AnomalyEvaluationResult(
            double falsePositiveRate,
            double truePositiveRate,
            double precision,
            double recall,
            double f1Score,
            boolean passedQualityGate
    ) {
    }

    /**
     * Segmentation (K-Means) evaluation result.
     */
    @Builder
    public record SegmentationEvaluationResult(
            double silhouetteScore,
            boolean passedQualityGate,
            int numClusters,
            int minClusterSize
    ) {
    }
}
