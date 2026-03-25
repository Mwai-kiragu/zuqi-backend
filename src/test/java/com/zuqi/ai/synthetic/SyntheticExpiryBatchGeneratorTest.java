package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.generators.SyntheticExpiryBatchGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SyntheticExpiryBatchGeneratorTest {

    private SyntheticExpiryBatchGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SyntheticExpiryBatchGenerator();
    }

    @Test
    void generateBatches_defaultCount_returns500() {
        List<SyntheticExpiryBatchGenerator.SyntheticExpiryBatch> batches =
                generator.generateBatches();
        assertThat(batches).hasSize(500);
    }

    @Test
    void generateBatches_customCount_returnsExactCount() {
        assertThat(generator.generateBatches(100)).hasSize(100);
        assertThat(generator.generateBatches(1)).hasSize(1);
    }

    @Test
    void generateBatches_sellThroughProbabilityBetween0And1() {
        generator.generateBatches(200).forEach(b ->
                assertThat(b.sellThroughProbability())
                        .isBetween(0.0, 1.0));
    }

    @Test
    void generateBatches_daysToExpiryBetween1And90() {
        generator.generateBatches(200).forEach(b ->
                assertThat(b.daysToExpiry())
                        .isBetween(1, 90));
    }

    @Test
    void generateBatches_outcomeDistributionApproxCorrect() {
        // 60% SOLD_OUT, 25% PARTIAL, 15% EXPIRED — allow ±10%
        List<SyntheticExpiryBatchGenerator.SyntheticExpiryBatch> batches =
                generator.generateBatches(1000);

        Map<String, Long> counts = batches.stream()
                .collect(Collectors.groupingBy(
                        SyntheticExpiryBatchGenerator.SyntheticExpiryBatch::outcome,
                        Collectors.counting()));

        double soldOutPct  = counts.getOrDefault("SOLD_OUT",  0L) / 1000.0;
        double partialPct  = counts.getOrDefault("PARTIAL",   0L) / 1000.0;
        double expiredPct  = counts.getOrDefault("EXPIRED",   0L) / 1000.0;

        assertThat(soldOutPct).isCloseTo(0.60, within(0.10));
        assertThat(partialPct).isCloseTo(0.25, within(0.10));
        assertThat(expiredPct).isCloseTo(0.15, within(0.10));
    }

    @Test
    void generateBatches_isDeterministic() {
        List<SyntheticExpiryBatchGenerator.SyntheticExpiryBatch> run1 =
                generator.generateBatches(50);
        List<SyntheticExpiryBatchGenerator.SyntheticExpiryBatch> run2 =
                new SyntheticExpiryBatchGenerator().generateBatches(50);

        for (int i = 0; i < 50; i++) {
            assertThat(run1.get(i).sellThroughProbability())
                    .isEqualTo(run2.get(i).sellThroughProbability());
        }
    }

    @Test
    void generateBatches_allBatchNumbersUnique() {
        List<String> batchNumbers = generator.generateBatches(200).stream()
                .map(SyntheticExpiryBatchGenerator.SyntheticExpiryBatch::batchNumber)
                .toList();
        assertThat(batchNumbers).doesNotHaveDuplicates();
    }
}
