package com.zuqi.ai.assistant;

import com.zuqi.ai.agent.tools.*;
import com.zuqi.ai.assistant.tools.CreditSummaryTool;
import com.zuqi.ai.assistant.tools.DemandForecastSummaryTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires AssistantAgent with 9 data tools and a DB-backed MessageWindowChatMemory.
 *
 * Memory design:
 * - chatMemoryProvider creates a per-conversation MessageWindowChatMemory(maxMessages=40)
 * - Each window is backed by AssistantChatMemoryStore which reads/writes ai_chat_messages
 * - On each turn LangChain4j loads the conversation from DB, sends proper
 *   alternating Human/AI messages to Ollama, then persists new messages back to DB
 * - Redis cache ("chat-history") is used by AssistantService for the history endpoint;
 *   the LangChain4j memory reads directly from the store (no Redis layer in between)
 */
@Configuration
@RequiredArgsConstructor
public class AssistantAgentConfig {

    private static final int MAX_MESSAGES_IN_WINDOW = 40; // 20 full turns

    private final AssistantChatMemoryStore chatMemoryStore;

    @Bean
    public AssistantAgent assistantAgent(
            ChatLanguageModel chatLanguageModel,
            SalesTrendTool salesTrendTool,
            InventoryHealthTool inventoryHealthTool,
            PaymentPerformanceTool paymentPerformanceTool,
            RepPerformanceTool repPerformanceTool,
            MerchantMetricsTool merchantMetricsTool,
            AnomalyAlertsTool anomalyAlertsTool,
            DeliveryMetricsTool deliveryMetricsTool,
            CreditSummaryTool creditSummaryTool,
            DemandForecastSummaryTool demandForecastSummaryTool) {

        return AiServices.builder(AssistantAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(MAX_MESSAGES_IN_WINDOW)
                                .chatMemoryStore(chatMemoryStore)
                                .build())
                .tools(salesTrendTool, inventoryHealthTool, paymentPerformanceTool,
                       repPerformanceTool, merchantMetricsTool, anomalyAlertsTool,
                       deliveryMetricsTool, creditSummaryTool, demandForecastSummaryTool)
                .build();
    }
}
