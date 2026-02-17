package com.zuqi.ai.model;

import com.zuqi.ai.event.ModelPromotedEvent;
import com.zuqi.domain.ai.AIModelRegistry;
import com.zuqi.domain.ai.ModelStatus;
import com.zuqi.repository.AIModelRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelRegistryService implements ModelRegistry {

    private final AIModelRegistryRepository modelRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public AIModelRegistry registerModel(
            String modelName,
            String algorithm,
            Map<String, Object> hyperparameters,
            String createdBy) {

        Integer nextVersion = calculateNextVersion(modelName);

        AIModelRegistry model = AIModelRegistry.builder()
                .modelName(modelName)
                .modelVersion(nextVersion)
                .algorithm(algorithm)
                .status(ModelStatus.TRAINING)
                .hyperparameters(hyperparameters)
                .createdBy(createdBy)
                .build();

        AIModelRegistry saved = modelRepository.save(model);
        log.info("Registered new model: {} v{} with algorithm: {}",
                modelName, nextVersion, algorithm);

        return saved;
    }

    @Override
    @Transactional
    public void updateModelAfterTraining(
            UUID modelId,
            Map<String, Object> performanceMetrics,
            byte[] modelBinary,
            Map<String, Object> featureColumns) {

        AIModelRegistry model = modelRepository.findById(modelId)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelId));

        model.setPerformanceMetrics(performanceMetrics);
        model.setModelBinary(modelBinary);
        model.setModelSizeBytes((long) modelBinary.length);
        model.setFeatureColumns(featureColumns);
        model.setStatus(ModelStatus.EVALUATING);

        modelRepository.save(model);
        log.info("Updated model {} v{} after training - size: {} bytes",
                model.getModelName(), model.getModelVersion(), modelBinary.length);
    }

    @Override
    @Transactional
    public AIModelRegistry promoteToActive(UUID modelId) {
        AIModelRegistry model = modelRepository.findById(modelId)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelId));

        if (model.getStatus() != ModelStatus.EVALUATING) {
            throw new IllegalStateException(
                    "Can only promote models in EVALUATING status. Current status: " + model.getStatus());
        }

        // Retire existing active model if any
        modelRepository.findLatestActiveModel(model.getModelName(), ModelStatus.ACTIVE)
                .ifPresent(activeModel -> {
                    activeModel.setStatus(ModelStatus.RETIRED);
                    activeModel.setRetiredAt(LocalDateTime.now());
                    modelRepository.save(activeModel);
                    log.info("Retired previous active model: {} v{}",
                            activeModel.getModelName(), activeModel.getModelVersion());
                });

        model.setStatus(ModelStatus.ACTIVE);
        model.setPromotedAt(LocalDateTime.now());
        AIModelRegistry updated = modelRepository.save(model);

        log.info("Promoted model to ACTIVE: {} v{}", model.getModelName(), model.getModelVersion());

        // Publish event for hot-swap (cache eviction)
        eventPublisher.publishEvent(new ModelPromotedEvent(
                this,
                model.getId(),
                model.getModelName(),
                model.getModelVersion()
        ));

        return updated;
    }

    @Override
    @Transactional
    public AIModelRegistry retireModel(UUID modelId) {
        AIModelRegistry model = modelRepository.findById(modelId)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelId));

        model.setStatus(ModelStatus.RETIRED);
        model.setRetiredAt(LocalDateTime.now());
        AIModelRegistry updated = modelRepository.save(model);

        log.info("Retired model: {} v{}", model.getModelName(), model.getModelVersion());
        return updated;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AIModelRegistry> getActiveModel(String modelName) {
        return modelRepository.findLatestActiveModel(modelName, ModelStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AIModelRegistry> getModel(String modelName, Integer version) {
        return modelRepository.findByModelNameAndModelVersion(modelName, version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AIModelRegistry> getAllVersions(String modelName) {
        return modelRepository.findAllVersionsByModelName(modelName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AIModelRegistry> getModelsByStatus(ModelStatus status) {
        return modelRepository.findByStatus(status);
    }

    private Integer calculateNextVersion(String modelName) {
        List<AIModelRegistry> existingVersions = modelRepository.findAllVersionsByModelName(modelName);
        if (existingVersions.isEmpty()) {
            return 1;
        }
        return existingVersions.get(0).getModelVersion() + 1;
    }
}
