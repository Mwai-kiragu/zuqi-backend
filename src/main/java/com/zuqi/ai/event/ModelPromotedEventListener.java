package com.zuqi.ai.event;

import com.zuqi.ai.model.ModelLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event listener for model promotion events.
 *
 * When a model is promoted to ACTIVE status, this listener automatically
 * evicts the old model from cache to enable hot-swap deployment.
 *
 * Workflow:
 * 1. ModelRegistryService.promoteToActive() publishes ModelPromotedEvent
 * 2. This listener catches the event asynchronously
 * 3. Calls ModelLoader.evictModel() to clear cache
 * 4. Next prediction call will load the new ACTIVE model
 *
 * No application restart required.
 *
 * Blueprint: implementation_plan.md Phase 3 Task 3.1
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModelPromotedEventListener {

    private final ModelLoader modelLoader;

    /**
     * Handle model promotion event by evicting old model from cache.
     *
     * @param event Model promotion event
     */
    @EventListener
    @Async
    public void handleModelPromoted(ModelPromotedEvent event) {
        log.info("Model promoted: {} v{} - evicting from cache for hot-swap",
                event.getModelName(), event.getModelVersion());

        try {
            // Evict old model from cache - next loadModel() call will fetch the new ACTIVE version
            modelLoader.evictModel(event.getModelName());

            log.info("Successfully hot-swapped model: {} v{} is now active",
                    event.getModelName(), event.getModelVersion());

        } catch (Exception e) {
            log.error("Failed to evict model {} from cache: {}",
                    event.getModelName(), e.getMessage(), e);
        }
    }
}
