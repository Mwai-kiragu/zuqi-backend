package com.zuqi.ai.recon;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.synthetic.SyntheticReconFeatureBuilder;
import com.zuqi.ai.synthetic.generators.SyntheticBankStatementGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tribuo.Trainer;
import org.tribuo.classification.Label;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconTrainingPipelineTest {

    @Mock private ModelEvaluator modelEvaluator;
    @Mock private ModelRegistry modelRegistry;
    @Mock private Trainer<Label> xgBoostClassificationTrainer;

    private ReconTrainingPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new ReconTrainingPipeline(
                new SyntheticBankStatementGenerator(),
                new SyntheticReconFeatureBuilder(),
                new ReconFeatureBuilder(),
                modelEvaluator,
                modelRegistry,
                xgBoostClassificationTrainer);
    }

    @Test
    void runPipeline_whenTrainerThrows_returnsFailedResult() {
        when(xgBoostClassificationTrainer.train(any()))
                .thenThrow(new RuntimeException("XGBoost training failure"));

        ReconTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("XGBoost training failure");
        assertThat(result.aucRoc()).isEqualTo(-1.0);
        assertThat(result.modelId()).isNull();
    }

    @Test
    void runPipeline_whenAucBelowGate_returnsFailedResult_neverPromotes() throws Exception {
        @SuppressWarnings("unchecked")
        org.tribuo.Model<Label> mockModel = mock(org.tribuo.Model.class);
        when(xgBoostClassificationTrainer.train(any())).thenReturn(mockModel);

        ModelEvaluator.ClassifierEvaluationResult badEval =
                ModelEvaluator.ClassifierEvaluationResult.builder()
                        .aucRoc(0.60)
                        .accuracy(0.70)
                        .precision(0.65)
                        .recall(0.68)
                        .f1Score(0.665)
                        .passedQualityGate(false)
                        .build();
        when(modelEvaluator.evaluateClassifier(eq(mockModel), any(), anyString()))
                .thenReturn(badEval);

        ReconTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.aucRoc()).isEqualTo(0.60);
        assertThat(result.errorMessage()).isNotNull();
        verify(modelRegistry, never()).promoteToActive(any());
        verify(modelRegistry, never()).registerModel(any(), any(), any(), any());
    }

    @Test
    void modelName_constant_isExpected() {
        assertThat(ReconTrainingPipeline.MODEL_NAME).isEqualTo("bank_recon_matcher");
    }

    @Test
    void trainingResult_record_holdsAllFields() {
        UUID id = UUID.randomUUID();
        ReconTrainingPipeline.TrainingResult r =
                new ReconTrainingPipeline.TrainingResult(true, 0.92, id, null);

        assertThat(r.success()).isTrue();
        assertThat(r.aucRoc()).isEqualTo(0.92);
        assertThat(r.modelId()).isEqualTo(id);
        assertThat(r.errorMessage()).isNull();
    }

    @Test
    void trainingResult_failed_holdsErrorMessage() {
        ReconTrainingPipeline.TrainingResult r =
                new ReconTrainingPipeline.TrainingResult(false, -1.0, null, "Failed AUC gate");

        assertThat(r.success()).isFalse();
        assertThat(r.modelId()).isNull();
        assertThat(r.errorMessage()).isEqualTo("Failed AUC gate");
    }
}
