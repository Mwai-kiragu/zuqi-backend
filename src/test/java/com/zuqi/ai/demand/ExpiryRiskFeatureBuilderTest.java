package com.zuqi.ai.demand;

import com.zuqi.ai.feature.ExpiryFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.regression.Regressor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiryRiskFeatureBuilderTest {

    private ExpiryRiskFeatureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ExpiryRiskFeatureBuilder();
    }

    private ExpiryFeatures sampleFeatures(int daysToExpiry, double stockQty, double dailyRate) {
        return new ExpiryFeatures(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "BATCH-001",
                LocalDate.now().plusDays(daysToExpiry),
                daysToExpiry,
                stockQty,
                dailyRate,
                dailyRate > 0 ? stockQty / dailyRate : 999.0,
                dailyRate * 0.9,
                12.0,
                0.3,
                0.5
        );
    }

    @Test
    void getFeatureCount_returns8() {
        assertThat(builder.getFeatureCount()).isEqualTo(8);
    }

    @Test
    void buildExample_returnsNonNullWithCorrectFeatureCount() {
        ExpiryFeatures features = sampleFeatures(30, 100, 5.0);
        Example<Regressor> example = builder.buildExample(features);

        assertThat(example).isNotNull();
        assertThat(example.size()).isEqualTo(8);
    }

    @Test
    void buildLabelledExample_setsTargetCorrectly() {
        ExpiryFeatures features = sampleFeatures(14, 50, 3.0);
        Example<Regressor> example = builder.buildLabelledExample(features, 0.75);

        assertThat(example).isNotNull();
        assertThat(example.getOutput().getNames()).contains("sell_through");
        assertThat(example.getOutput().getValues()[0]).isEqualTo(0.75);
    }

    @Test
    void buildLabelledExample_negativeDaysToExpiry_clampsToZero() {
        // Expired batch: days_to_expiry = -3
        ExpiryFeatures features = new ExpiryFeatures(
                null, null, null, "BATCH-X",
                LocalDate.now().minusDays(3), -3,
                100.0, 2.0, 50.0, 1.8, 12.0, 0.3, 0.9
        );
        Example<Regressor> example = builder.buildExample(features);
        // days_to_expiry feature should be clamped to 0
        assertThat(example.size()).isEqualTo(8);
    }

    @Test
    void buildDataset_fromMultipleExamples_hasCorrectSize() {
        List<ExpiryRiskFeatureBuilder.LabelledExpiryExample> examples = List.of(
                new ExpiryRiskFeatureBuilder.LabelledExpiryExample(sampleFeatures(30, 100, 5), 0.9),
                new ExpiryRiskFeatureBuilder.LabelledExpiryExample(sampleFeatures(7, 80, 1), 0.2),
                new ExpiryRiskFeatureBuilder.LabelledExpiryExample(sampleFeatures(60, 200, 8), 0.95)
        );

        MutableDataset<Regressor> dataset = builder.buildDataset(examples);

        assertThat(dataset.size()).isEqualTo(3);
    }

    @Test
    void buildDataset_emptyList_returnsEmptyDataset() {
        MutableDataset<Regressor> dataset = builder.buildDataset(List.of());
        assertThat(dataset.size()).isEqualTo(0);
    }
}
