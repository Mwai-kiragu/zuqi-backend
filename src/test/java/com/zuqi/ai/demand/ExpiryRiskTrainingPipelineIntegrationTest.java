package com.zuqi.ai.demand;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.pipeline.XGBoostHyperparameterTuner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tribuo.Model;
import org.tribuo.classification.Label;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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
    void runPipeline_withFailingAucGate_returnsFailure_andNeverPromotes() {
        Model<Label> mockModel = mock(Model.class);
        XGBoostHyperparameterTuner.TuningResult tuning =
                new XGBoostHyperparameterTuner.TuningResult(50, 0.1, 4, 0.50, "macro_f1", java.util.Map.of());
        when(hyperparameterTuner.tuneAndTrainClassifier(any(), anyString()))
                .thenReturn(new XGBoostHyperparameterTuner.TunedModel<>(mockModel, tuning));

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
    }

    @Test
    void runPipeline_withTrainerException_returnsFailure() {
        when(hyperparameterTuner.tuneAndTrainClassifier(any(), anyString()))
                .thenThrow(new RuntimeException("XGBoost training error"));

        ExpiryRiskTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("XGBoost training error");
        assertThat(result.aucRoc()).isEqualTo(-1.0);
    }

    @Test
    void modelName_matchesDataPhaseTrackerConstant() {
        assertThat(ExpiryRiskTrainingPipeline.MODEL_NAME)
                .isEqualTo("expiry_risk_predictor");
    }
}
