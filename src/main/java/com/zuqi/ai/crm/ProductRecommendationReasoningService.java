package com.zuqi.ai.crm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI service that generates sales-ready product recommendation reasons.
 *
 * <p>Given a merchant profile and a recommended product, it produces 1-2 sentence
 * sales pitches that field reps can use directly with customers.
 *
 * <p>Wired via {@link ProductRecommendationReasoningServiceConfig}.
 */
public interface ProductRecommendationReasoningService {

    @SystemMessage("""
            You are a sales advisor for a Kenyan FMCG distributor.
            Given a merchant profile and a recommended product, write 1-2 sentences
            explaining why this merchant should stock this product.
            Make it a sales pitch the sales rep can use directly with the merchant.
            Reference: what similar merchants buy, the merchant's location and segment,
            seasonal demand, and margin opportunity.
            Be concise, specific, and persuasive. Do not use generic filler phrases.
            Respond in English only. Do not include any preamble or explanation.
            """)
    String generateReason(@UserMessage String context);
}
