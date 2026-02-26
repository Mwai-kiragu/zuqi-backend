package com.zuqi.ai.model;

import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.DataPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ModelPhaseService}.
 *
 * Verifies that the 0.6× SYNTHETIC-phase modifier is applied correctly for
 * both {@code double} and {@link BigDecimal} values, and that HYBRID / REAL
 * phase predictions pass through unmodified.
 */
@ExtendWith(MockitoExtension.class)
class ModelPhaseServiceTest {

    @Mock
    private DataPhaseTracker phaseTracker;

    private ModelPhaseService service;

    private static final String MODEL = "credit_classifier";

    @BeforeEach
    void setUp() {
        service = new ModelPhaseService(phaseTracker);
    }

    // ── applyModifier(double) ─────────────────────────────────────────────

    @Test
    void applyModifier_double_syntheticPhase_appliesMultiplier() {
        when(phaseTracker.getPhase(MODEL, null)).thenReturn(DataPhase.SYNTHETIC);

        double result = service.applyModifier(0.80, MODEL);

        assertThat(result).isCloseTo(0.48, within(1e-9)); // 0.80 × 0.6
    }

    @Test
    void applyModifier_double_hybridPhase_noChange() {
        when(phaseTracker.getPhase(MODEL, null)).thenReturn(DataPhase.HYBRID);

        double result = service.applyModifier(0.80, MODEL);

        assertThat(result).isCloseTo(0.80, within(1e-9));
    }

    @Test
    void applyModifier_double_realPhase_noChange() {
        when(phaseTracker.getPhase(MODEL, null)).thenReturn(DataPhase.REAL);

        double result = service.applyModifier(0.80, MODEL);

        assertThat(result).isCloseTo(0.80, within(1e-9));
    }

    @Test
    void applyModifier_double_syntheticPhase_zeroValue_returnsZero() {
        when(phaseTracker.getPhase(MODEL, null)).thenReturn(DataPhase.SYNTHETIC);

        double result = service.applyModifier(0.0, MODEL);

        assertThat(result).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void applyModifier_double_syntheticPhase_scoreScaledTo100_returnsCorrect() {
        when(phaseTracker.getPhase(MODEL, null)).thenReturn(DataPhase.SYNTHETIC);

        // Rep performance score of 80.0 should become 48.0
        double result = service.applyModifier(80.0, MODEL);

        assertThat(result).isCloseTo(48.0, within(1e-9));
    }

    // ── applyModifier(BigDecimal) ─────────────────────────────────────────

    @Test
    void applyModifier_bigDecimal_syntheticPhase_appliesMultiplier() {
        when(phaseTracker.getPhase(MODEL, null)).thenReturn(DataPhase.SYNTHETIC);

        BigDecimal result = service.applyModifier(BigDecimal.valueOf(1_000_000), MODEL);

        // 1,000,000 × 0.6 = 600,000
        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(600_000));
    }

    @Test
    void applyModifier_bigDecimal_hybridPhase_noChange() {
        when(phaseTracker.getPhase(MODEL, null)).thenReturn(DataPhase.HYBRID);

        BigDecimal result = service.applyModifier(BigDecimal.valueOf(1_000_000), MODEL);

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
    }

    @Test
    void applyModifier_bigDecimal_nullValue_returnsNull() {
        // phaseTracker.getPhase is not called when value is null
        BigDecimal result = service.applyModifier((BigDecimal) null, MODEL);

        assertThat(result).isNull();
    }

    @Test
    void applyModifier_bigDecimal_syntheticPhase_roundsToIntegerKes() {
        when(phaseTracker.getPhase(MODEL, null)).thenReturn(DataPhase.SYNTHETIC);

        // 333,333 × 0.6 = 199,999.8 → rounds to 200,000
        BigDecimal result = service.applyModifier(BigDecimal.valueOf(333_333), MODEL);

        assertThat(result.scale()).isEqualTo(0);
        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(200_000));
    }

    // ── isSyntheticPhase ──────────────────────────────────────────────────

    @Test
    void isSyntheticPhase_syntheticPhase_returnsTrue() {
        when(phaseTracker.getPhase(MODEL, null)).thenReturn(DataPhase.SYNTHETIC);

        assertThat(service.isSyntheticPhase(MODEL)).isTrue();
    }

    @Test
    void isSyntheticPhase_hybridPhase_returnsFalse() {
        when(phaseTracker.getPhase(MODEL, null)).thenReturn(DataPhase.HYBRID);

        assertThat(service.isSyntheticPhase(MODEL)).isFalse();
    }

    @Test
    void isSyntheticPhase_realPhase_returnsFalse() {
        when(phaseTracker.getPhase(MODEL, null)).thenReturn(DataPhase.REAL);

        assertThat(service.isSyntheticPhase(MODEL)).isFalse();
    }
}
