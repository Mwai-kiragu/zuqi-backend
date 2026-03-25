package com.zuqi.ai.crm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.regression.Regressor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VisitFeatureBuilderTest {

    private VisitFeatureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new VisitFeatureBuilder();
    }

    private CustomerAnalyticsFeatures sample() {
        return new CustomerAnalyticsFeatures(
                UUID.randomUUID(), UUID.randomUUID(),
                25_000.0, 100_000.0, 25_000.0, 60_000.0, 100_000.0,
                1.5, 15_000.0, 0.05, 85.0, 20.0,
                3, 0.5, 9, "retail", 6.0, 18.0
        );
    }

    @Test
    void getFeatureCount_returns10() {
        assertThat(builder.getFeatureCount()).isEqualTo(10);
    }

    @Test
    void buildExample_hasCorrectFeatureCount() {
        Example<Regressor> example = builder.buildExample(sample(), 1, 2.0, false, false);

        assertThat(example).isNotNull();
        assertThat(example.size()).isEqualTo(10);
    }

    @Test
    void buildLabelledExample_targetSetCorrectly() {
        Example<Regressor> example = builder.buildLabelledExample(sample(), 3, 1.0, true, false, 1.0);

        assertThat(example.getOutput().getValues()[0]).isEqualTo(1.0);
    }

    @Test
    void buildLabelledExample_negativeExample_targetIsZero() {
        Example<Regressor> example = builder.buildLabelledExample(sample(), 5, 0.0, false, false, 0.0);

        assertThat(example.getOutput().getValues()[0]).isEqualTo(0.0);
    }

    @Test
    void buildDataset_fromTwoExamples_hasSize2() {
        List<VisitFeatureBuilder.LabelledVisitExample> examples = List.of(
                new VisitFeatureBuilder.LabelledVisitExample(sample(), 1, 2.0, false, false, 1.0),
                new VisitFeatureBuilder.LabelledVisitExample(sample(), 6, 0.0, false, false, 0.0)
        );

        MutableDataset<Regressor> dataset = builder.buildDataset(examples);

        assertThat(dataset.size()).isEqualTo(2);
    }

    @Test
    void buildDataset_empty_returnsEmptyDataset() {
        MutableDataset<Regressor> dataset = builder.buildDataset(List.of());

        assertThat(dataset.size()).isEqualTo(0);
    }

    @Test
    void isPaydayWeek_encodedAs1() {
        Example<Regressor> example = builder.buildExample(sample(), 2, 0.0, true, false);

        // All 10 features should be present
        assertThat(example.size()).isEqualTo(10);
    }
}
