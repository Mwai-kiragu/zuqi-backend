package com.zuqi.ai.credit;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CreditMlExplainer.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md - Phase 3, Task 7
 */
@ExtendWith(MockitoExtension.class)
class CreditMlExplainerTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @InjectMocks
    private CreditMlExplainer explainer;

    @Test
    void testExplainApprovalDecision() {
        // Given: ML approved merchant with high confidence
        UUID merchantId = UUID.randomUUID();
        String merchantName = "Test Hardware Store";

        CreditClassifier.CreditClassifierResult mlResult =
                CreditClassifier.CreditClassifierResult.builder()
                        .creditScore(85)
                        .defaultProbability(0.15)
                        .noDefaultProbability(0.85)
                        .confidence(0.85)
                        .prediction("NO_DEFAULT")
                        .featureImportance(createSampleFeatureImportance())
                        .modelVersion("credit_classifier-v1")
                        .build();

        BigDecimal suggestedLimit = BigDecimal.valueOf(350000);

        // Mock LLM response
        when(chatLanguageModel.generate(anyString()))
                .thenReturn("Based on AI analysis, Test Hardware Store has been APPROVED for credit " +
                        "with a suggested limit of KES 350,000 (confidence: 85%). Key strengths include: " +
                        "stable order history, good payment behavior, and strong business tenure.");

        // When: Generate explanation
        String explanation = explainer.explainMlDecision(
                merchantId,
                merchantName,
                mlResult,
                suggestedLimit,
                mlResult.featureImportance()
        );

        // Then: Explanation should be informative
        assertThat(explanation)
                .as("Explanation should be generated")
                .isNotBlank()
                .contains("APPROVED")
                .contains("350,000")
                .contains("85%");
    }

    @Test
    void testExplainRejectionDecision() {
        // Given: ML rejected merchant
        UUID merchantId = UUID.randomUUID();
        String merchantName = "New Kiosk";

        CreditClassifier.CreditClassifierResult mlResult =
                CreditClassifier.CreditClassifierResult.builder()
                        .creditScore(35)
                        .defaultProbability(0.75)
                        .noDefaultProbability(0.25)
                        .confidence(0.75)
                        .prediction("DEFAULT")
                        .featureImportance(createSampleFeatureImportance())
                        .modelVersion("credit_classifier-v1")
                        .build();

        BigDecimal suggestedLimit = BigDecimal.valueOf(50000);

        // Mock LLM response
        when(chatLanguageModel.generate(anyString()))
                .thenReturn("Based on AI analysis, New Kiosk has been FLAGGED for manual review (confidence: 75%). " +
                        "Primary concerns: limited order history, inconsistent payment behavior. " +
                        "Consider building a stronger track record before reapplying.");

        // When: Generate explanation
        String explanation = explainer.explainMlDecision(
                merchantId,
                merchantName,
                mlResult,
                suggestedLimit,
                mlResult.featureImportance()
        );

        // Then: Explanation should highlight concerns
        assertThat(explanation)
                .as("Explanation should highlight concerns")
                .isNotBlank()
                .containsAnyOf("FLAGGED", "review", "concerns");
    }

    @Test
    void testFallbackExplanationWhenLlmFails() {
        // Given: ML result
        UUID merchantId = UUID.randomUUID();
        String merchantName = "Test Merchant";

        CreditClassifier.CreditClassifierResult mlResult =
                CreditClassifier.CreditClassifierResult.builder()
                        .creditScore(75)
                        .defaultProbability(0.25)
                        .noDefaultProbability(0.75)
                        .confidence(0.75)
                        .prediction("NO_DEFAULT")
                        .featureImportance(createSampleFeatureImportance())
                        .modelVersion("credit_classifier-v1")
                        .build();

        BigDecimal suggestedLimit = BigDecimal.valueOf(250000);

        // Mock LLM failure
        when(chatLanguageModel.generate(anyString()))
                .thenThrow(new RuntimeException("LLM service unavailable"));

        // When: Generate explanation (should fall back to template)
        String explanation = explainer.explainMlDecision(
                merchantId,
                merchantName,
                mlResult,
                suggestedLimit,
                mlResult.featureImportance()
        );

        // Then: Fallback explanation should still be informative
        assertThat(explanation)
                .as("Fallback explanation should work")
                .isNotBlank()
                .contains("Test Merchant")
                .contains("APPROVED");
    }

    /**
     * Helper: Create sample feature importance map.
     */
    private Map<String, Double> createSampleFeatureImportance() {
        Map<String, Double> importance = new HashMap<>();
        importance.put("rolling_avg_4w", 0.25);
        importance.put("payment_success_rate", 0.20);
        importance.put("merchant_tenure_days", 0.15);
        importance.put("total_orders", 0.12);
        importance.put("avg_order_value", 0.10);
        importance.put("order_frequency_days", 0.08);
        importance.put("has_late_payments", 0.06);
        importance.put("credit_utilization_rate", 0.04);
        return importance;
    }
}
