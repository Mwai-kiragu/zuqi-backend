package com.zuqi.ai.cashflow;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.synthetic.SyntheticCashFlowFeatureBuilder;
import com.zuqi.ai.synthetic.generators.SyntheticCashFlowGenerator;
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
class CashFlowTrainingPipelineTest {

    @Mock private ModelEvaluator modelEvaluator;
    @Mock private ModelRegistry modelRegistry;
    @Mock private Trainer<Regressor> xgBoostRegressionTrainer;

    private CashFlowTrainingPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new CashFlowTrainingPipeline(
                new SyntheticCashFlowGenerator(),
                new SyntheticCashFlowFeatureBuilder(),
                new CashFlowFeatureBuilder(),
                modelEvaluator,
                modelRegistry,
                xgBoostRegressionTrainer);
    }

    @Test
    void runPipeline_whenTrainerThrows_returnsFailedResult() {
        when(xgBoostRegressionTrainer.train(any()))
                .thenThrow(new RuntimeException("XGBoost init failed"));

        CashFlowTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("XGBoost init failed");
        assertThat(result.rmse()).isEqualTo(-1.0);
        assertThat(result.r2()).isEqualTo(-1.0);
        assertThat(result.modelId()).isNull();
    }

    @Test
    void runPipeline_whenR2BelowGate_returnsFailedResult_neverPromotes() throws Exception {
        @SuppressWarnings("unchecked")
        org.tribuo.Model<Regressor> mockModel = mock(org.tribuo.Model.class);
        when(xgBoostRegressionTrainer.train(any())).thenReturn(mockModel);

        ModelEvaluator.RegressorEvaluationResult badEval =
                ModelEvaluator.RegressorEvaluationResult.builder()
                        .rmse(15_000.0)
                        .mae(12_000.0)
                        .r2(0.45)
                        .explainedVariance(0.48)
                        .passedQualityGate(false)
                        .build();
        when(modelEvaluator.evaluateRegressor(eq(mockModel), any())).thenReturn(badEval);

        CashFlowTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.r2()).isEqualTo(0.45);
        assertThat(result.errorMessage()).isNotNull();
        verify(modelRegistry, never()).promoteToActive(any());
        verify(modelRegistry, never()).registerModel(any(), any(), any(), any());
    }

    @Test
    void modelName_constant_isExpected() {
        assertThat(CashFlowTrainingPipeline.MODEL_NAME).isEqualTo("cash_flow_predictor");
    }

    @Test
    void trainingResult_record_holdsAllFields() {
        UUID id = UUID.randomUUID();
        CashFlowTrainingPipeline.TrainingResult r =
                new CashFlowTrainingPipeline.TrainingResult(true, 8_500.0, 0.82, id, null);

        assertThat(r.success()).isTrue();
        assertThat(r.rmse()).isEqualTo(8_500.0);
        assertThat(r.r2()).isEqualTo(0.82);
        assertThat(r.modelId()).isEqualTo(id);
        assertThat(r.errorMessage()).isNull();
    }

    @Test
    void trainingResult_failed_holdsErrorMessage() {
        CashFlowTrainingPipeline.TrainingResult r =
                new CashFlowTrainingPipeline.TrainingResult(false, -1.0, -1.0, null, "Failed R² gate");

        assertThat(r.success()).isFalse();
        assertThat(r.modelId()).isNull();
        assertThat(r.errorMessage()).isEqualTo("Failed R² gate");
    }
}
