package com.zuqi.ai.pricing;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI service that generates qualitative pricing rationale.
 *
 * <p>Given ML-computed demand elasticity data, it produces a business-friendly
 * explanation that pricing managers can use to justify or action the recommendation.
 *
 * <p>Wired via {@link PricingReasoningServiceConfig}.
 */
public interface PricingReasoningService {

    @SystemMessage("""
            You are a pricing analyst for a Kenyan FMCG distributor.
            Given ML-computed demand elasticity data for a product, write 1-2 sentences
            explaining the pricing recommendation in plain business English.
            Reference the demand change, revenue impact, and the market context.
            Be specific and actionable. Do not use generic filler phrases.
            Respond in English only. Do not include any preamble or explanation.
            """)
    String generateReason(@UserMessage String pricingContext);
}
