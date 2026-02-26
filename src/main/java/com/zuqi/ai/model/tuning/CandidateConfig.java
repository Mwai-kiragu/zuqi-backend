package com.zuqi.ai.model.tuning;

import org.tribuo.Output;
import org.tribuo.Trainer;

import java.util.Map;

/**
 * Pairs a Tribuo {@link Trainer} with its hyperparameter map.
 *
 * <p>The map is stored separately from the trainer so that the best configuration
 * can be logged and persisted in the model registry without reflection.
 *
 * @param <T>              Tribuo output type (Label, Regressor, Event)
 * @param trainer          the fully-configured trainer instance
 * @param hyperparameters  human-readable representation of the trainer's parameters
 */
public record CandidateConfig<T extends Output<T>>(
        Trainer<T>          trainer,
        Map<String, Object> hyperparameters) {}
