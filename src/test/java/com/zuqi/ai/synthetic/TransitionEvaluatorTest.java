package com.zuqi.ai.synthetic;

import com.zuqi.domain.ai.DataPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static com.zuqi.ai.synthetic.DataPhaseTracker.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransitionEvaluatorTest {

    @Mock private DataPhaseTracker phaseTracker;

    private TransitionEvaluator evaluator;

    private static final UUID DIST = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        evaluator = new TransitionEvaluator(phaseTracker);
    }

    // ── meetsRealOnlyRequirements (volume only) ───────────────────────────

    @Test
    void volumeOnly_syntheticPhase_returnsFalse() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.SYNTHETIC);
        assertThat(evaluator.meetsRealOnlyRequirements(MODEL_CREDIT_CLASSIFIER, DIST))
                .isFalse();
    }

    @Test
    void volumeOnly_hybridPhase_returnsFalse() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.HYBRID);
        assertThat(evaluator.meetsRealOnlyRequirements(MODEL_CREDIT_CLASSIFIER, DIST))
                .isFalse();
    }

    @Test
    void volumeOnly_realPhase_returnsTrue() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.REAL);
        assertThat(evaluator.meetsRealOnlyRequirements(MODEL_CREDIT_CLASSIFIER, DIST))
                .isTrue();
    }

    // ── meetsRealOnlyRequirements (volume + rare events) ─────────────────

    @Test
    void withRareEvents_notRealPhase_returnsFalse() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.HYBRID);
        // Plenty of rare events but not in REAL phase yet
        assertThat(evaluator.meetsRealOnlyRequirements(
                MODEL_CREDIT_CLASSIFIER, DIST, 200)).isFalse();
    }

    @Test
    void withRareEvents_realPhase_sufficientRareEvents_returnsTrue() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.REAL);
        // credit_classifier needs 100 real defaults
        assertThat(evaluator.meetsRealOnlyRequirements(
                MODEL_CREDIT_CLASSIFIER, DIST, 100)).isTrue();
    }

    @Test
    void withRareEvents_realPhase_moreRareEventsThanMin_returnsTrue() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.REAL);
        assertThat(evaluator.meetsRealOnlyRequirements(
                MODEL_CREDIT_CLASSIFIER, DIST, 500)).isTrue();
    }

    @Test
    void withRareEvents_realPhase_insufficientRareEvents_returnsFalse() {
        when(phaseTracker.getPhase(MODEL_CREDIT_CLASSIFIER, DIST))
                .thenReturn(DataPhase.REAL);
        // only 50 defaults — below the 100 minimum for credit_classifier
        assertThat(evaluator.meetsRealOnlyRequirements(
                MODEL_CREDIT_CLASSIFIER, DIST, 50)).isFalse();
    }

    @Test
    void withRareEvents_modelWithNoRequirement_realPhase_zeroRareEvents_returnsTrue() {
        when(phaseTracker.getPhase(MODEL_DEMAND_FORECASTER, DIST))
                .thenReturn(DataPhase.REAL);
        // demand_forecaster has no rare-event requirement
        assertThat(evaluator.meetsRealOnlyRequirements(
                MODEL_DEMAND_FORECASTER, DIST, 0)).isTrue();
    }

    // ── getMinRareEventCount ──────────────────────────────────────────────

    @Test
    void minRareEventCount_creditClassifier_returns100() {
        assertThat(evaluator.getMinRareEventCount(MODEL_CREDIT_CLASSIFIER))
                .isEqualTo(100);
    }

    @Test
    void minRareEventCount_creditLimitRegressor_returns100() {
        assertThat(evaluator.getMinRareEventCount(MODEL_CREDIT_LIMIT_REGRESSOR))
                .isEqualTo(100);
    }

    @Test
    void minRareEventCount_paymentDistressClassifier_returns30() {
        assertThat(evaluator.getMinRareEventCount(MODEL_PAYMENT_DISTRESS_CLASSIFIER))
                .isEqualTo(30);
    }

    @Test
    void minRareEventCount_shrinkageDetector_returns50() {
        assertThat(evaluator.getMinRareEventCount(MODEL_SHRINKAGE_DETECTOR))
                .isEqualTo(50);
    }

    @Test
    void minRareEventCount_paymentAnomalyDetector_returns50() {
        assertThat(evaluator.getMinRareEventCount(MODEL_PAYMENT_ANOMALY_DETECTOR))
                .isEqualTo(50);
    }

    @Test
    void minRareEventCount_stockoutPredictor_returns50() {
        assertThat(evaluator.getMinRareEventCount(MODEL_STOCKOUT_PREDICTOR))
                .isEqualTo(50);
    }

    @Test
    void minRareEventCount_demandForecaster_returnsZero() {
        assertThat(evaluator.getMinRareEventCount(MODEL_DEMAND_FORECASTER))
                .isEqualTo(0);
    }

    @Test
    void minRareEventCount_unknownModel_returnsZero() {
        assertThat(evaluator.getMinRareEventCount("some_new_model"))
                .isEqualTo(0);
    }
}
