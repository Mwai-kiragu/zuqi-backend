package com.zuqi.ai.assistant;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the report generation AI service.
 * No tools — the report context is pre-assembled by AssistantReportBuilder.
 *
 * Uses the dedicated reportChatLanguageModel (300s timeout) instead of the
 * shared chatLanguageModel (30s) to handle large report prompts via Ollama.
 */
@Configuration
public class AssistantReportAiServiceConfig {

    @Bean
    public AssistantReportAiService assistantReportAiService(
            @Qualifier("reportChatLanguageModel") ChatLanguageModel reportChatLanguageModel) {
        return AiServices.builder(AssistantReportAiService.class)
                .chatLanguageModel(reportChatLanguageModel)
                .build();
    }
}
