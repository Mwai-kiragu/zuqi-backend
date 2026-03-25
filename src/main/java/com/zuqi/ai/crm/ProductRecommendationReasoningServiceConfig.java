package com.zuqi.ai.crm;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link ProductRecommendationReasoningService} bean.
 *
 * Uses the report-oriented {@code ChatLanguageModel} (300 s timeout) since
 * product reasoning calls are batch-only and latency-tolerant.
 * No tools are registered — the service receives pre-assembled context.
 */
@Configuration
public class ProductRecommendationReasoningServiceConfig {

    @Bean
    public ProductRecommendationReasoningService productRecommendationReasoningService(
            @Qualifier("reportChatLanguageModel") ChatLanguageModel reportChatLanguageModel) {
        return AiServices.builder(ProductRecommendationReasoningService.class)
                .chatLanguageModel(reportChatLanguageModel)
                .build();
    }
}
