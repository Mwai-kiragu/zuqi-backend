package com.zuqi.ai.reporting;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that wires the {@link ComplianceReportAiService} bean.
 *
 * Uses the LangChain4j {@code AiServices} builder to create a proxy implementation
 * of the {@link ComplianceReportAiService} interface backed by the Ollama/Qwen 2.5
 * {@code ChatLanguageModel} bean registered in {@code LangChain4jConfig}.
 *
 * No tools are registered because compliance report generation relies solely on
 * the structured context passed in the user message — no live data lookups are needed.
 *
 * Blueprint reference: implementation_plan.md Phase 6 Task 6.3
 */
@Configuration
@RequiredArgsConstructor
public class ComplianceReportAiServiceConfig {

    @Bean
    public ComplianceReportAiService complianceReportAiService(ChatLanguageModel chatLanguageModel) {
        return AiServices.builder(ComplianceReportAiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }
}
