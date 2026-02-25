package com.zuqi.ai.agent;

import com.zuqi.ai.agent.tools.AnomalyAlertsTool;
import com.zuqi.ai.agent.tools.DeliveryMetricsTool;
import com.zuqi.ai.agent.tools.InventoryHealthTool;
import com.zuqi.ai.agent.tools.MerchantMetricsTool;
import com.zuqi.ai.agent.tools.PaymentPerformanceTool;
import com.zuqi.ai.agent.tools.RepPerformanceTool;
import com.zuqi.ai.agent.tools.SalesTrendTool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that wires the RecommendationAgent bean with its 7 data tools.
 *
 * Uses LangChain4j AiServices builder to attach tool-calling capabilities to the
 * Ollama/Qwen 2.5 chat model. The agent can invoke any combination of the registered
 * tools in a single reasoning cycle before returning its final recommendation JSON.
 *
 * Blueprint reference: plan.md Section 6.6, implementation_plan.md Phase 6 Task 6.2
 */
@Configuration
@RequiredArgsConstructor
public class RecommendationAgentConfig {

    @Bean
    public RecommendationAgent recommendationAgent(
            ChatLanguageModel chatLanguageModel,
            SalesTrendTool salesTrendTool,
            InventoryHealthTool inventoryHealthTool,
            PaymentPerformanceTool paymentPerformanceTool,
            RepPerformanceTool repPerformanceTool,
            MerchantMetricsTool merchantMetricsTool,
            AnomalyAlertsTool anomalyAlertsTool,
            DeliveryMetricsTool deliveryMetricsTool) {

        return AiServices.builder(RecommendationAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(salesTrendTool, inventoryHealthTool, paymentPerformanceTool,
                       repPerformanceTool, merchantMetricsTool, anomalyAlertsTool,
                       deliveryMetricsTool)
                .build();
    }
}
