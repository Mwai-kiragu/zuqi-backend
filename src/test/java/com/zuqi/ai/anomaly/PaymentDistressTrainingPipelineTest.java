package com.zuqi.ai.anomaly;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.domain.ai.AIModelRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentDistressTrainingPipeline}.
 *
 * Covers: successful pipeline run, quality gate pass/fail, pipeline failure handling,
 * model promotion, and result record structure.
 */
@ExtendWith(MockitoExtension.class)
class PaymentDistressTrainingPipelineTest {

    @Mock private PaymentDistressModelTrainer   modelTrainer;
    @Mock private PaymentDistressFeatureBuilder featureBuilder;
    @Mock private ModelEvaluator                modelEvaluator;
    @Mock private ModelRegistry                modelRegistry;

    @InjectMocks
    private PaymentDistressTrainingPipeline pipeline;

    // ── successful pipeline with quality gate pass ──────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void runPipeline_whenQualityGatePasses_promotesModel() {
        // Arrange
        Model<Label> mockModel = mock(Model.class);
        when(modelTrainer.train(anyList(), anyList())).thenReturn(mockModel);

        MutableDataset<Label> mockDataset = mock(MutableDataset.class);
        when(featureBuilder.buildDataset(anyList(), anyList())).thenReturn(mockDataset);

        ModelEvaluator.ClassifierEvaluationResult eval =
                ModelEvaluator.ClassifierEvaluationResult.builder()
                        .aucRoc(0.85)
                        .accuracy(0.82)
                        .precision(0.80)
                        .recall(0.78)
                        .f1Score(0.79)
                        .passedQualityGate(true)
                        .confusionMatrix("mock")
                        .build();
        when(modelEvaluator.evaluateClassifier(eq(mockModel), eq(mockDataset))).thenReturn(eval);

        UUID registryId = UUID.randomUUID();
        AIModelRegistry registryEntry = new AIModelRegistry();
        registryEntry.setId(registryId);
        when(modelRegistry.registerModel(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(registryEntry);
        when(featureBuilder.getFeatureCount()).thenReturn(20);

        // Act
        PaymentDistressTrainingPipeline.TrainingPipelineResult result = pipeline.runPipeline();

        // Assert
        assertThat(result.success()).isTrue();
        assertThat(result.passedQualityGate()).isTrue();
        assertThat(result.aucRoc()).isEqualTo(0.85);
        assertThat(result.accuracy()).isEqualTo(0.82);
        assertThat(result.modelId()).isEqualTo(registryId);
        assertThat(result.trainSize()).isGreaterThan(0);
        assertThat(result.testSize()).isGreaterThan(0);
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);

        verify(modelRegistry).registerModel(eq("payment_distress_classifier"),
                eq("xgboost_classification"), anyMap(), eq("training_pipeline"));
        verify(modelRegistry).promoteToActive(registryId);
    }

    // ── quality gate failure — model NOT promoted ───────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void runPipeline_whenQualityGateFails_doesNotPromoteModel() {
        Model<Label> mockModel = mock(Model.class);
        when(modelTrainer.train(anyList(), anyList())).thenReturn(mockModel);

        MutableDataset<Label> mockDataset = mock(MutableDataset.class);
        when(featureBuilder.buildDataset(anyList(), anyList())).thenReturn(mockDataset);

        ModelEvaluator.ClassifierEvaluationResult eval =
                ModelEvaluator.ClassifierEvaluationResult.builder()
                        .aucRoc(0.60)
                        .accuracy(0.55)
                        .passedQualityGate(false)
                        .build();
        when(modelEvaluator.evaluateClassifier(any(), any())).thenReturn(eval);

        PaymentDistressTrainingPipeline.TrainingPipelineResult result = pipeline.runPipeline();

        assertThat(result.success()).isTrue();
        assertThat(result.passedQualityGate()).isFalse();
        assertThat(result.modelId()).isNull();

        verify(modelRegistry, never()).registerModel(any(), any(), any(), any());
        verify(modelRegistry, never()).promoteToActive(any());
    }

    // ── trainer throws exception — pipeline returns failure ─────────────

    @Test
    void runPipeline_whenTrainerThrows_returnsFailure() {
        when(modelTrainer.train(anyList(), anyList()))
                .thenThrow(new RuntimeException("XGBoost training failed"));

        PaymentDistressTrainingPipeline.TrainingPipelineResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("XGBoost training failed");
        assertThat(result.modelId()).isNull();
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    // ── result record structure ─────────────────────────────────────────

    @Test
    void trainingPipelineResult_builderCreatesCorrectValues() {
        UUID id = UUID.randomUUID();

        PaymentDistressTrainingPipeline.TrainingPipelineResult result =
                PaymentDistressTrainingPipeline.TrainingPipelineResult.builder()
                        .success(true)
                        .trainSize(480)
                        .testSize(120)
                        .aucRoc(0.88)
                        .accuracy(0.85)
                        .passedQualityGate(true)
                        .modelId(id)
                        .durationMs(1234)
                        .build();

        assertThat(result.success()).isTrue();
        assertThat(result.trainSize()).isEqualTo(480);
        assertThat(result.testSize()).isEqualTo(120);
        assertThat(result.aucRoc()).isEqualTo(0.88);
        assertThat(result.accuracy()).isEqualTo(0.85);
        assertThat(result.passedQualityGate()).isTrue();
        assertThat(result.modelId()).isEqualTo(id);
        assertThat(result.durationMs()).isEqualTo(1234);
        assertThat(result.errorMessage()).isNull();
    }

    // ── train/test split proportions ────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void runPipeline_usesCorrectTrainTestSplit() {
        Model<Label> mockModel = mock(Model.class);
        when(modelTrainer.train(anyList(), anyList())).thenReturn(mockModel);

        MutableDataset<Label> mockDataset = mock(MutableDataset.class);
        when(featureBuilder.buildDataset(anyList(), anyList())).thenReturn(mockDataset);

        ModelEvaluator.ClassifierEvaluationResult eval =
                ModelEvaluator.ClassifierEvaluationResult.builder()
                        .aucRoc(0.50)
                        .accuracy(0.50)
                        .passedQualityGate(false)
                        .build();
        when(modelEvaluator.evaluateClassifier(any(), any())).thenReturn(eval);

        PaymentDistressTrainingPipeline.TrainingPipelineResult result = pipeline.runPipeline();

        // 600 merchants: 80% train = 480, 20% test = 120
        assertThat(result.trainSize()).isEqualTo(480);
        assertThat(result.testSize()).isEqualTo(120);
    }
}
