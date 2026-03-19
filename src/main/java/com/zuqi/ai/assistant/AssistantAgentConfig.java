package com.zuqi.ai.assistant;

import com.zuqi.ai.agent.tools.*;
import com.zuqi.ai.assistant.tools.CreditSummaryTool;
import com.zuqi.ai.assistant.tools.DemandForecastSummaryTool;
// New module tools
import com.zuqi.ai.agent.tools.InvoiceTool;
import com.zuqi.ai.agent.tools.ExpensesTool;
import com.zuqi.ai.agent.tools.ProcurementTool;
import com.zuqi.ai.agent.tools.FundsTransferTool;
import com.zuqi.ai.agent.tools.PosSalesTool;
import com.zuqi.ai.agent.tools.StockTransferTool;
// Financial statement tools
import com.zuqi.ai.agent.tools.BalanceSheetTool;
import com.zuqi.ai.agent.tools.ProfitLossTool;
import com.zuqi.ai.agent.tools.TrialBalanceTool;
import com.zuqi.ai.agent.tools.CashFlowTool;
import com.zuqi.ai.agent.tools.ArAgingTool;
import com.zuqi.ai.agent.tools.ApAgingTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires AssistantAgent with 21 data tools and a DB-backed MessageWindowChatMemory.
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
            DemandForecastSummaryTool demandForecastSummaryTool,
            InvoiceTool invoiceTool,
            ExpensesTool expensesTool,
            ProcurementTool procurementTool,
            FundsTransferTool fundsTransferTool,
            PosSalesTool posSalesTool,
            StockTransferTool stockTransferTool,
            BalanceSheetTool balanceSheetTool,
            ProfitLossTool profitLossTool,
            TrialBalanceTool trialBalanceTool,
            CashFlowTool cashFlowTool,
            ArAgingTool arAgingTool,
            ApAgingTool apAgingTool,
            HelpTool helpTool) {

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
                       deliveryMetricsTool, creditSummaryTool, demandForecastSummaryTool,
                       invoiceTool, expensesTool, procurementTool,
                       fundsTransferTool, posSalesTool, stockTransferTool,
                       balanceSheetTool, profitLossTool, trialBalanceTool,
                       cashFlowTool, arAgingTool, apAgingTool, helpTool)
                .build();
    }
}
