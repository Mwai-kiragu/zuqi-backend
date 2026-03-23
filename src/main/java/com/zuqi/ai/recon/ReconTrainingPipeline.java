package com.zuqi.ai.recon;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.pipeline.XGBoostHyperparameterTuner;
import com.zuqi.ai.synthetic.SyntheticReconFeatureBuilder;
import com.zuqi.ai.synthetic.SyntheticReconFeatureBuilder.LabelledReconExample;
import com.zuqi.ai.synthetic.generators.SyntheticBankStatementGenerator;
import com.zuqi.ai.synthetic.dto.SyntheticBankStatementLine;
import com.zuqi.ai.synthetic.dto.SyntheticPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tribuo.Dataset;
import org.tribuo.Model;
import org.tribuo.Trainer;
import org.tribuo.classification.Label;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Training pipeline for the bank reconciliation classifier (Model #11).
 *
 * Pipeline:
 * 1. Generate synthetic bank statement lines (from synthetic payments)
 * 2. Build labelled (MATCH / NO_MATCH) feature pairs
 * 3. Split 80/20
 * 4. Train XGBoost classifier
 * 5. Evaluate AUC (gate: >= 0.75)
 * 6. Promote to ACTIVE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconTrainingPipeline {

    public static final String MODEL_NAME = "bank_recon_matcher";
    private static final double AUC_GATE = 0.75;
    private static final int PAYMENT_COUNT = 600;

    private final SyntheticBankStatementGenerator statementGenerator;
    private final SyntheticReconFeatureBuilder syntheticFeatureBuilder;
    private final ReconFeatureBuilder featureBuilder;
    private final ModelEvaluator modelEvaluator;
    private final ModelRegistry modelRegistry;
    private final Trainer<Label> xgBoostClassificationTrainer;
    private final XGBoostHyperparameterTuner hyperparameterTuner;

    @Transactional
    public TrainingResult runPipeline() {
        log.info("=== Starting Bank Recon Training Pipeline ===");
        long start = System.currentTimeMillis();

        try {
            // Step 1: Generate synthetic payments and statement lines
            List<SyntheticPayment> payments = generateSyntheticPayments(PAYMENT_COUNT);
            List<SyntheticBankStatementLine> lines =
                    statementGenerator.generate(payments, 42L);

            // Step 2: Build labelled examples
            List<LabelledReconExample> examples =
                    syntheticFeatureBuilder.buildLabelledExamples(lines, payments);

            if (examples.size() < 50) {
                return new TrainingResult(false, 0.0, null,
                        "Insufficient training examples: " + examples.size());
            }

            // Step 3: Split 80/20
            int trainSize = (int) (examples.size() * 0.8);
            List<LabelledReconExample> trainExamples = examples.subList(0, trainSize);
            List<LabelledReconExample> testExamples = examples.subList(trainSize, examples.size());

            // Step 4: Hyperparameter tuning + Train
            Dataset<Label> trainDataset = featureBuilder.buildDataset(trainExamples);
            XGBoostHyperparameterTuner.TunedModel<Label> tunedModel =
                    hyperparameterTuner.tuneAndTrainClassifier(trainDataset, ReconFeatureBuilder.LABEL_MATCH);
            Model<Label> model = tunedModel.model();
            XGBoostHyperparameterTuner.TuningResult tuning = tunedModel.tuning();
            log.info("Training complete on {} examples (rounds={} eta={} maxDepth={})",
                    trainSize, tuning.bestNumRounds(), tuning.bestEta(), tuning.bestMaxDepth());

            // Step 5: Evaluate
            Dataset<Label> testDataset = featureBuilder.buildDataset(testExamples);
            ModelEvaluator.ClassifierEvaluationResult eval =
                    modelEvaluator.evaluateClassifier(model, testDataset, ReconFeatureBuilder.LABEL_MATCH);

            boolean passed = eval.aucRoc() >= AUC_GATE;
            log.info("{} AUC={} (gate={})",
                    passed ? "PASSED" : "FAILED",
                    String.format("%.4f", eval.aucRoc()), AUC_GATE);

            if (!passed) {
                return new TrainingResult(false, eval.aucRoc(), null, "Failed AUC gate");
            }

            // Step 6: Promote
            UUID modelId = promoteModel(model, eval, trainSize, tuning);

            long duration = System.currentTimeMillis() - start;
            log.info("=== Bank Recon Pipeline complete in {}ms, modelId={} ===", duration, modelId);
            return new TrainingResult(true, eval.aucRoc(), modelId, null);

        } catch (Exception e) {
            log.error("Bank recon training pipeline failed: {}", e.getMessage(), e);
            return new TrainingResult(false, -1.0, null, e.getMessage());
        }
    }

    // ── Synthetic payment generation ─────────────────────────────────────────

    private List<SyntheticPayment> generateSyntheticPayments(int count) {
        Random rng = new Random(42L);
        List<SyntheticPayment> payments = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            double amount = 5_000 + rng.nextDouble() * 195_000; // KES 5k–200k
            int daysAgo = rng.nextInt(180);
            boolean isPartial = rng.nextDouble() < 0.15;
            boolean isDefault = rng.nextDouble() < 0.05;

            String method = rng.nextDouble() < 0.6 ? "MPESA" : "BANK_TRANSFER";

            payments.add(new SyntheticPayment(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    BigDecimal.valueOf(isPartial ? amount * 0.5 : amount)
                            .setScale(2, java.math.RoundingMode.HALF_UP),
                    LocalDateTime.now().minusDays(daysAgo),
                    method,
                    rng.nextInt(30),
                    isPartial,
                    isDefault
            ));
        }
        return payments;
    }

    // ── Model promotion ───────────────────────────────────────────────────────

    private UUID promoteModel(Model<Label> model,
                               ModelEvaluator.ClassifierEvaluationResult eval,
                               int trainSize,
                               XGBoostHyperparameterTuner.TuningResult tuning) throws Exception {
        byte[] modelBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            modelBytes = baos.toByteArray();
        }

        Map<String, Object> hyperparams = new HashMap<>();
        hyperparams.put("algorithm", "xgboost_classification");
        hyperparams.put("tuned_num_rounds", tuning.bestNumRounds());
        hyperparams.put("tuned_eta", tuning.bestEta());
        hyperparams.put("tuned_max_depth", tuning.bestMaxDepth());
        hyperparams.put("tuning_cv_auc", tuning.bestScore());

        com.zuqi.domain.ai.AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "xgboost_classification", hyperparams, "training_pipeline");

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("auc_roc", eval.aucRoc());
        metrics.put("accuracy", eval.accuracy());
        metrics.put("precision", eval.precision());
        metrics.put("recall", eval.recall());
        metrics.put("f1_score", eval.f1Score());
        metrics.put("training_size", trainSize);

        modelRegistry.updateModelAfterTraining(registry.getId(), metrics, modelBytes,
                Map.of("feature_count", ReconFeatureBuilder.FEATURE_COUNT));
        modelRegistry.promoteToActive(registry.getId());

        return registry.getId();
    }

    public record TrainingResult(
            boolean success,
            double aucRoc,
            UUID modelId,
            String errorMessage
    ) {}
}
