package com.zuqi.ai.demand;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.pipeline.XGBoostHyperparameterTuner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tribuo.Dataset;
import org.tribuo.Model;
import org.tribuo.regression.Regressor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test: ExpiryRiskTrainingPipeline wires correctly in the Spring context.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExpiryRiskTrainingPipelineIntegrationTest {

    @Autowired
    private ExpiryRiskTrainingPipeline pipeline;

    @MockitoBean
    private XGBoostHyperparameterTuner hyperparameterTuner;

    @MockitoBean
    private ModelEvaluator modelEvaluator;

    @MockitoBean
    private ModelRegistry modelRegistry;

    @Test
    void contextLoads_pipelineBeanIsPresent() {
        assertThat(pipeline).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void runPipeline_withFailingRmseGate_returnsFailure_andNeverPromotes() {
        Model<Regressor> mockModel = mock(Model.class);
        XGBoostHyperparameterTuner.TuningResult tuning =
                new XGBoostHyperparameterTuner.TuningResult(50, 0.1, 4, 0.35, "rmse", java.util.Map.of());
        when(hyperparameterTuner.tuneAndTrainRegressor(any()))
                .thenReturn(new XGBoostHyperparameterTuner.TunedModel<>(mockModel, tuning));

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
    }

    @Test
    void runPipeline_withTrainerException_returnsFailure() {
        when(hyperparameterTuner.tuneAndTrainRegressor(any()))
                .thenThrow(new RuntimeException("XGBoost training error"));

        ExpiryRiskTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("XGBoost training error");
        assertThat(result.rmse()).isEqualTo(-1.0);
    }

    @Test
    void modelName_matchesDataPhaseTrackerConstant() {
        assertThat(ExpiryRiskTrainingPipeline.MODEL_NAME)
                .isEqualTo("expiry_risk_predictor");
    }
}
