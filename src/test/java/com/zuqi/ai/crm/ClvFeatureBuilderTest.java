package com.zuqi.ai.crm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.regression.Regressor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClvFeatureBuilderTest {

    private ClvFeatureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ClvFeatureBuilder();
    }

    private CustomerAnalyticsFeatures sample(double revenue12m) {
        return new CustomerAnalyticsFeatures(
                UUID.randomUUID(), UUID.randomUUID(),
                50_000.0, 200_000.0, 50_000.0, 120_000.0, revenue12m,
                2.5, 20_000.0, 0.1, 85.0, 30.0, 5, 0.7, 12,
                "retail", 10.0, 25.0
        );
    }

    @Test
    void getFeatureCount_returns12() {
        assertThat(builder.getFeatureCount()).isEqualTo(12);
    }

    @Test
    void buildExample_hasCorrectFeatureCount() {
        Example<Regressor> example = builder.buildExample(sample(200_000.0));

        assertThat(example).isNotNull();
        assertThat(example.size()).isEqualTo(12);
    }

    @Test
    void buildLabelledExample_targetSetCorrectly() {
        Example<Regressor> example = builder.buildLabelledExample(sample(180_000.0), 180_000.0);

        assertThat(example.getOutput().getValues()[0]).isEqualTo(180_000.0);
    }

    @Test
    void buildLabelledExample_negativeTarget_clampedToZero() {
        Example<Regressor> example = builder.buildLabelledExample(sample(0.0), -5_000.0);

        assertThat(example.getOutput().getValues()[0]).isEqualTo(0.0);
    }

    @Test
    void buildDataset_fromTwoExamples_hasSize2() {
        List<ClvFeatureBuilder.LabelledClvExample> examples = List.of(
                new ClvFeatureBuilder.LabelledClvExample(sample(100_000.0), 100_000.0),
                new ClvFeatureBuilder.LabelledClvExample(sample(200_000.0), 200_000.0)
        );

        MutableDataset<Regressor> dataset = builder.buildDataset(examples);

        assertThat(dataset.size()).isEqualTo(2);
    }

    @Test
    void buildDataset_empty_returnsEmptyDataset() {
        MutableDataset<Regressor> dataset = builder.buildDataset(List.of());

        assertThat(dataset.size()).isEqualTo(0);
    }
}
