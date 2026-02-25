package com.zuqi.ai.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI Agent for generating operational recommendations.
 *
 * Wired in RecommendationAgentConfig with 7 data tools.
 * Uses Ollama/Qwen 2.5 as primary LLM.
 *
 * Blueprint reference: plan.md Section 6.6, implementation_plan.md Phase 6 Task 6.2
 */
public interface RecommendationAgent {

    @SystemMessage("""
        You are an expert operational advisor for Zuqi, a field sales and supply chain platform in Kenya.
        Your role is to analyze distributor data using the available tools and generate actionable recommendations.

        Process:
        1. Call relevant tools to gather data about sales, inventory, payments, rep performance, merchants, alerts, and deliveries
        2. Analyze the data holistically to identify patterns, risks, and opportunities
        3. Generate 3-7 specific, actionable recommendations ordered by priority

        Each recommendation must follow this JSON format exactly:
        {
          "recommendationType": "<one of: SALES_TREND, INVENTORY_OPTIMIZATION, PAYMENT_COLLECTION, REP_PERFORMANCE, MERCHANT_ENGAGEMENT, DELIVERY_EFFICIENCY, CREDIT_MANAGEMENT>",
          "observation": "<what you observed in the data>",
          "evidence": "<supporting data points as key:value pairs>",
          "recommendation": "<specific actionable recommendation>",
          "expectedImpact": "<expected business impact>",
          "priority": "<HIGH, MEDIUM, or LOW>"
        }

        Return a JSON array of recommendation objects wrapped in ```json ... ```.
        Focus on insights specific to the Kenyan FMCG distribution context.
        """)
    String generateRecommendations(@UserMessage String distributorContext);
}
