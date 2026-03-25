package com.zuqi.ai.pricing;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link PricingReasoningService} bean.
 *
 * Uses the report-oriented {@code ChatLanguageModel} (300 s timeout) since
 * pricing reasoning calls are batch-only and latency-tolerant.
 * No tools — receives pre-assembled elasticity context.
 */
@Configuration
public class PricingReasoningServiceConfig {

    @Bean
    public PricingReasoningService pricingReasoningService(
            @Qualifier("reportChatLanguageModel") ChatLanguageModel reportChatLanguageModel) {
        return AiServices.builder(PricingReasoningService.class)
                .chatLanguageModel(reportChatLanguageModel)
                .build();
    }
}
