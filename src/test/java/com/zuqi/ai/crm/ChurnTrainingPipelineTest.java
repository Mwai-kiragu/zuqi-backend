package com.zuqi.ai.crm;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.pipeline.XGBoostHyperparameterTuner;
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
import org.tribuo.classification.Label;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChurnTrainingPipelineTest {

    @Mock private SyntheticDataOrchestrator orchestrator;
    @Mock private SyntheticCustomerAnalyticsFeatureBuilder featureBuilder;
    @Mock private ChurnFeatureBuilder churnFeatureBuilder;
    @Mock private ModelEvaluator modelEvaluator;
    @Mock private ModelRegistry modelRegistry;
    @Mock private Trainer<Label> xgBoostClassificationTrainer;
    @Mock private XGBoostHyperparameterTuner hyperparameterTuner;

    private ChurnTrainingPipeline pipeline;

    private static final XGBoostHyperparameterTuner.TuningResult FIXED_TUNING =
            new XGBoostHyperparameterTuner.TuningResult(50, 0.2, 6, 0.15, "auc", Map.of("eta", 0.2, "max_depth", 6));

    @BeforeEach
    void setUp() {
        pipeline = new ChurnTrainingPipeline(
                orchestrator, featureBuilder, churnFeatureBuilder,
                modelEvaluator, modelRegistry, xgBoostClassificationTrainer, hyperparameterTuner);
    }

    @Test
    void orchestratorException_returnsFailure() {
        when(orchestrator.generateBundle(any(SyntheticDataConfig.class)))
                .thenThrow(new RuntimeException("Failed"));

        ChurnTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.aucRoc()).isEqualTo(-1.0);
    }

    @Test
    void trainerException_returnsFailure() {
        SyntheticDataBundle bundle = mock(SyntheticDataBundle.class);
        when(bundle.getMerchants()).thenReturn(List.of());
        when(orchestrator.generateBundle(any(SyntheticDataConfig.class))).thenReturn(bundle);

        var emptyDataset = new org.tribuo.MutableDataset<Label>(
                new org.tribuo.provenance.SimpleDataSourceProvenance(
                        "test", new org.tribuo.classification.LabelFactory()),
                new org.tribuo.classification.LabelFactory());
        when(churnFeatureBuilder.buildDataset(any())).thenReturn(emptyDataset);
        when(hyperparameterTuner.tuneAndTrainClassifier(any(), anyString()))
                .thenThrow(new RuntimeException("Train failed"));

        ChurnTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
    }

    @Test
    void aucGateFailure_returnsFailure() {
        SyntheticDataBundle bundle = mock(SyntheticDataBundle.class);
        when(bundle.getMerchants()).thenReturn(List.of());
        when(orchestrator.generateBundle(any(SyntheticDataConfig.class))).thenReturn(bundle);

        var emptyDataset = new org.tribuo.MutableDataset<Label>(
                new org.tribuo.provenance.SimpleDataSourceProvenance(
                        "test", new org.tribuo.classification.LabelFactory()),
                new org.tribuo.classification.LabelFactory());
        when(churnFeatureBuilder.buildDataset(any())).thenReturn(emptyDataset);

        @SuppressWarnings("unchecked")
        Model<Label> mockModel = mock(Model.class);
        when(hyperparameterTuner.tuneAndTrainClassifier(any(), anyString()))
                .thenReturn(new XGBoostHyperparameterTuner.TunedModel<>(mockModel, FIXED_TUNING));

        // AUC = 0.55 < gate of 0.70
        ModelEvaluator.ClassifierEvaluationResult eval =
                ModelEvaluator.ClassifierEvaluationResult.builder()
                        .accuracy(0.6).precision(0.55).recall(0.55)
                        .f1Score(0.55).aucRoc(0.55).passedQualityGate(false)
                        .confusionMatrix("").build();
        when(modelEvaluator.evaluateClassifier(any(), any(), any())).thenReturn(eval);

        ChurnTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.aucRoc()).isEqualTo(0.55);
    }

    @Test
    void successPath_aucPassesGate_registersModel() {
        SyntheticDataBundle bundle = mock(SyntheticDataBundle.class);
        when(bundle.getMerchants()).thenReturn(List.of());
        when(orchestrator.generateBundle(any(SyntheticDataConfig.class))).thenReturn(bundle);

        var emptyDataset = new org.tribuo.MutableDataset<Label>(
                new org.tribuo.provenance.SimpleDataSourceProvenance(
                        "test", new org.tribuo.classification.LabelFactory()),
                new org.tribuo.classification.LabelFactory());
        when(churnFeatureBuilder.buildDataset(any())).thenReturn(emptyDataset);
        when(churnFeatureBuilder.getFeatureCount()).thenReturn(9);

        @SuppressWarnings("unchecked")
        Model<Label> mockModel = mock(Model.class);
        when(hyperparameterTuner.tuneAndTrainClassifier(any(), anyString()))
                .thenReturn(new XGBoostHyperparameterTuner.TunedModel<>(mockModel, FIXED_TUNING));

        ModelEvaluator.ClassifierEvaluationResult eval =
                ModelEvaluator.ClassifierEvaluationResult.builder()
                        .accuracy(0.80).precision(0.78).recall(0.75)
                        .f1Score(0.76).aucRoc(0.77).passedQualityGate(true)
                        .confusionMatrix("").build();
        when(modelEvaluator.evaluateClassifier(any(), any(), any())).thenReturn(eval);

        UUID modelId = UUID.randomUUID();
        AIModelRegistry registryEntry = mock(AIModelRegistry.class);
        when(registryEntry.getId()).thenReturn(modelId);
        when(modelRegistry.registerModel(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(registryEntry);
        when(modelRegistry.promoteToActive(any())).thenReturn(registryEntry);

        ChurnTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isTrue();
        assertThat(result.modelId()).isEqualTo(modelId);
        assertThat(result.aucRoc()).isEqualTo(0.77);
    }
}
