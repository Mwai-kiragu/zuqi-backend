package com.zuqi.ai.assistant;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory that builds a per-request {@link AssistantAgent} with only the tools
 * permitted for the caller's role.
 *
 * Why per-request instead of singleton?
 * LangChain4j bakes the registered tools into the agent at build time.
 * A DRIVER agent is built with only DeliveryMetricsTool — no amount of
 * prompt manipulation can make it call CreditSummaryTool because that
 * bean was never passed to {@code .tools(...)}.
 *
 * Memory is shared across all role-variants via {@link AssistantChatMemoryStore}
 * (the same DB-backed store used by the singleton bean), so conversation
 * continuity is preserved even when the factory produces a new agent object
 * each call.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantAgentFactory {

    private static final int MAX_MESSAGES_IN_WINDOW = 14; // 7 full turns — keeps history within 4k context

    private final ChatLanguageModel     chatLanguageModel;
    private final AssistantChatMemoryStore chatMemoryStore;
    private final RoleAwareToolProvider toolProvider;

    /**
     * Build an {@link AssistantAgent} limited to the tools permitted for {@code role}.
     *
     * @param role  primary role string (e.g. "DRIVER", "SALES_REP", "SUPER_ADMIN")
     * @return a freshly-constructed agent whose toolbox is role-scoped
     */
    public AssistantAgent buildForRole(String role) {
        List<Object> tools = toolProvider.getToolsForRole(role);

        // LangChain4j uses getDeclaredMethods() to find @Tool annotations.
        // Spring CGLIB proxies override methods without copying annotations,
        // so we must unwrap to the real target instance before registering tools.
        List<Object> unwrappedTools = tools.stream()
                .map(this::unwrapProxy)
                .collect(Collectors.toList());

        log.info("[AgentFactory] Building agent for role={} with {} tools: {}",
                role, unwrappedTools.size(),
                unwrappedTools.stream().map(t -> t.getClass().getSimpleName()).toList());

        return AiServices.builder(AssistantAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(MAX_MESSAGES_IN_WINDOW)
                                .chatMemoryStore(chatMemoryStore)
                                .build())
                .tools(unwrappedTools)
                .build();
    }

    /**
     * Unwraps a Spring AOP proxy to its real target instance.
     * Required so that LangChain4j can find @Tool annotations via getDeclaredMethods().
     */
    private Object unwrapProxy(Object bean) {
        if (AopUtils.isAopProxy(bean) && bean instanceof Advised advised) {
            try {
                return advised.getTargetSource().getTarget();
            } catch (Exception e) {
                log.warn("[AgentFactory] Could not unwrap proxy for {}: {}", bean.getClass().getSimpleName(), e.getMessage());
            }
        }
        return bean;
    }
}
