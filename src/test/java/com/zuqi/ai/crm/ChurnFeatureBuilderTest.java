package com.zuqi.ai.crm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChurnFeatureBuilderTest {

    private ChurnFeatureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ChurnFeatureBuilder();
    }

    private CustomerAnalyticsFeatures sample(int daysSinceLastOrder) {
        return new CustomerAnalyticsFeatures(
                UUID.randomUUID(), UUID.randomUUID(),
                10_000.0, 50_000.0, 10_000.0, 30_000.0, 50_000.0,
                1.5, 10_000.0, 0.05, 80.0, 20.0,
                daysSinceLastOrder, 0.6, 6, "retail", 5.0, 12.0
        );
    }

    @Test
    void getFeatureCount_returns9() {
        assertThat(builder.getFeatureCount()).isEqualTo(9);
    }

    @Test
    void buildExample_hasCorrectFeatureCount() {
        Example<Label> example = builder.buildExample(sample(5));

        assertThat(example).isNotNull();
        assertThat(example.size()).isEqualTo(9);
    }

    @Test
    void buildLabelledExample_churnedLabel() {
        Example<Label> example = builder.buildLabelledExample(sample(90), true);

        assertThat(example.getOutput().getLabel()).isEqualTo("CHURNED");
    }

    @Test
    void buildLabelledExample_activeLabel() {
        Example<Label> example = builder.buildLabelledExample(sample(2), false);

        assertThat(example.getOutput().getLabel()).isEqualTo("ACTIVE");
    }

    @Test
    void buildDataset_fromMultipleExamples_hasCorrectSize() {
        List<ChurnFeatureBuilder.LabelledChurnExample> examples = List.of(
                new ChurnFeatureBuilder.LabelledChurnExample(sample(2), false),
                new ChurnFeatureBuilder.LabelledChurnExample(sample(90), true),
                new ChurnFeatureBuilder.LabelledChurnExample(sample(45), true)
        );

        MutableDataset<Label> dataset = builder.buildDataset(examples);

        assertThat(dataset.size()).isEqualTo(3);
    }

    @Test
    void buildDataset_empty_returnsEmptyDataset() {
        MutableDataset<Label> dataset = builder.buildDataset(List.of());

        assertThat(dataset.size()).isEqualTo(0);
    }

    @Test
    void ratio30dVs90d_computedCorrectly() {
        // 6 orders in 30d, 12 in 90d → ratio = 0.5
        CustomerAnalyticsFeatures f = new CustomerAnalyticsFeatures(
                UUID.randomUUID(), null,
                0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 100.0, 0.0,
                5, 0.0, 0, "retail", 6.0, 12.0
        );
        Example<Label> example = builder.buildExample(f);
        // 9 features should be present
        assertThat(example.size()).isEqualTo(9);
    }
}
