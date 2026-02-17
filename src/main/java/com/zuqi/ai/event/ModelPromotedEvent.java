package com.zuqi.ai.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Event published when a model is promoted to ACTIVE status.
 *
 * This event triggers automatic cache eviction for hot-swap deployment.
 * When a new model version is promoted, the old version is evicted from cache,
 * and subsequent predictions will use the new model without restart.
 *
 * Blueprint: implementation_plan.md Phase 3 Task 3.1
 */
@Getter
public class ModelPromotedEvent extends ApplicationEvent {

    private final UUID modelId;
    private final String modelName;
    private final Integer modelVersion;

    public ModelPromotedEvent(Object source, UUID modelId, String modelName, Integer modelVersion) {
        super(source);
        this.modelId = modelId;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
    }
}
