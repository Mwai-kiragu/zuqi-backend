package com.zuqi.ai.model;

import com.zuqi.domain.ai.AIModelRegistry;
import com.zuqi.domain.ai.ModelStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.tribuo.Model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelLoaderService implements ModelLoader {

    private final ModelRegistry modelRegistry;
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    @Override
    @Cacheable(value = "aiModels", key = "#modelName")
    public <T extends Model<?>> T loadModel(String modelName) {
        log.debug("Cache miss for model: {}", modelName);
        cacheMisses.incrementAndGet();

        AIModelRegistry modelRegistry = this.modelRegistry.getActiveModel(modelName)
                .orElseThrow(() -> new ModelNotFoundException(
                        "No active model found with name: " + modelName));

        return deserializeModel(modelRegistry);
    }

    @Override
    @Cacheable(value = "aiModels", key = "#modelName + '_v' + #version")
    public <T extends Model<?>> T loadModel(String modelName, Integer version) {
        log.debug("Cache miss for model: {} v{}", modelName, version);
        cacheMisses.incrementAndGet();

        AIModelRegistry modelRegistry = this.modelRegistry.getModel(modelName, version)
                .orElseThrow(() -> new ModelNotFoundException(
                        String.format("Model not found: %s v%d", modelName, version)));

        if (modelRegistry.getStatus() == ModelStatus.TRAINING) {
            throw new ModelNotFoundException(
                    String.format("Model %s v%d is still in TRAINING status", modelName, version));
        }

        return deserializeModel(modelRegistry);
    }

    @Override
    @CacheEvict(value = "aiModels", key = "#modelName")
    public void evictModel(String modelName) {
        log.info("Evicted model from cache: {}", modelName);
    }

    @Override
    public void warmUpCache() {
        log.info("Warming up model cache...");
        modelRegistry.getModelsByStatus(ModelStatus.ACTIVE).forEach(model -> {
            try {
                loadModel(model.getModelName());
                log.info("Pre-loaded model: {} v{}", model.getModelName(), model.getModelVersion());
            } catch (Exception e) {
                log.error("Failed to pre-load model: {} v{}",
                        model.getModelName(), model.getModelVersion(), e);
            }
        });
        log.info("Model cache warm-up complete");
    }

    @Override
    public CacheStats getCacheStats() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total : 0.0;

        return new CacheStats(hits, misses, hits + misses, hitRate);
    }

    @SuppressWarnings("unchecked")
    private <T extends Model<?>> T deserializeModel(AIModelRegistry modelRegistry) {
        byte[] modelBinary = modelRegistry.getModelBinary();

        if (modelBinary == null || modelBinary.length == 0) {
            throw new ModelLoadException(
                    "Model binary is empty for: " + modelRegistry.getModelName() +
                            " v" + modelRegistry.getModelVersion(), null);
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(modelBinary);
             ObjectInputStream ois = new ObjectInputStream(bis)) {

            T model = (T) ois.readObject();
            cacheHits.incrementAndGet();

            log.info("Loaded model: {} v{} (size: {} bytes)",
                    modelRegistry.getModelName(),
                    modelRegistry.getModelVersion(),
                    modelBinary.length);

            return model;

        } catch (IOException | ClassNotFoundException e) {
            throw new ModelLoadException(
                    String.format("Failed to deserialize model: %s v%d",
                            modelRegistry.getModelName(), modelRegistry.getModelVersion()),
                    e);
        }
    }
}
