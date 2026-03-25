package com.zuqi.ai.demand;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.pipeline.XGBoostHyperparameterTuner;
import com.zuqi.ai.synthetic.generators.SyntheticExpiryBatchGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tribuo.Trainer;
import org.tribuo.classification.Label;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpiryRiskTrainingPipelineTest {

    @Mock private ModelEvaluator modelEvaluator;
    @Mock private ModelRegistry modelRegistry;
    @Mock private Trainer<Label> xgBoostClassificationTrainer;
    @Mock private XGBoostHyperparameterTuner hyperparameterTuner;

    private SyntheticExpiryBatchGenerator realBatchGenerator;
    private ExpiryRiskFeatureBuilder realFeatureBuilder;
    private ExpiryRiskTrainingPipeline pipeline;

    private static final XGBoostHyperparameterTuner.TuningResult FIXED_TUNING =
            new XGBoostHyperparameterTuner.TuningResult(50, 0.2, 6, 0.15, "macro_f1", Map.of("eta", 0.2, "max_depth", 6));

    @BeforeEach
    void setUp() {
        realBatchGenerator = new SyntheticExpiryBatchGenerator();
        realFeatureBuilder = new ExpiryRiskFeatureBuilder();
        pipeline = new ExpiryRiskTrainingPipeline(
                realBatchGenerator, realFeatureBuilder,
                modelEvaluator, modelRegistry, xgBoostClassificationTrainer, hyperparameterTuner);
    }

    @Test
    void runPipeline_whenTunerThrows_returnsFailedResult() {
        when(hyperparameterTuner.tuneAndTrainClassifier(any(), anyString()))
                .thenThrow(new RuntimeException("training failed"));

        ExpiryRiskTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("training failed");
        assertThat(result.aucRoc()).isEqualTo(-1.0);
        assertThat(result.modelId()).isNull();
    }

    @Test
    void runPipeline_whenAucBelowGate_returnsFailed_andNeverPromotes() throws Exception {
        @SuppressWarnings("unchecked")
        org.tribuo.Model<Label> mockModel = mock(org.tribuo.Model.class);
        when(hyperparameterTuner.tuneAndTrainClassifier(any(), anyString()))
                .thenReturn(new XGBoostHyperparameterTuner.TunedModel<>(mockModel, FIXED_TUNING));

        ModelEvaluator.ClassifierEvaluationResult badEval =
                ModelEvaluator.ClassifierEvaluationResult.builder()
                        .accuracy(0.55)
                        .precision(0.50)
                        .recall(0.50)
                        .f1Score(0.50)
                        .aucRoc(0.50)
                        .passedQualityGate(false)
                        .confusionMatrix("")
                        .build();
        when(modelEvaluator.evaluateClassifier(eq(mockModel), any(), anyString())).thenReturn(badEval);

        ExpiryRiskTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.aucRoc()).isEqualTo(0.50);
        assertThat(result.errorMessage()).isNotNull();
        verify(modelRegistry, never()).promoteToActive(any());
        verify(modelRegistry, never()).registerModel(any(), any(), any(), any());
    }

    @Test
    void modelName_constant_isExpected() {
        assertThat(ExpiryRiskTrainingPipeline.MODEL_NAME).isEqualTo("expiry_risk_predictor");
    }

    @Test
    void trainingResult_record_holdsAllFields() {
        UUID id = UUID.randomUUID();
        ExpiryRiskTrainingPipeline.TrainingResult r =
                new ExpiryRiskTrainingPipeline.TrainingResult(true, 0.82, id, null);

        assertThat(r.success()).isTrue();
        assertThat(r.aucRoc()).isEqualTo(0.82);
        assertThat(r.modelId()).isEqualTo(id);
        assertThat(r.errorMessage()).isNull();
    }

    @Test
    void trainingResult_failed_holdsErrorMessage() {
        ExpiryRiskTrainingPipeline.TrainingResult r =
                new ExpiryRiskTrainingPipeline.TrainingResult(false, -1.0, null, "AUC gate failed");

        assertThat(r.success()).isFalse();
        assertThat(r.modelId()).isNull();
        assertThat(r.errorMessage()).isEqualTo("AUC gate failed");
    }
}
