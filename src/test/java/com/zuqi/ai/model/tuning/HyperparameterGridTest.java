package com.zuqi.ai.model.tuning;

import org.junit.jupiter.api.Test;
import org.tribuo.anomaly.Event;
import org.tribuo.classification.Label;
import org.tribuo.regression.Regressor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HyperparameterGrid}.
 *
 * Verifies that:
 * - Candidate lists are non-empty and of the expected size
 * - All candidates carry a trainer and a non-empty hyperparameter map
 * - Key hyperparameter entries are present
 */
class HyperparameterGridTest {

    // ── Classification ──────────────────────────────────────────────────────

    @Test
    void classificationCandidates_returnsFiveCandidates() {
        List<CandidateConfig<Label>> candidates = HyperparameterGrid.classificationCandidates();
        assertThat(candidates).hasSize(5);
    }

    @Test
    void classificationCandidates_allHaveTrainerAndParams() {
        for (CandidateConfig<Label> c : HyperparameterGrid.classificationCandidates()) {
            assertThat(c.trainer()).isNotNull();
            assertThat(c.hyperparameters()).isNotEmpty();
        }
    }

    @Test
    void classificationCandidates_containsNumRoundsKey() {
        for (CandidateConfig<Label> c : HyperparameterGrid.classificationCandidates()) {
            assertThat(c.hyperparameters()).containsKey("num_rounds");
            assertThat(c.hyperparameters()).containsKey("eta");
            assertThat(c.hyperparameters()).containsKey("max_depth");
        }
    }

    @Test
    void classificationCandidates_numRoundsArePositive() {
        for (CandidateConfig<Label> c : HyperparameterGrid.classificationCandidates()) {
            Object rounds = c.hyperparameters().get("num_rounds");
            assertThat(rounds).isInstanceOf(Integer.class);
            assertThat((Integer) rounds).isPositive();
        }
    }

    @Test
    void classificationCandidates_etaIsInValidRange() {
        for (CandidateConfig<Label> c : HyperparameterGrid.classificationCandidates()) {
            Object eta = c.hyperparameters().get("eta");
            assertThat(eta).isInstanceOf(Double.class);
            assertThat((Double) eta).isBetween(0.001, 1.0);
        }
    }

    // ── Regression ──────────────────────────────────────────────────────────

    @Test
    void regressionCandidates_returnsFiveCandidates() {
        List<CandidateConfig<Regressor>> candidates = HyperparameterGrid.regressionCandidates();
        assertThat(candidates).hasSize(5);
    }

    @Test
    void regressionCandidates_allHaveTrainerAndParams() {
        for (CandidateConfig<Regressor> c : HyperparameterGrid.regressionCandidates()) {
            assertThat(c.trainer()).isNotNull();
            assertThat(c.hyperparameters()).isNotEmpty();
        }
    }

    @Test
    void regressionCandidates_containsRequiredKeys() {
        for (CandidateConfig<Regressor> c : HyperparameterGrid.regressionCandidates()) {
            Map<String, Object> p = c.hyperparameters();
            assertThat(p).containsKey("num_rounds");
            assertThat(p).containsKey("eta");
            assertThat(p).containsKey("max_depth");
            assertThat(p).containsKey("subsample");
        }
    }

    // ── Anomaly ─────────────────────────────────────────────────────────────

    @Test
    void anomalyCandidates_returnsFourCandidates() {
        List<CandidateConfig<Event>> candidates = HyperparameterGrid.anomalyCandidates();
        assertThat(candidates).hasSize(4);
    }

    @Test
    void anomalyCandidates_allHaveTrainerAndParams() {
        for (CandidateConfig<Event> c : HyperparameterGrid.anomalyCandidates()) {
            assertThat(c.trainer()).isNotNull();
            assertThat(c.hyperparameters()).isNotEmpty();
        }
    }

    @Test
    void anomalyCandidates_nuIsInValidRange() {
        for (CandidateConfig<Event> c : HyperparameterGrid.anomalyCandidates()) {
            Object nu = c.hyperparameters().get("nu");
            assertThat(nu).isInstanceOf(Double.class);
            assertThat((Double) nu).isBetween(0.0, 1.0);
        }
    }

    @Test
    void anomalyCandidates_gammaIsPositive() {
        for (CandidateConfig<Event> c : HyperparameterGrid.anomalyCandidates()) {
            Object gamma = c.hyperparameters().get("gamma");
            assertThat(gamma).isInstanceOf(Double.class);
            assertThat((Double) gamma).isPositive();
        }
    }

    @Test
    void anomalyCandidates_containsKernelAndSvmType() {
        for (CandidateConfig<Event> c : HyperparameterGrid.anomalyCandidates()) {
            assertThat(c.hyperparameters()).containsKey("kernel");
            assertThat(c.hyperparameters()).containsKey("svm_type");
        }
    }
}
