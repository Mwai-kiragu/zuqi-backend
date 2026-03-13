package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.domain.ai.DataPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.classification.Label;
import org.tribuo.impl.ArrayExample;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static com.zuqi.ai.synthetic.DataPhaseTracker.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataMixerTest {

    @Mock private DataPhaseTracker phaseTracker;

    private DataMixer mixer;

    // Convenience label constants
    private static final Label DEFAULT_LABEL    = new Label("DEFAULT");
    private static final Label NO_DEFAULT_LABEL = new Label("NO_DEFAULT");

    private static final Predicate<Example<Label>> IS_DEFAULT =
            ex -> "DEFAULT".equals(ex.getOutput().getLabel());

    private static final UUID DIST = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mixer = new DataMixer(phaseTracker);
        lenient().when(phaseTracker.getRealDataRatio(any(), any())).thenReturn(0.0);
    }

    // ── SYNTHETIC phase ───────────────────────────────────────────────────

    @Test
    void syntheticPhase_returnsSyntheticFeatures() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.SYNTHETIC);

        List<Example<Label>> real      = examples(10, NO_DEFAULT_LABEL);
        List<Example<Label>> synthetic = examples(50, NO_DEFAULT_LABEL);

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_CREDIT_CLASSIFIER, DIST, real, synthetic);

        assertThat(result).hasSize(50);
    }

    @Test
    void syntheticPhase_emptyRealFeatures_returnsSyntheticFeatures() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.SYNTHETIC);

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_CREDIT_CLASSIFIER, DIST, List.of(), examples(20, NO_DEFAULT_LABEL));

        assertThat(result).hasSize(20);
    }

    // ── REAL phase ────────────────────────────────────────────────────────

    @Test
    void realPhase_returnsRealFeatures() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.REAL);

        List<Example<Label>> real      = examples(30, NO_DEFAULT_LABEL);
        List<Example<Label>> synthetic = examples(100, NO_DEFAULT_LABEL);

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_CREDIT_CLASSIFIER, DIST, real, synthetic);

        assertThat(result).hasSize(30);
    }

    @Test
    void realPhase_emptyRealFeatures_returnsEmpty() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.REAL);

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_CREDIT_CLASSIFIER, DIST, List.of(), examples(50, NO_DEFAULT_LABEL));

        assertThat(result).isEmpty();
    }

    // ── HYBRID phase ──────────────────────────────────────────────────────

    @Test
    void hybridPhase_containsAllRealExamples() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.HYBRID);
        when(phaseTracker.getRealDataRatio(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(0.30);   // syntheticWeight = max(0.2, 0.70) = 0.70

        List<Example<Label>> real      = examples(100, NO_DEFAULT_LABEL);
        List<Example<Label>> synthetic = examples(200, NO_DEFAULT_LABEL);

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_CREDIT_CLASSIFIER, DIST, real, synthetic);

        // All 100 real + up to 70 synthetic
        assertThat(result.size()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void hybridPhase_syntheticSizeIsWeightTimesRealSize() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.HYBRID);
        when(phaseTracker.getRealDataRatio(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(0.30);   // syntheticWeight = 0.70 → 100 × 0.70 = 70

        List<Example<Label>> real      = examples(100, NO_DEFAULT_LABEL);
        List<Example<Label>> synthetic = examples(200, NO_DEFAULT_LABEL);

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_CREDIT_CLASSIFIER, DIST, real, synthetic);

        // 100 real + 70 synthetic = 170
        assertThat(result).hasSize(170);
    }

    @Test
    void hybridPhase_highRealRatio_syntheticWeightFlooredAt0point2() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.HYBRID);
        when(phaseTracker.getRealDataRatio(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(0.95);   // 1.0 - 0.95 = 0.05 → floor to 0.20

        List<Example<Label>> real      = examples(100, NO_DEFAULT_LABEL);
        List<Example<Label>> synthetic = examples(200, NO_DEFAULT_LABEL);

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_CREDIT_CLASSIFIER, DIST, real, synthetic);

        // syntheticWeight = 0.20 → 100 × 0.20 = 20 → total = 120
        assertThat(result).hasSize(120);
    }

    @Test
    void hybridPhase_limitedSyntheticAvailable_usesAllAvailable() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.HYBRID);
        when(phaseTracker.getRealDataRatio(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(0.30);   // syntheticWeight = 0.70 → would want 70 but only 10 available

        List<Example<Label>> real      = examples(100, NO_DEFAULT_LABEL);
        List<Example<Label>> synthetic = examples(10, NO_DEFAULT_LABEL);  // only 10

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_CREDIT_CLASSIFIER, DIST, real, synthetic);

        // All 10 synthetic used
        assertThat(result).hasSize(110);
    }

    // ── Rare-class preservation ───────────────────────────────────────────

    @Test
    void rareClassPreservation_sufficientRareInMixed_noSyntheticAdded() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.REAL);

        // 5 DEFAULT out of 100 = 5% — meets the 5% threshold exactly
        List<Example<Label>> real = new ArrayList<>(examples(95, NO_DEFAULT_LABEL));
        real.addAll(examples(5, DEFAULT_LABEL));

        List<Example<Label>> synRare = examples(20, DEFAULT_LABEL);
        List<Example<Label>> synthetic = new ArrayList<>(examples(80, NO_DEFAULT_LABEL));
        synthetic.addAll(synRare);

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_CREDIT_CLASSIFIER, DIST, real, synthetic, IS_DEFAULT);

        // No supplementation needed — still 100
        assertThat(result).hasSize(100);
        long defaultCount = result.stream().filter(IS_DEFAULT).count();
        assertThat(defaultCount).isEqualTo(5);
    }

    @Test
    void rareClassPreservation_insufficientRareInMixed_supplementsFromSynthetic() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.REAL);

        // Only 2 DEFAULT out of 100 = 2% — below the 5% threshold
        List<Example<Label>> real = new ArrayList<>(examples(98, NO_DEFAULT_LABEL));
        real.addAll(examples(2, DEFAULT_LABEL));

        // Synthetic has plenty of DEFAULT examples
        List<Example<Label>> synthetic = new ArrayList<>(examples(50, NO_DEFAULT_LABEL));
        synthetic.addAll(examples(30, DEFAULT_LABEL));

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_CREDIT_CLASSIFIER, DIST, real, synthetic, IS_DEFAULT);

        // After supplementation, DEFAULT count >= ceil(100 * 0.05) = 5
        long defaultCount = result.stream().filter(IS_DEFAULT).count();
        assertThat(defaultCount).isGreaterThanOrEqualTo(5);
        assertThat(result.size()).isGreaterThan(100);
    }

    @Test
    void rareClassPreservation_noSyntheticRareAvailable_returnsMixedUnchanged() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.REAL);

        List<Example<Label>> real = examples(100, NO_DEFAULT_LABEL);  // 0 DEFAULT
        List<Example<Label>> synthetic = examples(50, NO_DEFAULT_LABEL); // also 0 DEFAULT

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_CREDIT_CLASSIFIER, DIST, real, synthetic, IS_DEFAULT);

        // No rare examples to add — returned unchanged
        assertThat(result).hasSize(100);
    }

    @Test
    void rareClassPreservation_noPredicateSupplied_noCheck() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.REAL);

        List<Example<Label>> real = examples(100, NO_DEFAULT_LABEL);  // 0 DEFAULT
        List<Example<Label>> synthetic = examples(20, DEFAULT_LABEL);

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_CREDIT_CLASSIFIER, DIST, real, synthetic);  // no predicate

        // REAL phase with no predicate → just real features, no supplementation
        assertThat(result).hasSize(100);
    }

    @Test
    void rareClassPreservation_modelWithNoRequirement_noCheck() {
        // demand_forecaster has no rare-class requirement
        when(phaseTracker.getPhase(MODEL_DEMAND_FORECASTER, DIST))
                .thenReturn(DataPhase.REAL);

        List<Example<Label>> real = examples(100, NO_DEFAULT_LABEL);
        List<Example<Label>> synthetic = examples(20, DEFAULT_LABEL);

        List<Example<Label>> result = mixer.buildTrainingDataset(
                MODEL_DEMAND_FORECASTER, DIST, real, synthetic, IS_DEFAULT);

        // minFraction = 0 for demand_forecaster → no supplementation
        assertThat(result).hasSize(100);
    }

    // ── Confidence modifier ───────────────────────────────────────────────

    @Test
    void confidenceModifier_syntheticPhase_appliesFactor0point6() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.SYNTHETIC);

        double adjusted = mixer.applyConfidenceModifier(1.0, MODEL_CREDIT_CLASSIFIER, DIST);
        assertThat(adjusted).isEqualTo(0.6);
    }

    @Test
    void confidenceModifier_realPhase_returnRawUnchanged() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.REAL);

        double adjusted = mixer.applyConfidenceModifier(0.85, MODEL_CREDIT_CLASSIFIER, DIST);
        assertThat(adjusted).isEqualTo(0.85);
    }

    @Test
    void confidenceModifier_hybridPhase_interpolatesBasedOnRatio() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.HYBRID);
        when(phaseTracker.getRealDataRatio(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(0.50);

        // modifier = 0.6 + 0.4 × 0.50 = 0.80
        double adjusted = mixer.applyConfidenceModifier(1.0, MODEL_CREDIT_CLASSIFIER, DIST);
        assertThat(adjusted).isEqualTo(0.80);
    }

    @Test
    void confidenceModifier_hybridPhase_ratio0_givenFactor0point6() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.HYBRID);
        when(phaseTracker.getRealDataRatio(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(0.0);

        // modifier = 0.6 + 0.4 × 0.0 = 0.60
        double adjusted = mixer.applyConfidenceModifier(1.0, MODEL_CREDIT_CLASSIFIER, DIST);
        assertThat(adjusted).isEqualTo(0.60);
    }

    @Test
    void confidenceModifier_hybridPhase_ratio1_givenFactor1point0() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.HYBRID);
        when(phaseTracker.getRealDataRatio(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(1.0);

        // modifier = 0.6 + 0.4 × 1.0 = 1.00
        double adjusted = mixer.applyConfidenceModifier(1.0, MODEL_CREDIT_CLASSIFIER, DIST);
        assertThat(adjusted).isEqualTo(1.00);
    }

    @Test
    void confidenceModifier_scalesRawConfidence() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.SYNTHETIC);

        double adjusted = mixer.applyConfidenceModifier(0.80, MODEL_CREDIT_CLASSIFIER, DIST);
        assertThat(adjusted).isEqualTo(0.48);  // 0.80 × 0.6
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Build a list of simple label-only Examples. */
    private List<Example<Label>> examples(int count, Label label) {
        List<Example<Label>> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new ArrayExample<>(label, List.of(new Feature("f", 1.0))));
        }
        return list;
    }

    /** Null-safe any() helper (Mockito's any() requires import). */
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
