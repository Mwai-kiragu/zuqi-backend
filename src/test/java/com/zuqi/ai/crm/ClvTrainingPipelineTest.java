package com.zuqi.ai.crm;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticDataOrchestrator;
import com.zuqi.domain.ai.AIModelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tribuo.Model;
import org.tribuo.Trainer;
import org.tribuo.regression.Regressor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClvTrainingPipelineTest {

    @Mock private SyntheticDataOrchestrator orchestrator;
    @Mock private SyntheticCustomerAnalyticsFeatureBuilder featureBuilder;
    @Mock private ClvFeatureBuilder clvFeatureBuilder;
    @Mock private ModelEvaluator modelEvaluator;
    @Mock private ModelRegistry modelRegistry;
    @Mock private Trainer<Regressor> xgBoostRegressionTrainer;

    private ClvTrainingPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new ClvTrainingPipeline(
                orchestrator, featureBuilder, clvFeatureBuilder,
                modelEvaluator, modelRegistry, xgBoostRegressionTrainer);
    }

    @Test
    void trainerException_returnsFailure() {
        SyntheticDataBundle bundle = mock(SyntheticDataBundle.class);
        when(bundle.getMerchants()).thenReturn(List.of());
        when(orchestrator.generateBundle(any(SyntheticDataConfig.class))).thenReturn(bundle);

        var emptyDataset = new org.tribuo.MutableDataset<Regressor>(
                new org.tribuo.provenance.SimpleDataSourceProvenance(
                        "test", new org.tribuo.regression.RegressionFactory()),
                new org.tribuo.regression.RegressionFactory());
        when(clvFeatureBuilder.buildDataset(any())).thenReturn(emptyDataset);
        when(xgBoostRegressionTrainer.train(any())).thenThrow(new RuntimeException("Train failed"));

        ClvTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isNotNull();
    }

    @Test
    void orchestratorException_returnsFailure() {
        when(orchestrator.generateBundle(any(SyntheticDataConfig.class)))
                .thenThrow(new RuntimeException("Orchestrator failed"));

        ClvTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.rmse()).isEqualTo(-1.0);
    }

    @Test
    void rmseGateFailure_returnsFailure() {
        SyntheticDataBundle bundle = mock(SyntheticDataBundle.class);
        when(bundle.getMerchants()).thenReturn(List.of());
        when(orchestrator.generateBundle(any(SyntheticDataConfig.class))).thenReturn(bundle);

        var emptyDataset = new org.tribuo.MutableDataset<Regressor>(
                new org.tribuo.provenance.SimpleDataSourceProvenance(
                        "test", new org.tribuo.regression.RegressionFactory()),
                new org.tribuo.regression.RegressionFactory());
        when(clvFeatureBuilder.buildDataset(any())).thenReturn(emptyDataset);

        @SuppressWarnings("unchecked")
        Model<Regressor> mockModel = mock(Model.class);
        when(xgBoostRegressionTrainer.train(any())).thenReturn(mockModel);

        // RMSE = 100000 > gate of 50000
        ModelEvaluator.RegressorEvaluationResult eval = ModelEvaluator.RegressorEvaluationResult.builder()
                .rmse(100_000.0).mae(50_000.0).r2(0.5).explainedVariance(0.5).passedQualityGate(false)
                .build();
        when(modelEvaluator.evaluateRegressor(any(), any())).thenReturn(eval);

        ClvTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.rmse()).isEqualTo(100_000.0);
    }

    @Test
    void successPath_rmsePassesGate_registersModel() {
        SyntheticDataBundle bundle = mock(SyntheticDataBundle.class);
        when(bundle.getMerchants()).thenReturn(List.of());
        when(orchestrator.generateBundle(any(SyntheticDataConfig.class))).thenReturn(bundle);

        var emptyDataset = new org.tribuo.MutableDataset<Regressor>(
                new org.tribuo.provenance.SimpleDataSourceProvenance(
                        "test", new org.tribuo.regression.RegressionFactory()),
                new org.tribuo.regression.RegressionFactory());
        when(clvFeatureBuilder.buildDataset(any())).thenReturn(emptyDataset);
        when(clvFeatureBuilder.getFeatureCount()).thenReturn(12);

        @SuppressWarnings("unchecked")
        Model<Regressor> mockModel = mock(Model.class);
        when(xgBoostRegressionTrainer.train(any())).thenReturn(mockModel);

        ModelEvaluator.RegressorEvaluationResult eval = ModelEvaluator.RegressorEvaluationResult.builder()
                .rmse(20_000.0).mae(10_000.0).r2(0.85).explainedVariance(0.85).passedQualityGate(true)
                .build();
        when(modelEvaluator.evaluateRegressor(any(), any())).thenReturn(eval);

        UUID modelId = UUID.randomUUID();
        AIModelRegistry registryEntry = mock(AIModelRegistry.class);
        when(registryEntry.getId()).thenReturn(modelId);
        when(modelRegistry.registerModel(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(registryEntry);
        when(modelRegistry.promoteToActive(any())).thenReturn(registryEntry);

        ClvTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isTrue();
        assertThat(result.modelId()).isEqualTo(modelId);
        assertThat(result.rmse()).isEqualTo(20_000.0);
    }
}
