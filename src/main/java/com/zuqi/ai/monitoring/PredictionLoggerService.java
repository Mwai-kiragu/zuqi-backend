package com.zuqi.ai.monitoring;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.domain.ai.AIPrediction;
import com.zuqi.domain.ai.EntityType;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.AIPredictionRepository;
import com.zuqi.repository.DistributorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionLoggerService implements PredictionLogger {

    private final AIPredictionRepository predictionRepository;
    private final DistributorRepository  distributorRepository;
    private final ModelRegistry          modelRegistry;

    @Override
    @Transactional
    public AIPrediction logPrediction(
            String modelName,
            Integer modelVersion,
            EntityType entityType,
            UUID entityId,
            UUID distributorId,
            Map<String, Object> predictionValue,
            Double confidenceScore,
            String inputFeaturesHash) {

        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        AIPrediction prediction = AIPrediction.builder()
                .modelName(modelName)
                .modelVersion(modelVersion)
                .entityType(entityType)
                .entityId(entityId)
                .distributor(distributor)
                .predictionValue(predictionValue)
                .confidenceScore(confidenceScore)
                .inputFeaturesHash(inputFeaturesHash)
                .build();

        AIPrediction saved = predictionRepository.save(prediction);

        log.debug("Logged prediction: model={} v{}, entity={}:{}, confidence={}",
                modelName, modelVersion, entityType, entityId, confidenceScore);

        return saved;
    }

    /**
     * Convenience overload — resolves the active model version from the registry automatically.
     *
     * <p>Use this in inference services so that audit logs always capture the exact registry
     * version rather than a hardcoded integer (KCB traceability requirement).
     * Falls back to version {@code 0} if no active model is found (e.g. fallback path).
     */
    @Transactional
    public AIPrediction logPrediction(
            String modelName,
            EntityType entityType,
            UUID entityId,
            UUID distributorId,
            Map<String, Object> predictionValue,
            Double confidenceScore,
            String inputFeaturesHash) {

        Integer version = modelRegistry.getActiveModel(modelName)
                .map(m -> m.getModelVersion())
                .orElse(0);

        return logPrediction(modelName, version, entityType, entityId,
                distributorId, predictionValue, confidenceScore, inputFeaturesHash);
    }

    @Override
    @Transactional
    public AIPrediction logOverride(
            UUID predictionId,
            Map<String, Object> overrideValue,
            String overrideBy,
            String overrideReason) {

        AIPrediction prediction = predictionRepository.findById(predictionId)
                .orElseThrow(() -> new IllegalArgumentException("Prediction not found: " + predictionId));

        prediction.setWasOverridden(true);
        prediction.setOverrideValue(overrideValue);
        prediction.setOverrideBy(overrideBy);
        prediction.setOverrideReason(overrideReason);

        AIPrediction updated = predictionRepository.save(prediction);

        log.info("Logged override: prediction={}, by={}, reason={}",
                predictionId, overrideBy, overrideReason);

        return updated;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AIPrediction> getPredictionHistory(
            EntityType entityType,
            UUID entityId,
            int limit) {

        return predictionRepository.findByEntity(
                        entityType,
                        entityId,
                        PageRequest.of(0, limit))
                .getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AIPrediction> getLatestPrediction(EntityType entityType, UUID entityId) {
        return predictionRepository.findLatestByEntity(entityType, entityId);
    }

    @Override
    @Transactional(readOnly = true)
    public double calculateOverrideRate(String modelName, Integer modelVersion) {
        List<AIPrediction> predictions = predictionRepository
                .findByModelNameAndVersion(modelName, modelVersion);

        if (predictions.isEmpty()) {
            return 0.0;
        }

        long overrideCount = predictions.stream()
                .filter(AIPrediction::getWasOverridden)
                .count();

        double overrideRate = (double) overrideCount / predictions.size();

        log.debug("Override rate for {} v{}: {}/{} = {:.2%}",
                modelName, modelVersion, overrideCount, predictions.size(), overrideRate);

        return overrideRate;
    }
}
