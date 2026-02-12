package com.zuqi.ai.credit;

import com.zuqi.ai.monitoring.LlmMetricsService;
import com.zuqi.ai.monitoring.PredictionLogger;
import com.zuqi.domain.ai.EntityType;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.repository.MerchantRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates end-to-end credit evaluation workflow.
 *
 * Workflow:
 * 1. Build LLM credit profile from features
 * 2. Build peer comparison context via RAG
 * 3. Invoke LLM for credit evaluation
 * 4. Apply business rules overlay
 * 5. Determine approval routing (auto-approve vs manual review)
 * 6. Log prediction for audit trail
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.5
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditScoringOrchestrator {

    private final CreditFeatureBuilder featureBuilder;
    private final ChatLanguageModel chatLanguageModel;
    private final PredictionLogger predictionLogger;
    private final MerchantRepository merchantRepository;
    private final LlmMetricsService llmMetricsService;

    private static final String MODEL_NAME = "credit_scoring";
    private static final Integer MODEL_VERSION = 1;

    // Business rule thresholds
    private static final int MIN_TENURE_DAYS_FOR_AUTO_APPROVE = 30;
    private static final int OVERDUE_DAYS_FOR_AUTO_REJECT = 90;
    private static final BigDecimal AUTO_APPROVE_LIMIT_CEILING = BigDecimal.valueOf(500_000); // KES 500k
    private static final double MIN_CONFIDENCE_FOR_AUTO_APPROVE = 0.75;

    /**
     * Execute full credit evaluation for a merchant.
     *
     * @param merchantId Merchant to evaluate
     * @return Complete credit evaluation with routing decision
     */
    public CreditEvaluation evaluateMerchant(UUID merchantId) {
        log.info("Starting credit evaluation for merchant {}", merchantId);

        try {
            // Step 1: Build LLM-friendly credit profile
            MerchantCreditProfile profile = featureBuilder.buildLlmProfile(merchantId);
            log.debug("Built credit profile for merchant {}", profile.businessName());

            // Step 2: Build peer comparison context
            String peerContext = featureBuilder.buildPeerContext(merchantId);
            log.debug("Built peer context with {} characters", peerContext.length());

            // Step 3: Invoke LLM for evaluation with metrics tracking
            CreditScoringAiService aiService = AiServices.create(CreditScoringAiService.class, chatLanguageModel);

            CreditScoringAiService.CreditEvaluationResponse llmResponse = llmMetricsService.recordOperation(
                    "ollama",
                    "qwen2.5:32b",
                    "credit_scoring",
                    () -> aiService.evaluate(profile, peerContext)
            );

            log.info("LLM evaluated merchant {} with score {}", merchantId, llmResponse.creditScore());

            // Step 4: Apply business rules overlay
            CreditEvaluation evaluation = applyBusinessRules(merchantId, profile, llmResponse);

            // Step 5: Determine routing (auto-approve vs manual review)
            String routingDecision = determineRouting(evaluation, profile);
            log.info("Credit evaluation complete for {}: {} (routing: {})",
                    merchantId, evaluation.recommendation(), routingDecision);

            // Step 6: Log prediction for audit trail
            logPrediction(merchantId, evaluation);

            return evaluation;

        } catch (Exception e) {
            log.error("Credit evaluation failed for merchant {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("Credit evaluation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Apply business rules overlay to LLM evaluation.
     *
     * Hard business rules that override LLM judgment:
     * - 90+ days overdue → automatic F grade
     * - < 30 day tenure → max grade C (60 points)
     * - First evaluation → limit capped at KES 500k
     * - Limit cannot exceed 200% of monthly order value
     */
    private CreditEvaluation applyBusinessRules(
            UUID merchantId,
            MerchantCreditProfile profile,
            CreditScoringAiService.CreditEvaluationResponse llmResponse
    ) {
        int adjustedScore = llmResponse.creditScore();
        BigDecimal adjustedLimit = BigDecimal.valueOf(llmResponse.recommendedCreditLimit());
        String adjustedRecommendation = llmResponse.recommendation();

        // Rule 1: 90+ days overdue → automatic severe risk
        if (profile.paymentHistory().worstDaysToPay() >= OVERDUE_DAYS_FOR_AUTO_REJECT) {
            log.warn("Merchant {} has {}+ days overdue - applying severe risk override",
                    merchantId, OVERDUE_DAYS_FOR_AUTO_REJECT);
            adjustedScore = Math.min(adjustedScore, 15); // Cap at VERY_HIGH risk
            adjustedRecommendation = "REJECT";
        }

        // Rule 2: < 30 day tenure → cap at MEDIUM risk
        if (profile.relationshipTenureDays() < MIN_TENURE_DAYS_FOR_AUTO_APPROVE) {
            log.info("Merchant {} has only {} days tenure - capping at MEDIUM risk",
                    merchantId, profile.relationshipTenureDays());
            adjustedScore = Math.min(adjustedScore, 59); // Cap at MEDIUM
        }

        // Rule 3: First evaluation or low tenure → limit ceiling
        if (profile.creditUtilization().currentCreditLimit().compareTo(BigDecimal.ZERO) == 0
                || profile.relationshipTenureDays() < 90) {
            adjustedLimit = adjustedLimit.min(AUTO_APPROVE_LIMIT_CEILING);
        }

        // Rule 4: Limit cannot exceed 200% of monthly order value
        BigDecimal monthlyOrderValue = profile.orderBehavior().avgOrderValue()
                .multiply(BigDecimal.valueOf(profile.orderBehavior().orderFrequencyPerWeek() * 4));
        BigDecimal maxAllowedLimit = monthlyOrderValue.multiply(BigDecimal.valueOf(2.0));
        if (adjustedLimit.compareTo(maxAllowedLimit) > 0) {
            log.info("Capping limit at 200% of monthly order value: {} -> {}",
                    adjustedLimit, maxAllowedLimit);
            adjustedLimit = maxAllowedLimit;
        }

        // Recalculate recommendation based on adjusted values
        adjustedRecommendation = CreditEvaluation.determineRecommendation(
                adjustedScore,
                profile.creditUtilization().currentCreditLimit(),
                adjustedLimit
        );

        CreditEvaluation.RiskCategory riskCategory = CreditEvaluation.determineRiskCategory(adjustedScore);

        return CreditEvaluation.builder()
                .merchantId(merchantId.toString())
                .creditScore(adjustedScore)
                .riskCategory(riskCategory)
                .recommendedCreditLimit(adjustedLimit)
                .currentCreditLimit(profile.creditUtilization().currentCreditLimit())
                .recommendation(adjustedRecommendation)
                .reasoning(llmResponse.reasoning())
                .strengthFactors(llmResponse.strengthFactors())
                .riskFactors(llmResponse.riskFactors())
                .recommendations(llmResponse.recommendations())
                .evaluatedAt(LocalDateTime.now())
                .modelVersion(MODEL_NAME + "-v" + MODEL_VERSION)
                .build();
    }

    /**
     * Determine routing: auto-approve vs manual review.
     *
     * Auto-approval criteria:
     * - Score >= 60 (LOW or VERY_LOW risk)
     * - Recommended limit <= KES 500k
     * - Tenure >= 30 days
     * - No overdue > 60 days
     *
     * @return "AUTO_APPROVE", "MANUAL_REVIEW", or "AUTO_REJECT"
     */
    private String determineRouting(CreditEvaluation evaluation, MerchantCreditProfile profile) {
        // Auto-reject cases
        if (evaluation.creditScore() < 20 || "REJECT".equals(evaluation.recommendation())) {
            return "AUTO_REJECT";
        }

        // Auto-approve criteria
        boolean scoreAcceptable = evaluation.creditScore() >= 60;
        boolean limitAcceptable = evaluation.recommendedCreditLimit().compareTo(AUTO_APPROVE_LIMIT_CEILING) <= 0;
        boolean tenureAcceptable = profile.relationshipTenureDays() >= MIN_TENURE_DAYS_FOR_AUTO_APPROVE;
        boolean noSevereOverdue = profile.paymentHistory().worstDaysToPay() < 60;

        if (scoreAcceptable && limitAcceptable && tenureAcceptable && noSevereOverdue) {
            return "AUTO_APPROVE";
        }

        // Default to manual review
        return "MANUAL_REVIEW";
    }

    /**
     * Log prediction to audit trail.
     */
    private void logPrediction(UUID merchantId, CreditEvaluation evaluation) {
        try {
            Merchant merchant = merchantRepository.findById(merchantId)
                    .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

            Map<String, Object> predictionValue = new HashMap<>();
            predictionValue.put("creditScore", evaluation.creditScore());
            predictionValue.put("riskCategory", evaluation.riskCategory().name());
            predictionValue.put("recommendedCreditLimit", evaluation.recommendedCreditLimit());
            predictionValue.put("recommendation", evaluation.recommendation());
            predictionValue.put("reasoning", evaluation.reasoning());

            predictionLogger.logPrediction(
                    MODEL_NAME,
                    MODEL_VERSION,
                    EntityType.MERCHANT,
                    merchantId,
                    merchant.getDistributor().getId(),
                    predictionValue,
                    (double) evaluation.creditScore() / 100.0, // confidence proxy
                    "features_hash_placeholder" // TODO: Implement feature hashing
            );
        } catch (Exception e) {
            log.error("Failed to log prediction for merchant {}: {}", merchantId, e.getMessage());
            // Don't fail the evaluation if logging fails
        }
    }
}
