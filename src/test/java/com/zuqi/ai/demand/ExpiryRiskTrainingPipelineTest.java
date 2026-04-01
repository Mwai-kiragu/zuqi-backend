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
import org.tribuo.regression.Regressor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpiryRiskTrainingPipelineTest {

    @Mock private ModelEvaluator modelEvaluator;
    @Mock private ModelRegistry modelRegistry;
    @Mock private Trainer<Regressor> xgBoostRegressionTrainer;
    @Mock private XGBoostHyperparameterTuner hyperparameterTuner;

    private SyntheticExpiryBatchGenerator realBatchGenerator;
    private ExpiryRiskFeatureBuilder realFeatureBuilder;
    private ExpiryRiskTrainingPipeline pipeline;

    private static final XGBoostHyperparameterTuner.TuningResult FIXED_TUNING =
            new XGBoostHyperparameterTuner.TuningResult(50, 0.2, 6, 0.15, "rmse", Map.of("eta", 0.2, "max_depth", 6));

    @BeforeEach
    void setUp() {
        realBatchGenerator = new SyntheticExpiryBatchGenerator();
        realFeatureBuilder = new ExpiryRiskFeatureBuilder();
        pipeline = new ExpiryRiskTrainingPipeline(
                realBatchGenerator, realFeatureBuilder,
                modelEvaluator, modelRegistry, xgBoostRegressionTrainer, hyperparameterTuner);
    }

    @Test
    void runPipeline_whenTunerThrows_returnsFailedResult() {
        when(hyperparameterTuner.tuneAndTrainRegressor(any()))
                .thenThrow(new RuntimeException("training failed"));

        ExpiryRiskTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("training failed");
        assertThat(result.r2()).isEqualTo(-1.0);
        assertThat(result.modelId()).isNull();
    }

    @Test
    void runPipeline_whenR2BelowGate_returnsFailed_andNeverPromotes() throws Exception {
        @SuppressWarnings("unchecked")
        org.tribuo.Model<Regressor> mockModel = mock(org.tribuo.Model.class);
        when(hyperparameterTuner.tuneAndTrainRegressor(any()))
                .thenReturn(new XGBoostHyperparameterTuner.TunedModel<>(mockModel, FIXED_TUNING));

        ModelEvaluator.RegressorEvaluationResult badEval =
                ModelEvaluator.RegressorEvaluationResult.builder()
                        .r2(0.10)
                        .mae(200.0)
                        .rmse(300.0)
                        .explainedVariance(0.10)
                        .passedQualityGate(false)
                        .build();
        when(modelEvaluator.evaluateRegressor(eq(mockModel), any())).thenReturn(badEval);

        ExpiryRiskTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.r2()).isEqualTo(0.10);
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
                new ExpiryRiskTrainingPipeline.TrainingResult(true, 0.82, 50.0, 7.0, id, null);

        assertThat(r.success()).isTrue();
        assertThat(r.r2()).isEqualTo(0.82);
        assertThat(r.mae()).isEqualTo(50.0);
        assertThat(r.rmse()).isEqualTo(7.0);
        assertThat(r.modelId()).isEqualTo(id);
        assertThat(r.errorMessage()).isNull();
    }

    @Test
    void trainingResult_failed_holdsErrorMessage() {
        ExpiryRiskTrainingPipeline.TrainingResult r =
                new ExpiryRiskTrainingPipeline.TrainingResult(false, -1.0, -1.0, -1.0, null, "R2 gate failed");

        assertThat(r.success()).isFalse();
        assertThat(r.modelId()).isNull();
        assertThat(r.errorMessage()).isEqualTo("R2 gate failed");
    }
}
