package com.zuqi.ai.demand;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.synthetic.generators.SyntheticExpiryBatchGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tribuo.Trainer;
import org.tribuo.regression.Regressor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpiryRiskTrainingPipelineTest {

    @Mock private ModelEvaluator modelEvaluator;
    @Mock private ModelRegistry modelRegistry;
    @Mock private Trainer<Regressor> xgBoostRegressionTrainer;

    private SyntheticExpiryBatchGenerator realBatchGenerator;
    private ExpiryRiskFeatureBuilder realFeatureBuilder;
    private ExpiryRiskTrainingPipeline pipeline;

    @BeforeEach
    void setUp() {
        realBatchGenerator = new SyntheticExpiryBatchGenerator();
        realFeatureBuilder = new ExpiryRiskFeatureBuilder();
        pipeline = new ExpiryRiskTrainingPipeline(
                realBatchGenerator, realFeatureBuilder,
                modelEvaluator, modelRegistry, xgBoostRegressionTrainer);
    }

    @Test
    void runPipeline_whenExceptionFromTrainer_returnsFailedResult() {
        when(xgBoostRegressionTrainer.train(any()))
                .thenThrow(new RuntimeException("training failed"));

        ExpiryRiskTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("training failed");
        assertThat(result.rmse()).isEqualTo(-1.0);
        assertThat(result.modelId()).isNull();
    }

    @Test
    void runPipeline_whenRmseAboveGate_returnsFailed_andNeverPromotes() throws Exception {
        @SuppressWarnings("unchecked")
        org.tribuo.Model<Regressor> mockModel = mock(org.tribuo.Model.class);
        when(xgBoostRegressionTrainer.train(any())).thenReturn(mockModel);

        ModelEvaluator.RegressorEvaluationResult badEval =
                ModelEvaluator.RegressorEvaluationResult.builder()
                        .rmse(0.35)
                        .mae(0.28)
                        .r2(0.40)
                        .explainedVariance(0.42)
                        .passedQualityGate(false)
                        .build();
        when(modelEvaluator.evaluateRegressor(eq(mockModel), any())).thenReturn(badEval);

        ExpiryRiskTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.rmse()).isEqualTo(0.35);
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
                new ExpiryRiskTrainingPipeline.TrainingResult(true, 0.12, id, null);

        assertThat(r.success()).isTrue();
        assertThat(r.rmse()).isEqualTo(0.12);
        assertThat(r.modelId()).isEqualTo(id);
        assertThat(r.errorMessage()).isNull();
    }

    @Test
    void trainingResult_failed_holdsErrorMessage() {
        ExpiryRiskTrainingPipeline.TrainingResult r =
                new ExpiryRiskTrainingPipeline.TrainingResult(false, -1.0, null, "RMSE gate failed");

        assertThat(r.success()).isFalse();
        assertThat(r.modelId()).isNull();
        assertThat(r.errorMessage()).isEqualTo("RMSE gate failed");
    }
}
