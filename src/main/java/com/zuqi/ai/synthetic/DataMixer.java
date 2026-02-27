package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.domain.ai.DataPhase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Output;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Blends real and synthetic training feature vectors according to the current
 * {@link DataPhase} for a (model, distributor) pair.
 *
 * <h3>Blending strategy</h3>
 * <table border="1">
 * <tr><th>Phase</th><th>Strategy</th></tr>
 * <tr><td>SYNTHETIC</td><td>Return {@code syntheticFeatures} unchanged</td></tr>
 * <tr><td>HYBRID</td>
 *     <td>Return all real examples + {@code max(0.2, 1.0 − realRatio) × |real|} synthetic examples</td></tr>
 * <tr><td>REAL</td><td>Return {@code realFeatures} unchanged</td></tr>
 * </table>
 *
 * <h3>Anomaly-class preservation</h3>
 * When a {@code rareClassPredicate} is supplied, the method guarantees that at least
 * {@code minRareFraction} of the returned dataset belongs to the rare class.
 * If the blended dataset falls short, synthetic rare-class examples are injected from
 * {@code syntheticFeatures} regardless of phase. This ensures anomaly-detection and
 * credit-risk models always see sufficient positive examples.
 *
 * <p>Per-model minimum fractions:
 * <ul>
 *   <li>Credit classifier: 5% DEFAULT class</li>
 *   <li>Shrinkage detector: 10% ANOMALOUS class</li>
 *   <li>Payment anomaly detector: 8% ANOMALOUS class</li>
 *   <li>Stockout predictor: 10% stockout-positive class</li>
 * </ul>
 *
 * <h3>Confidence modifier</h3>
 * {@link #applyConfidenceModifier} scales raw model confidence by phase:
 * SYNTHETIC × 0.6, HYBRID × (0.6 + 0.4 × realRatio), REAL × 1.0.
 * This is exposed as a utility method for caller-side application before logging predictions
 * (see {@link com.zuqi.ai.monitoring.PredictionLoggerService}).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataMixer {

    // ── Rare-class minimum fractions per model ──────────────────────────────

    /** Minimum fraction of rare-class examples in the training dataset per model. */
    private static final Map<String, Double> RARE_CLASS_MIN_FRACTION = Map.of(
            DataPhaseTracker.MODEL_CREDIT_CLASSIFIER,        0.05,
            DataPhaseTracker.MODEL_SHRINKAGE_DETECTOR,       0.10,
            DataPhaseTracker.MODEL_PAYMENT_ANOMALY_DETECTOR, 0.08,
            DataPhaseTracker.MODEL_STOCKOUT_PREDICTOR,       0.10
    );

    // ── Dependencies ────────────────────────────────────────────────────────

    private final DataPhaseTracker phaseTracker;

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Build a blended training dataset without rare-class preservation.
     *
     * @param modelName         model identifier
     * @param distributorId     distributor scope (null = global)
     * @param realFeatures      examples from real operational data
     * @param syntheticFeatures examples from {@code SyntheticDataBundle}
     * @param <T>               Tribuo output type
     * @return blended list according to the current phase
     */
    public <T extends Output<T>> List<Example<T>> buildTrainingDataset(
            String modelName, UUID distributorId,
            List<Example<T>> realFeatures,
            List<Example<T>> syntheticFeatures) {
        return buildTrainingDataset(modelName, distributorId,
                realFeatures, syntheticFeatures, null);
    }

    /**
     * Build a blended training dataset with optional rare-class preservation.
     *
     * @param rareClassPredicate tests whether an example belongs to the rare class;
     *                           if {@code null} no preservation is applied
     */
    public <T extends Output<T>> List<Example<T>> buildTrainingDataset(
            String modelName, UUID distributorId,
            List<Example<T>> realFeatures,
            List<Example<T>> syntheticFeatures,
            Predicate<Example<T>> rareClassPredicate) {

        DataPhase phase = phaseTracker.getPhase(modelName, distributorId);
        log.debug("[DataMixer] {} phase={} real={} synthetic={}",
                modelName, phase, realFeatures.size(), syntheticFeatures.size());

        List<Example<T>> mixed = switch (phase) {
            case SYNTHETIC -> new ArrayList<>(syntheticFeatures);
            case REAL      -> new ArrayList<>(realFeatures);
            case HYBRID    -> blendHybrid(modelName, distributorId,
                                          realFeatures, syntheticFeatures);
        };

        // Rare-class preservation — always supplement from synthetic if needed
        if (rareClassPredicate != null && !mixed.isEmpty()) {
            double minFraction = RARE_CLASS_MIN_FRACTION.getOrDefault(modelName, 0.0);
            if (minFraction > 0.0) {
                mixed = preserveRareClass(mixed, syntheticFeatures,
                        rareClassPredicate, minFraction);
            }
        }

        log.debug("[DataMixer] {} final dataset size={}", modelName, mixed.size());
        return mixed;
    }

    /**
     * Apply the data-phase confidence modifier to a raw model confidence score.
     *
     * <ul>
     *   <li>SYNTHETIC: {@code rawConfidence × 0.6}</li>
     *   <li>HYBRID: {@code rawConfidence × (0.6 + 0.4 × realDataRatio)}</li>
     *   <li>REAL: {@code rawConfidence × 1.0} (unchanged)</li>
     * </ul>
     *
     * <p>Callers should invoke this before passing the confidence score to
     * {@link com.zuqi.ai.monitoring.PredictionLogger#logPrediction}.
     *
     * @param rawConfidence  model-reported confidence in [0, 1]
     * @param modelName      model identifier (used to look up current phase)
     * @param distributorId  distributor scope (null = global)
     * @return adjusted confidence, still in [0, 1]
     */
    public double applyConfidenceModifier(double rawConfidence,
                                          String modelName,
                                          UUID distributorId) {
        DataPhase phase = phaseTracker.getPhase(modelName, distributorId);
        double modifier = switch (phase) {
            case SYNTHETIC -> 0.6;
            case HYBRID    -> {
                double ratio = phaseTracker.getRealDataRatio(modelName, distributorId);
                yield 0.6 + 0.4 * ratio;
            }
            case REAL      -> 1.0;
        };
        return rawConfidence * modifier;
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    /**
     * Compute the HYBRID blend: all real examples plus a weighted subsample of synthetic.
     *
     * <p>Synthetic weight = {@code max(0.2, 1.0 − realRatio)}.
     * Synthetic count = {@code round(|real| × syntheticWeight)}, capped at available synthetic size.
     */
    private <T extends Output<T>> List<Example<T>> blendHybrid(
            String modelName, UUID distributorId,
            List<Example<T>> realFeatures,
            List<Example<T>> syntheticFeatures) {

        double realRatio        = phaseTracker.getRealDataRatio(modelName, distributorId);
        double syntheticWeight  = Math.max(0.2, 1.0 - realRatio);
        int    syntheticTarget  = (int) Math.round(realFeatures.size() * syntheticWeight);
        int    syntheticActual  = Math.min(syntheticTarget, syntheticFeatures.size());

        List<Example<T>> result = new ArrayList<>(realFeatures.size() + syntheticActual);
        result.addAll(realFeatures);
        if (syntheticActual > 0) {
            result.addAll(syntheticFeatures.subList(0, syntheticActual));
        }

        log.debug("[DataMixer] HYBRID realRatio={} synWeight={} real={} synAdded={}",
                String.format("%.3f", realRatio),
                String.format("%.3f", syntheticWeight),
                realFeatures.size(), syntheticActual);
        return result;
    }

    /**
     * Guarantee a minimum fraction of rare-class examples in the mixed dataset.
     *
     * <p>If the current fraction is already sufficient, returns {@code mixed} unchanged.
     * Otherwise supplements with rare-class examples from {@code syntheticExamples}
     * (up to the deficit needed to hit {@code minFraction}).
     */
    private <T extends Output<T>> List<Example<T>> preserveRareClass(
            List<Example<T>> mixed,
            List<Example<T>> syntheticExamples,
            Predicate<Example<T>> rareClassPredicate,
            double minFraction) {

        long   rareInMixed   = mixed.stream().filter(rareClassPredicate).count();
        double currentFrac   = (double) rareInMixed / mixed.size();

        if (currentFrac >= minFraction) {
            return mixed;
        }

        int requiredRare = (int) Math.ceil(mixed.size() * minFraction);
        int deficit      = requiredRare - (int) rareInMixed;

        List<Example<T>> syntheticRare = syntheticExamples.stream()
                .filter(rareClassPredicate)
                .limit(deficit)
                .collect(Collectors.toList());

        if (syntheticRare.isEmpty()) {
            return mixed;
        }

        List<Example<T>> result = new ArrayList<>(mixed.size() + syntheticRare.size());
        result.addAll(mixed);
        result.addAll(syntheticRare);

        log.debug("[DataMixer] Rare-class preservation: added {} synthetic examples "
                + "(currentFrac={} minFrac={})",
                syntheticRare.size(),
                String.format("%.3f", currentFrac),
                String.format("%.3f", minFraction));
        return result;
    }
}
