package com.zuqi.ai.credit;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that uses LLM to generate human-readable explanations for ML credit decisions.
 *
 * Converts ML model outputs (classification results, feature importance scores) into
 * natural language narratives that explain:
 * - Why a merchant was approved/rejected
 * - What were the key factors (top 5 features)
 * - What actions the merchant could take to improve
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md - Phase 3, Task 7
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditMlExplainer {

    private final ChatLanguageModel chatLanguageModel;

    /**
     * Generate a human-readable explanation for an ML credit decision.
     *
     * @param merchantId        Merchant being evaluated
     * @param merchantName      Merchant name for personalization
     * @param mlResult          ML classification result (approval prediction)
     * @param suggestedLimit    ML regression result (credit limit)
     * @param featureImportance Map of feature names → importance scores (0.0-1.0)
     * @return Natural language explanation (2-3 paragraphs)
     */
    public String explainMlDecision(
            UUID merchantId,
            String merchantName,
            CreditClassifier.CreditClassifierResult mlResult,
            BigDecimal suggestedLimit,
            Map<String, Double> featureImportance) {

        log.info("Generating ML explanation for merchant {} (prediction: {}, confidence: {:.2f})",
                merchantId, mlResult.prediction(), mlResult.confidence());

        // Get top 5 most important features
        List<FeatureContribution> topFeatures = getTopFeatures(featureImportance, 5);

        // Build prompt for LLM
        boolean approved = mlResult.prediction().equals("NO_DEFAULT");
        Prompt prompt = buildExplanationPrompt(
                merchantName,
                approved,
                mlResult.confidence(),
                suggestedLimit,
                topFeatures
        );

        // Generate explanation
        try {
            String explanation = chatLanguageModel.generate(prompt.text());
            log.info("Generated explanation: {} chars", explanation.length());
            return explanation;

        } catch (Exception e) {
            log.error("Failed to generate LLM explanation for merchant {}: {}",
                    merchantId, e.getMessage(), e);

            // Fallback: Template-based explanation
            return generateFallbackExplanation(
                    merchantName,
                    approved,
                    mlResult.confidence(),
                    suggestedLimit,
                    topFeatures
            );
        }
    }

    /**
     * Extract top N features by importance score.
     */
    private List<FeatureContribution> getTopFeatures(Map<String, Double> featureImportance, int topN) {
        return featureImportance.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .map(entry -> new FeatureContribution(
                        humanizeFeatureName(entry.getKey()),
                        entry.getValue()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Build LLM prompt for credit decision explanation.
     */
    private Prompt buildExplanationPrompt(
            String merchantName,
            boolean approved,
            double confidence,
            BigDecimal suggestedLimit,
            List<FeatureContribution> topFeatures) {

        String decision = approved ? "APPROVED" : "REJECTED";
        String confidencePercent = String.format("%.0f%%", confidence * 100);

        // Build feature list for prompt
        String featureList = topFeatures.stream()
                .map(f -> String.format("- %s (importance: %.1f%%)", f.name(), f.importance() * 100))
                .collect(Collectors.joining("\n"));

        PromptTemplate template = PromptTemplate.from("""
                You are a credit analyst explaining a machine learning credit decision to a sales representative.

                MERCHANT: {{merchantName}}
                DECISION: {{decision}}
                CONFIDENCE: {{confidence}}
                SUGGESTED CREDIT LIMIT: KES {{suggestedLimit}}

                TOP FACTORS (by importance):
                {{featureList}}

                Task: Write a clear, professional 2-3 paragraph explanation that:
                1. States the decision and confidence level
                2. Explains the top 3 factors that influenced the decision
                3. If REJECTED, suggests what the merchant could improve
                4. If APPROVED, highlights the merchant's strengths

                Tone: Professional but friendly. Use simple language.
                Length: 100-150 words.

                Do NOT include any preamble or meta-commentary. Start directly with the explanation.
                """);

        return template.apply(Map.of(
                "merchantName", merchantName,
                "decision", decision,
                "confidence", confidencePercent,
                "suggestedLimit", suggestedLimit.toString(),
                "featureList", featureList
        ));
    }

    /**
     * Generate fallback explanation when LLM is unavailable.
     */
    private String generateFallbackExplanation(
            String merchantName,
            boolean approved,
            double confidence,
            BigDecimal suggestedLimit,
            List<FeatureContribution> topFeatures) {

        StringBuilder explanation = new StringBuilder();

        if (approved) {
            explanation.append(String.format(
                    "Based on AI analysis, %s has been APPROVED for credit with a suggested limit of KES %s " +
                            "(confidence: %.0f%%). ",
                    merchantName, suggestedLimit, confidence * 100
            ));

            explanation.append("Key strengths include: ");
            explanation.append(topFeatures.stream()
                    .limit(3)
                    .map(FeatureContribution::name)
                    .collect(Collectors.joining(", ")));
            explanation.append(". ");

            explanation.append("The merchant demonstrates strong creditworthiness based on historical patterns " +
                    "and business characteristics.");

        } else {
            explanation.append(String.format(
                    "Based on AI analysis, %s has been FLAGGED for manual review (confidence: %.0f%%). ",
                    merchantName, confidence * 100
            ));

            explanation.append("Primary concerns: ");
            explanation.append(topFeatures.stream()
                    .limit(3)
                    .map(FeatureContribution::name)
                    .collect(Collectors.joining(", ")));
            explanation.append(". ");

            explanation.append("Consider building a stronger order history or improving business stability " +
                    "before reapplying for credit.");
        }

        return explanation.toString();
    }

    /**
     * Convert ML feature names to human-readable labels.
     */
    private String humanizeFeatureName(String featureName) {
        return switch (featureName) {
            // Lag features
            case "qty_1w_ago" -> "Recent order quantity";
            case "qty_2w_ago" -> "Order quantity 2 weeks ago";
            case "qty_3w_ago" -> "Order quantity 3 weeks ago";
            case "qty_4w_ago" -> "Order quantity 4 weeks ago";
            case "rolling_avg_4w" -> "Average order size (4 weeks)";
            case "rolling_avg_12w" -> "Average order size (12 weeks)";
            case "trend_direction_INCREASING" -> "Increasing order trend";
            case "trend_direction_STABLE" -> "Stable order trend";
            case "trend_direction_DECREASING" -> "Decreasing order trend";

            // Temporal features
            case "day_of_week" -> "Day of week pattern";
            case "week_of_month" -> "Week of month pattern";
            case "month_of_year" -> "Seasonal pattern";
            case "is_holiday" -> "Holiday ordering behavior";
            case "is_payday_week" -> "Payday week activity";
            case "is_ramadan" -> "Ramadan period behavior";
            case "is_christmas_season" -> "Holiday season activity";

            // Merchant context
            case "merchant_category_Hardware Store" -> "Hardware store category";
            case "merchant_category_Supermarket" -> "Supermarket category";
            case "merchant_category_Kiosk" -> "Kiosk category";
            case "merchant_category_Restaurant" -> "Restaurant category";
            case "merchant_category_Pharmacy" -> "Pharmacy category";
            case "merchant_category_Mini-Mart" -> "Mini-mart category";
            case "merchant_category_Butchery" -> "Butchery category";
            case "merchant_category_Cyber Cafe" -> "Cyber cafe category";
            case "merchant_category_General Store" -> "General store category";
            case "merchant_category_Boutique" -> "Boutique category";
            case "merchant_size_tier_SMALL" -> "Small business size";
            case "merchant_size_tier_MEDIUM" -> "Medium business size";
            case "merchant_size_tier_LARGE" -> "Large business size";
            case "merchant_credit_status_GOOD" -> "Good credit history";
            case "merchant_credit_status_MODERATE" -> "Moderate credit history";
            case "merchant_credit_status_POOR" -> "Poor credit history";
            case "merchant_tenure_days" -> "Business tenure";

            // SKU context
            case "product_category_Beverages" -> "Beverage ordering";
            case "product_category_Snacks" -> "Snack ordering";
            case "product_category_Household" -> "Household goods ordering";
            case "product_category_Personal Care" -> "Personal care ordering";
            case "product_category_Food Staples" -> "Food staples ordering";
            case "product_category_Dairy" -> "Dairy product ordering";
            case "product_category_Frozen" -> "Frozen goods ordering";
            case "product_category_Health & Medicine" -> "Health product ordering";
            case "product_category_Electronics" -> "Electronics ordering";
            case "product_category_Stationery" -> "Stationery ordering";
            case "price_tier_LOW" -> "Low-price product preference";
            case "price_tier_MEDIUM" -> "Medium-price product preference";
            case "price_tier_HIGH" -> "High-price product preference";
            case "is_promotional" -> "Promotional item ordering";
            case "typical_shelf_life_days" -> "Product shelf life pattern";

            // Order aggregates
            case "total_orders" -> "Total order count";
            case "avg_order_value" -> "Average order value";
            case "order_frequency_days" -> "Order frequency";
            case "last_order_days_ago" -> "Days since last order";

            // Payment behavior
            case "payment_success_rate" -> "Payment success rate";
            case "avg_payment_delay_days" -> "Average payment delay";
            case "has_late_payments" -> "Late payment history";

            // Credit history
            case "current_credit_limit" -> "Current credit limit";
            case "credit_utilization_rate" -> "Credit utilization";
            case "max_overdue_days" -> "Maximum overdue period";

            default -> featureName.replace("_", " ").toLowerCase();
        };
    }

    /**
     * Record class for feature contribution.
     */
    private record FeatureContribution(String name, double importance) {}
}
