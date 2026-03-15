package com.zuqi.ai.assistant;

import java.util.UUID;

/**
 * Thread-local holder that passes conversation context (distributorId, userId, modelName)
 * from AssistantService into AssistantChatMemoryStore.
 *
 * LangChain4j's ChatMemoryStore interface only receives the memoryId — it has no
 * knowledge of the current HTTP request or Spring security context.  We bridge that
 * gap with a ThreadLocal that AssistantService sets before calling the agent and clears
 * in a finally block after the call returns.
 */
public final class AssistantMemoryContext {

    private static final ThreadLocal<MemoryContext> HOLDER = new ThreadLocal<>();

    private AssistantMemoryContext() {}

    public record MemoryContext(UUID distributorId, UUID userId, String modelName) {}

    public static void set(UUID distributorId, UUID userId, String modelName) {
        HOLDER.set(new MemoryContext(distributorId, userId, modelName));
    }

    public static MemoryContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
