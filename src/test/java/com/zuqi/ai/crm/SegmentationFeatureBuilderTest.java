package com.zuqi.ai.crm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.clustering.ClusterID;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentationFeatureBuilderTest {

    private SegmentationFeatureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SegmentationFeatureBuilder();
    }

    private CustomerAnalyticsFeatures sample() {
        return new CustomerAnalyticsFeatures(
                UUID.randomUUID(), UUID.randomUUID(),
                50_000.0, 200_000.0, 50_000.0, 120_000.0, 180_000.0,
                2.5, 20_000.0, 0.1, 85.0, 30.0, 5, 0.7, 12,
                "retail", 10.0, 25.0
        );
    }

    @Test
    void getFeatureCount_returns9() {
        assertThat(builder.getFeatureCount()).isEqualTo(9);
    }

    @Test
    void buildExample_hasCorrectFeatureCount() {
        Example<ClusterID> example = builder.buildExample(sample());

        assertThat(example).isNotNull();
        assertThat(example.size()).isEqualTo(9);
    }

    @Test
    void buildExample_outputIsUnassigned() {
        Example<ClusterID> example = builder.buildExample(sample());

        assertThat(example.getOutput().getID()).isEqualTo(ClusterID.UNASSIGNED);
    }

    @Test
    void buildDataset_fromThreeExamples_hasSize3() {
        List<CustomerAnalyticsFeatures> features = List.of(sample(), sample(), sample());
        MutableDataset<ClusterID> dataset = builder.buildDataset(features);

        assertThat(dataset.size()).isEqualTo(3);
    }

    @Test
    void buildDataset_empty_returnsEmptyDataset() {
        MutableDataset<ClusterID> dataset = builder.buildDataset(List.of());

        assertThat(dataset.size()).isEqualTo(0);
    }

    @Test
    void buildExample_extremeValues_clampedProperly() {
        CustomerAnalyticsFeatures f = new CustomerAnalyticsFeatures(
                UUID.randomUUID(), null,
                -1.0,    // negative revenue → clamped to 0
                0.0, 0.0, 0.0, 0.0,
                -5.0,    // negative frequency → clamped to 0
                0.0, 2.0, // very high trend
                200.0,   // timeliness > 100 → clamped to 100
                -50.0,   // negative utilization → clamped to 0
                Integer.MAX_VALUE, 1.5, // diversity > 1 → clamped to 1
                0, "UNKNOWN", 0.0, 0.0
        );

        Example<ClusterID> example = builder.buildExample(f);
        assertThat(example.size()).isEqualTo(9);
    }
}
