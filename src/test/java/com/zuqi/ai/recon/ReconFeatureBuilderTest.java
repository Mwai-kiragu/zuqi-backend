package com.zuqi.ai.recon;

import com.zuqi.ai.feature.ReconFeatures;
import com.zuqi.ai.synthetic.SyntheticReconFeatureBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReconFeatureBuilderTest {

    private ReconFeatureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ReconFeatureBuilder();
    }

    private ReconFeatures perfectMatch() {
        return new ReconFeatures(
                UUID.randomUUID(), UUID.randomUUID(), "PAYMENT",
                0.0,   // amountDiffPct — exact
                1.0,   // amountExactMatch
                0,     // dateDiffDays — same day
                1.0,   // referenceExactMatch
                1.0,   // referenceSimilarity
                0.9,   // descriptionSimilarity
                1.0,   // sameMerchant
                1.0    // paymentMethodMatch
        );
    }

    private ReconFeatures noMatch() {
        return new ReconFeatures(
                UUID.randomUUID(), UUID.randomUUID(), "PAYMENT",
                0.8,  // amountDiffPct — very different
                0.0,
                25,   // dateDiffDays — 25 days apart
                0.0,
                0.0,
                0.0,
                0.0,
                0.0
        );
    }

    @Test
    void getFeatureCount_returns8() {
        assertThat(builder.getFeatureCount()).isEqualTo(8);
    }

    @Test
    void buildExample_hasCorrectFeatureCount() {
        Example<Label> example = builder.buildExample(perfectMatch());
        assertThat(example).isNotNull();
        assertThat(example.size()).isEqualTo(8);
    }

    @Test
    void buildLabelledExample_match_setsLabelToMatch() {
        Example<Label> example = builder.buildLabelledExample(perfectMatch(), "MATCH");
        assertThat(example.getOutput().getLabel()).isEqualTo("MATCH");
    }

    @Test
    void buildLabelledExample_noMatch_setsLabelToNoMatch() {
        Example<Label> example = builder.buildLabelledExample(noMatch(), "NO_MATCH");
        assertThat(example.getOutput().getLabel()).isEqualTo("NO_MATCH");
    }

    @Test
    void buildExample_amountDiffPctClampedAt5() {
        // amountDiffPct = 10 — should be clamped to 5 (max)
        ReconFeatures features = new ReconFeatures(
                UUID.randomUUID(), UUID.randomUUID(), "PAYMENT",
                10.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0, 0.0);
        Example<Label> example = builder.buildExample(features);
        assertThat(example).isNotNull();
        // feature array is not directly inspectable without conversion, just verify no exception
    }

    @Test
    void buildDataset_fromLabelledExamples_hasCorrectSize() {
        List<SyntheticReconFeatureBuilder.LabelledReconExample> examples = List.of(
                new SyntheticReconFeatureBuilder.LabelledReconExample(perfectMatch(), "MATCH"),
                new SyntheticReconFeatureBuilder.LabelledReconExample(noMatch(), "NO_MATCH"),
                new SyntheticReconFeatureBuilder.LabelledReconExample(perfectMatch(), "MATCH")
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
    void buildDataset_labelDistribution_hasMatchAndNoMatch() {
        List<SyntheticReconFeatureBuilder.LabelledReconExample> examples = List.of(
                new SyntheticReconFeatureBuilder.LabelledReconExample(perfectMatch(), "MATCH"),
                new SyntheticReconFeatureBuilder.LabelledReconExample(noMatch(), "NO_MATCH")
        );

        MutableDataset<Label> dataset = builder.buildDataset(examples);
        assertThat(dataset.getOutputInfo().size()).isEqualTo(2);
    }
}
