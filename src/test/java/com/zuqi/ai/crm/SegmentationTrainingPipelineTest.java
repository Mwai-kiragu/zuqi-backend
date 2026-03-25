package com.zuqi.ai.crm;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticDataOrchestrator;
import com.zuqi.domain.ai.AIModelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tribuo.Trainer;
import org.tribuo.clustering.ClusterID;

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
class SegmentationTrainingPipelineTest {

    @Mock private SyntheticDataOrchestrator orchestrator;
    @Mock private SyntheticCustomerAnalyticsFeatureBuilder featureBuilder;
    @Mock private SegmentationFeatureBuilder segmentationFeatureBuilder;
    @Mock private ModelRegistry modelRegistry;
    @Mock private Trainer<ClusterID> kMeansTrainer;

    private SegmentationTrainingPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new SegmentationTrainingPipeline(
                orchestrator, featureBuilder, segmentationFeatureBuilder,
                modelRegistry, kMeansTrainer);
    }

    @Test
    void trainerException_returnsFailure() {
        SyntheticDataBundle bundle = mock(SyntheticDataBundle.class);
        when(bundle.getMerchants()).thenReturn(List.of());
        when(orchestrator.generateBundle(any(SyntheticDataConfig.class))).thenReturn(bundle);

        org.tribuo.MutableDataset<ClusterID> emptyDataset = new org.tribuo.MutableDataset<>(
                new org.tribuo.provenance.SimpleDataSourceProvenance(
                        "test", new org.tribuo.clustering.ClusteringFactory()),
                new org.tribuo.clustering.ClusteringFactory());
        when(segmentationFeatureBuilder.buildDataset(any())).thenReturn(emptyDataset);
        when(kMeansTrainer.train(any())).thenThrow(new RuntimeException("KMeans failed"));

        SegmentationTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isNotNull();
    }

    @Test
    void orchestratorException_returnsFailure() {
        when(orchestrator.generateBundle(any(SyntheticDataConfig.class)))
                .thenThrow(new RuntimeException("Generation failed"));

        SegmentationTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.modelId()).isNull();
    }

    @Test
    void successPath_registersAndPromotesModel() {
        SyntheticDataBundle bundle = mock(SyntheticDataBundle.class);
        when(bundle.getMerchants()).thenReturn(List.of());
        when(orchestrator.generateBundle(any(SyntheticDataConfig.class))).thenReturn(bundle);

        org.tribuo.MutableDataset<ClusterID> emptyDataset = new org.tribuo.MutableDataset<>(
                new org.tribuo.provenance.SimpleDataSourceProvenance(
                        "test", new org.tribuo.clustering.ClusteringFactory()),
                new org.tribuo.clustering.ClusteringFactory());
        when(segmentationFeatureBuilder.buildDataset(any())).thenReturn(emptyDataset);
        when(segmentationFeatureBuilder.getFeatureCount()).thenReturn(9);

        @SuppressWarnings("unchecked")
        org.tribuo.Model<ClusterID> mockModel = mock(org.tribuo.Model.class);
        when(kMeansTrainer.train(any())).thenReturn(mockModel);

        UUID modelId = UUID.randomUUID();
        AIModelRegistry registryEntry = mock(AIModelRegistry.class);
        when(registryEntry.getId()).thenReturn(modelId);
        when(modelRegistry.registerModel(anyString(), anyString(), anyMap(), anyString()))
                .thenReturn(registryEntry);
        when(modelRegistry.promoteToActive(any())).thenReturn(registryEntry);

        SegmentationTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isTrue();
        assertThat(result.modelId()).isEqualTo(modelId);
    }
}
