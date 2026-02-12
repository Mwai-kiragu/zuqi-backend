package com.zuqi.ai.credit;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LangChain4j AI Service for LLM-based credit scoring.
 *
 * Uses Ollama (Qwen 2.5 32B) to evaluate merchant creditworthiness
 * based on structured merchant profiles and peer comparisons.
 *
 * This is a programmatic AI service (not Spring-managed).
 * Instantiated manually in CreditScoringOrchestrator using AiServices.create().
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.5
 */
public interface CreditScoringAiService {

    /**
     * Evaluate merchant credit risk using LLM.
     *
     * System prompt defines credit evaluation rubric:
     * - Credit score: 0-100 (higher = lower risk)
     * - Risk categories: VERY_LOW (80-100), LOW (60-79), MEDIUM (40-59), HIGH (20-39), VERY_HIGH (0-19)
     * - Recommended credit limit based on business metrics and risk
     * - Reasoning for audit trail
     *
     * @param profile Merchant credit profile with features
     * @param peerContext Summary of similar merchants' performance
     * @return Structured credit evaluation
     */
    @SystemMessage("""
            You are a credit risk analyst for Zuqi, a field sales and supply chain platform in Kenya.
            Your task is to evaluate merchant creditworthiness based on their business profile and payment history.

            SCORING RUBRIC (0-100 scale):
            - 80-100 (VERY_LOW risk): Excellent payment history (>95% on-time), consistent orders, low utilization
            - 60-79 (LOW risk): Good payment history (80-95% on-time), stable orders, moderate utilization
            - 40-59 (MEDIUM risk): Fair payment history (60-80% on-time), some inconsistency, higher utilization
            - 20-39 (HIGH risk): Poor payment history (<60% on-time), irregular orders, overdue balances
            - 0-19 (VERY_HIGH risk): Severe payment issues, frequent delays, significant overdue amounts

            KEY EVALUATION FACTORS:
            1. Payment Behavior (40%):
               - On-time payment percentage (critical)
               - Average days to pay (benchmark: <30 days)
               - Consecutive on-time streak
               - Total overdue amount

            2. Order Consistency (30%):
               - Order frequency and trend
               - Order value stability (low stddev = good)
               - Days since last order (<14 days ideal)
               - Product diversification

            3. Credit Utilization (20%):
               - Current utilization ratio (<70% is healthy)
               - Utilization trend (decreasing = positive signal)
               - Peak utilization management

            4. Business Profile (10%):
               - Relationship tenure (longer = better)
               - Business category risk
               - Verification status
               - Geographic risk factors

            CREDIT LIMIT RECOMMENDATION:
            - Base on avg order value × order frequency × 4 weeks
            - Adjust by risk multiplier: 2.0x (VERY_LOW) to 0.5x (VERY_HIGH)
            - Floor: KES 10,000, Ceiling: KES 5,000,000
            - Round to nearest KES 10,000

            RESPONSE FORMAT:
            Provide a structured evaluation with:
            - creditScore: Integer 0-100
            - recommendedCreditLimit: Amount in KES
            - recommendation: APPROVE, INCREASE, DECREASE, REJECT, or MAINTAIN
            - reasoning: 2-3 sentences explaining the score
            - strengthFactors: List of 2-3 positive factors
            - riskFactors: List of 2-3 negative factors or concerns
            - recommendations: List of 2-3 actionable suggestions

            Be objective, data-driven, and conservative in credit assessment.
            """)
    @UserMessage("""
            Evaluate credit risk for the following merchant:

            {{profile}}

            PEER COMPARISON CONTEXT:
            {{peerContext}}

            Provide your credit evaluation in JSON format with the following structure:
            {
              "creditScore": <0-100>,
              "recommendedCreditLimit": <amount in KES>,
              "recommendation": "<APPROVE|INCREASE|DECREASE|REJECT|MAINTAIN>",
              "reasoning": "<explanation>",
              "strengthFactors": ["<factor1>", "<factor2>", "<factor3>"],
              "riskFactors": ["<factor1>", "<factor2>", "<factor3>"],
              "recommendations": ["<action1>", "<action2>", "<action3>"]
            }
            """)
    CreditEvaluationResponse evaluate(
            @V("profile") MerchantCreditProfile profile,
            @V("peerContext") String peerContext
    );

    /**
     * LLM response structure for parsing.
     */
    record CreditEvaluationResponse(
            int creditScore,
            double recommendedCreditLimit,
            String recommendation,
            String reasoning,
            java.util.List<String> strengthFactors,
            java.util.List<String> riskFactors,
            java.util.List<String> recommendations
    ) {}
}
