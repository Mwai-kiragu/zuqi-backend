package com.zuqi.ai.cashflow;

import com.zuqi.ai.feature.CashFlowFeatures;
import com.zuqi.ai.synthetic.SyntheticCashFlowFeatureBuilder;
import com.zuqi.ai.synthetic.SyntheticCashFlowFeatureBuilder.LabelledCashFlowExample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.regression.Regressor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CashFlowFeatureBuilderTest {

    private CashFlowFeatureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new CashFlowFeatureBuilder();
    }

    private CashFlowFeatures typicalFeatures() {
        return new CashFlowFeatures(
                UUID.randomUUID(),
                LocalDate.now(),
                500_000.0,   // pendingOrdersValue
                30_000.0,    // avgDailyCollections7d
                28_000.0,    // avgDailyCollections30d
                2_000.0,     // collectionTrend
                120_000.0,   // overdueReceivablesTotal
                80_000.0,    // paymentDueNext7d
                200_000.0,   // pendingPurchaseOrdersValue
                15_000.0,    // avgDailyExpenses30d
                50_000.0,    // upcomingSupplierPayments
                3,           // dayOfWeek (Wednesday)
                15,          // dayOfMonth
                0.0,         // isPaydayWeek
                0.0,         // isMonthEnd
                5_000.0,     // netCashFlow7dAgo
                -2_000.0     // netCashFlow30dAgo
        );
    }

    private CashFlowFeatures shortfallFeatures() {
        return new CashFlowFeatures(
                UUID.randomUUID(),
                LocalDate.now().plusDays(3),
                800_000.0,
                5_000.0,
                10_000.0,
                -5_000.0,    // declining trend
                300_000.0,   // high overdue
                200_000.0,   // large payments due
                500_000.0,   // big POs pending
                25_000.0,
                150_000.0,
                5,
                28,
                1.0,
                1.0,
                -10_000.0,
                -8_000.0
        );
    }

    @Test
    void getFeatureCount_returns16() {
        assertThat(builder.getFeatureCount()).isEqualTo(16);
    }

    @Test
    void buildExample_hasCorrectFeatureCount() {
        Example<Regressor> example = builder.buildExample(typicalFeatures());
        assertThat(example).isNotNull();
        assertThat(example.size()).isEqualTo(16);
    }

    @Test
    void buildLabelledExample_setsTargetLabel() {
        Example<Regressor> example = builder.buildLabelledExample(typicalFeatures(), 12_000.0);
        assertThat(example).isNotNull();
        assertThat(example.getOutput().getNames()[0]).isEqualTo("net_cash_flow");
        assertThat(example.getOutput().getValues()[0]).isEqualTo(12_000.0);
    }

    @Test
    void buildExample_placeholderTarget_isZero() {
        Example<Regressor> example = builder.buildExample(typicalFeatures());
        assertThat(example.getOutput().getValues()[0]).isEqualTo(0.0);
    }

    @Test
    void buildDataset_fromLabelledExamples_hasCorrectSize() {
        List<LabelledCashFlowExample> examples = List.of(
                new LabelledCashFlowExample(typicalFeatures(), 12_000.0),
                new LabelledCashFlowExample(shortfallFeatures(), -3_000.0),
                new LabelledCashFlowExample(typicalFeatures(), 8_000.0)
        );

        MutableDataset<Regressor> dataset = builder.buildDataset(examples);

        assertThat(dataset.size()).isEqualTo(3);
    }

    @Test
    void buildDataset_empty_returnsEmptyDataset() {
        MutableDataset<Regressor> dataset = builder.buildDataset(List.of());
        assertThat(dataset.size()).isEqualTo(0);
    }

    @Test
    void buildLabelledExample_cappedValues_noException() {
        // Very large values should be capped without throwing
        CashFlowFeatures extreme = new CashFlowFeatures(
                UUID.randomUUID(), LocalDate.now(),
                50_000_000.0,  // well above 10M cap
                50_000_000.0,
                50_000_000.0,
                -9_999.0,
                50_000_000.0,
                50_000_000.0,
                50_000_000.0,
                50_000_000.0,
                50_000_000.0,
                7, 31,
                1.0, 1.0,
                -50_000_000.0,
                -50_000_000.0
        );

        Example<Regressor> example = builder.buildLabelledExample(extreme, 0.0);
        assertThat(example).isNotNull();
        assertThat(example.size()).isEqualTo(16);
    }

    @Test
    void syntheticFeatureBuilder_computeFeatures_roundTrip() {
        // Verify SyntheticCashFlowFeatureBuilder produces valid CashFlowFeatures
        // that can be fed back into the production CashFlowFeatureBuilder
        SyntheticCashFlowFeatureBuilder syntheticBuilder = new SyntheticCashFlowFeatureBuilder();
        com.zuqi.ai.synthetic.generators.SyntheticCashFlowGenerator generator =
                new com.zuqi.ai.synthetic.generators.SyntheticCashFlowGenerator();

        List<com.zuqi.ai.synthetic.dto.SyntheticCashFlowSnapshot> snapshots =
                generator.generate(List.of(), List.of(), 99L);

        assertThat(snapshots).isNotEmpty();

        List<LabelledCashFlowExample> examples = syntheticBuilder.buildLabelledExamples(snapshots);
        MutableDataset<Regressor> dataset = builder.buildDataset(examples);

        assertThat(dataset.size()).isGreaterThan(0);
    }
}
