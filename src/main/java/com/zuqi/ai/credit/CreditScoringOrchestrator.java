package com.zuqi.ai.credit;

import com.zuqi.ai.config.HybridScoringConfig;
import com.zuqi.ai.monitoring.LlmMetricsService;
import com.zuqi.ai.monitoring.PredictionLogger;
import com.zuqi.ai.synthetic.DataMixer;
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
import java.util.Random;
import java.util.UUID;

/**
 * Orchestrates end-to-end credit evaluation workflow with hybrid ML+LLM scoring.
 *
 * Supports three modes:
 * - LLM_ONLY: 100% LLM scoring (Phase 2)
 * - ML_ONLY: 100% ML scoring (Phase 5+)
 * - HYBRID: ML primary (70%) + LLM validation (30%) (Phase 3-4)
 *
 * Hybrid Workflow:
 * 1. ML scoring (fast, 50-100ms)
 * 2. Selective LLM validation (30% of cases)
 * 3. Score blending (ML 70% + LLM 30%)
 * 4. Discrepancy flagging (>15 point difference)
 * 5. Business rules overlay
 * 6. Routing decision
 * 7. Audit logging
 *
 * Blueprint reference: ML_IMPLEMENTATION_PLAN.md Task 6
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
    private final CreditClassifier creditClassifier;
    private final CreditLimitRegressor creditLimitRegressor;
    private final HybridScoringConfig hybridConfig;
    private final DataMixer dataMixer;

    private final Random random = new Random();

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
     * Routes to appropriate scoring mode based on configuration.
     *
     * @param merchantId Merchant to evaluate
     * @return Complete credit evaluation with routing decision
     */
    public CreditEvaluation evaluateMerchant(UUID merchantId) {
        log.info("Starting credit evaluation for merchant {} (mode: {})", merchantId, hybridConfig.getMode());

        try {
            hybridConfig.validate(); // Ensure configuration is valid

            CreditEvaluation evaluation = switch (hybridConfig.getMode()) {
                case LLM_ONLY -> evaluateWithLlm(merchantId);
                case ML_ONLY -> evaluateWithMl(merchantId);
                case HYBRID -> evaluateHybrid(merchantId);
            };

            // Determine routing (auto-approve vs manual review)
            MerchantCreditProfile profile = featureBuilder.buildLlmProfile(merchantId);
            String routingDecision = determineRouting(evaluation, profile);
            log.info("Credit evaluation complete for {}: {} (routing: {})",
                    merchantId, evaluation.recommendation(), routingDecision);

            // Log prediction for audit trail
            logPrediction(merchantId, evaluation);

            return evaluation;

        } catch (Exception e) {
            log.error("Credit evaluation failed for merchant {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("Credit evaluation failed: " + e.getMessage(), e);
        }
    }

    /**
     * LLM-only evaluation (Phase 2 mode).
     */
    private CreditEvaluation evaluateWithLlm(UUID merchantId) throws Exception {
        log.debug("Evaluating with LLM only");

        // Build LLM-friendly credit profile
        MerchantCreditProfile profile = featureBuilder.buildLlmProfile(merchantId);
        log.debug("Built credit profile for merchant {}", profile.businessName());

        // Build peer comparison context
        String peerContext = featureBuilder.buildPeerContext(merchantId);
        log.debug("Built peer context with {} characters", peerContext.length());

        // Invoke LLM for evaluation with metrics tracking
        CreditScoringAiService aiService = AiServices.create(CreditScoringAiService.class, chatLanguageModel);

        CreditScoringAiService.CreditEvaluationResponse llmResponse = llmMetricsService.recordOperation(
                "ollama",
                "qwen2.5:32b",
                "credit_scoring",
                () -> aiService.evaluate(profile, peerContext)
        );

        log.info("LLM evaluated merchant {} with score {}", merchantId, llmResponse.creditScore());

        // Apply business rules overlay
        return applyBusinessRules(merchantId, profile, llmResponse);
    }

    /**
     * ML-only evaluation (Phase 5+ mode).
     */
    private CreditEvaluation evaluateWithMl(UUID merchantId) {
        log.debug("Evaluating with ML only");

        // Run ML classifier
        CreditClassifier.CreditClassifierResult classifierResult = creditClassifier.predict(merchantId);
        log.info("ML classifier: score={}, confidence={}",
                classifierResult.creditScore(), classifierResult.confidence());

        // Run ML regressor for credit limit
        BigDecimal predictedLimit = creditLimitRegressor.predictCreditLimit(merchantId);
        log.info("ML regressor: predicted limit={}", predictedLimit);

        // Build profile for business rules
        MerchantCreditProfile profile = featureBuilder.buildLlmProfile(merchantId);

        // Convert ML result to CreditEvaluation
        return buildEvaluationFromMl(merchantId, profile, classifierResult, predictedLimit);
    }

    /**
     * Hybrid evaluation (Phase 3-4 mode): ML primary + selective LLM validation.
     */
    private CreditEvaluation evaluateHybrid(UUID merchantId) throws Exception {
        log.debug("Evaluating with HYBRID mode (ML 70% + LLM 30%)");

        // Step 1: ML scoring (always runs - fast path)
        CreditClassifier.CreditClassifierResult mlClassifier = creditClassifier.predict(merchantId);
        BigDecimal mlLimit = creditLimitRegressor.predictCreditLimit(merchantId);

        log.info("ML evaluation: score={}, confidence={:.2f}, limit={}",
                mlClassifier.creditScore(), mlClassifier.confidence(), mlLimit);

        // Build profile for decision logic
        MerchantCreditProfile profile = featureBuilder.buildLlmProfile(merchantId);

        // Step 2: Determine if LLM validation needed
        boolean needsLlmValidation = shouldValidateWithLlm(mlClassifier, mlLimit, profile);

        if (!needsLlmValidation) {
            // ML-only path (70% of cases)
            log.info("ML-only evaluation for merchant {} (no LLM validation needed)", merchantId);
            return buildEvaluationFromMl(merchantId, profile, mlClassifier, mlLimit);
        }

        // Step 3: LLM validation path (30% of cases)
        log.info("LLM validation triggered for merchant {}", merchantId);

        String peerContext = featureBuilder.buildPeerContext(merchantId);
        CreditScoringAiService aiService = AiServices.create(CreditScoringAiService.class, chatLanguageModel);

        CreditScoringAiService.CreditEvaluationResponse llmResponse = llmMetricsService.recordOperation(
                "ollama",
                "qwen2.5:32b",
                "credit_scoring",
                () -> aiService.evaluate(profile, peerContext)
        );

        log.info("LLM evaluation: score={}", llmResponse.creditScore());

        // Step 4: Blend ML and LLM scores
        return blendEvaluations(merchantId, profile, mlClassifier, mlLimit, llmResponse);
    }

    /**
     * Determine if LLM validation is needed for this merchant.
     */
    private boolean shouldValidateWithLlm(CreditClassifier.CreditClassifierResult mlResult,
                                           BigDecimal mlLimit,
                                           MerchantCreditProfile profile) {
        HybridScoringConfig.LlmValidationTriggers triggers = hybridConfig.getLlmValidationTriggers();

        // Trigger 1: High-value credit limit
        if (mlLimit.compareTo(triggers.getHighValueLimit()) > 0) {
            log.debug("LLM validation: high-value limit ({})", mlLimit);
            return true;
        }

        // Trigger 2: Low ML confidence
        if (mlResult.confidence() < triggers.getLowMlConfidence()) {
            log.debug("LLM validation: low ML confidence ({:.2f})", mlResult.confidence());
            return true;
        }

        // Trigger 3: New merchant category (check against training categories)
        if (triggers.isNewCategoryValidation() && isNewCategory(profile.businessCategory())) {
            log.debug("LLM validation: new category ({})", profile.businessCategory());
            return true;
        }

        // Trigger 4: Random sample for comparison
        if (random.nextDouble() < triggers.getSampleRate()) {
            log.debug("LLM validation: random sample");
            return true;
        }

        return false;
    }

    /**
     * Check if merchant category is new (not in training data).
     */
    private boolean isNewCategory(String category) {
        // Training data categories (from SyntheticMerchantDataGenerator)
        var knownCategories = java.util.Set.of(
                "Hardware Store", "General Store", "Supermarket", "Kiosk",
                "Grocery", "Building Materials", "Pharmacy", "Electronics",
                "Clothing", "Restaurant"
        );
        return !knownCategories.contains(category);
    }

    /**
     * Blend ML and LLM evaluations with weighted scoring.
     */
    private CreditEvaluation blendEvaluations(
            UUID merchantId,
            MerchantCreditProfile profile,
            CreditClassifier.CreditClassifierResult mlResult,
            BigDecimal mlLimit,
            CreditScoringAiService.CreditEvaluationResponse llmResponse) {

        double mlWeight = hybridConfig.getHybridWeights().getMl();
        double llmWeight = hybridConfig.getHybridWeights().getLlm();

        // Weighted average of scores
        int blendedScore = (int) Math.round(
                mlResult.creditScore() * mlWeight + llmResponse.creditScore() * llmWeight
        );

        // Check for large discrepancies
        int scoreDiff = Math.abs(llmResponse.creditScore() - mlResult.creditScore());
        if (scoreDiff > hybridConfig.getFlagDiscrepancyThreshold()) {
            log.warn("⚠️ Large score discrepancy for merchant {}: ML={}, LLM={}, diff={}",
                    merchantId, mlResult.creditScore(), llmResponse.creditScore(), scoreDiff);
            // TODO: Flag for manual review in separate table
        }

        // Use ML limit if available, otherwise LLM
        BigDecimal blendedLimit = mlLimit != null ? mlLimit :
                BigDecimal.valueOf(llmResponse.recommendedCreditLimit());

        // Build hybrid evaluation
        String reasoning = String.format(
                "HYBRID: ML score=%d (confidence=%.1f%%), LLM score=%d. Weighted result=%d. %s",
                mlResult.creditScore(),
                mlResult.confidence() * 100,
                llmResponse.creditScore(),
                blendedScore,
                llmResponse.reasoning()
        );

        CreditEvaluation.RiskCategory riskCategory = CreditEvaluation.determineRiskCategory(blendedScore);
        String recommendation = CreditEvaluation.determineRecommendation(
                blendedScore,
                profile.creditUtilization().currentCreditLimit(),
                blendedLimit
        );

        CreditEvaluation evaluation = CreditEvaluation.builder()
                .merchantId(merchantId.toString())
                .creditScore(blendedScore)
                .riskCategory(riskCategory)
                .recommendedCreditLimit(blendedLimit)
                .currentCreditLimit(profile.creditUtilization().currentCreditLimit())
                .recommendation(recommendation)
                .reasoning(reasoning)
                .strengthFactors(llmResponse.strengthFactors())
                .riskFactors(llmResponse.riskFactors())
                .recommendations(llmResponse.recommendations())
                .evaluatedAt(LocalDateTime.now())
                .modelVersion("hybrid-v1-ml70-llm30")
                .build();

        // Apply business rules
        return applyBusinessRulesHybrid(merchantId, profile, evaluation);
    }

    /**
     * Build CreditEvaluation from ML-only results.
     */
    private CreditEvaluation buildEvaluationFromMl(
            UUID merchantId,
            MerchantCreditProfile profile,
            CreditClassifier.CreditClassifierResult mlResult,
            BigDecimal mlLimit) {

        String reasoning = String.format(
                "ML-only: Credit score %d (default probability=%.1f%%, confidence=%.1f%%). " +
                "Model: %s",
                mlResult.creditScore(),
                mlResult.defaultProbability() * 100,
                mlResult.confidence() * 100,
                mlResult.modelVersion()
        );

        CreditEvaluation.RiskCategory riskCategory = CreditEvaluation.determineRiskCategory(mlResult.creditScore());
        String recommendation = CreditEvaluation.determineRecommendation(
                mlResult.creditScore(),
                profile.creditUtilization().currentCreditLimit(),
                mlLimit
        );

        CreditEvaluation evaluation = CreditEvaluation.builder()
                .merchantId(merchantId.toString())
                .creditScore(mlResult.creditScore())
                .riskCategory(riskCategory)
                .recommendedCreditLimit(mlLimit)
                .currentCreditLimit(profile.creditUtilization().currentCreditLimit())
                .recommendation(recommendation)
                .reasoning(reasoning)
                .strengthFactors(java.util.List.of()) // TODO: Extract from ML feature importance
                .riskFactors(java.util.List.of())
                .recommendations(java.util.List.of())
                .evaluatedAt(LocalDateTime.now())
                .modelVersion(mlResult.modelVersion())
                .build();

        // Apply business rules
        return applyBusinessRulesHybrid(merchantId, profile, evaluation);
    }

    /**
     * Apply business rules overlay to hybrid/ML evaluation.
     */
    private CreditEvaluation applyBusinessRulesHybrid(
            UUID merchantId,
            MerchantCreditProfile profile,
            CreditEvaluation evaluation) {

        int adjustedScore = evaluation.creditScore();
        BigDecimal adjustedLimit = evaluation.recommendedCreditLimit();
        String adjustedRecommendation = evaluation.recommendation();
        boolean hardOverride = false;

        // Rule 1: 90+ days overdue → automatic severe risk (hard override)
        if (profile.paymentHistory().worstDaysToPay() >= OVERDUE_DAYS_FOR_AUTO_REJECT) {
            log.warn("Merchant {} has {}+ days overdue - applying severe risk override",
                    merchantId, OVERDUE_DAYS_FOR_AUTO_REJECT);
            adjustedScore = Math.min(adjustedScore, 15);
            adjustedRecommendation = "REJECT";
            hardOverride = true;
        }

        // Rule 2: < 30 day tenure → cap at MEDIUM risk
        if (profile.relationshipTenureDays() < MIN_TENURE_DAYS_FOR_AUTO_APPROVE) {
            log.info("Merchant {} has only {} days tenure - capping at MEDIUM risk",
                    merchantId, profile.relationshipTenureDays());
            adjustedScore = Math.min(adjustedScore, 59);
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

        // Recalculate recommendation only if no hard override was applied
        if (!hardOverride) {
            adjustedRecommendation = CreditEvaluation.determineRecommendation(
                    adjustedScore,
                    profile.creditUtilization().currentCreditLimit(),
                    adjustedLimit
            );
        }

        CreditEvaluation.RiskCategory riskCategory = CreditEvaluation.determineRiskCategory(adjustedScore);

        return CreditEvaluation.builder()
                .merchantId(evaluation.merchantId())
                .creditScore(adjustedScore)
                .riskCategory(riskCategory)
                .recommendedCreditLimit(adjustedLimit)
                .currentCreditLimit(evaluation.currentCreditLimit())
                .recommendation(adjustedRecommendation)
                .reasoning(evaluation.reasoning())
                .strengthFactors(evaluation.strengthFactors())
                .riskFactors(evaluation.riskFactors())
                .recommendations(evaluation.recommendations())
                .evaluatedAt(evaluation.evaluatedAt())
                .modelVersion(evaluation.modelVersion())
                .build();
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
        boolean hardOverride = false;

        // Rule 1: 90+ days overdue → automatic severe risk (hard override)
        if (profile.paymentHistory().worstDaysToPay() >= OVERDUE_DAYS_FOR_AUTO_REJECT) {
            log.warn("Merchant {} has {}+ days overdue - applying severe risk override",
                    merchantId, OVERDUE_DAYS_FOR_AUTO_REJECT);
            adjustedScore = Math.min(adjustedScore, 15); // Cap at VERY_HIGH risk
            adjustedRecommendation = "REJECT";
            hardOverride = true;
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

        // Recalculate recommendation only if no hard override was applied
        if (!hardOverride) {
            adjustedRecommendation = CreditEvaluation.determineRecommendation(
                    adjustedScore,
                    profile.creditUtilization().currentCreditLimit(),
                    adjustedLimit
            );
        }

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

            double rawConfidence = (double) evaluation.creditScore() / 100.0;
            double adjustedConfidence = dataMixer.applyConfidenceModifier(
                    rawConfidence, MODEL_NAME, merchant.getDistributor().getId());

            predictionLogger.logPrediction(
                    MODEL_NAME,
                    MODEL_VERSION,
                    EntityType.MERCHANT,
                    merchantId,
                    merchant.getDistributor().getId(),
                    predictionValue,
                    adjustedConfidence,
                    "features_hash_placeholder" // TODO: Implement feature hashing
            );
        } catch (Exception e) {
            log.error("Failed to log prediction for merchant {}: {}", merchantId, e.getMessage());
            // Don't fail the evaluation if logging fails
        }
    }
}
