package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatures;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual test for credit scoring with actual Ollama connection.
 *
 * REQUIRES:
 * - Ollama server running at http://192.168.2.17:11434
 * - qwen2.5:32b model pulled and ready
 *
 * Run this test manually when Ollama is available to verify end-to-end flow.
 *
 * To run: ./mvnw test -Dtest=CreditScoringManualTest
 */
@SpringBootTest
@ActiveProfiles("test")
// @Disabled("Manual test - requires Ollama server running")
class CreditScoringManualTest {

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Test
    void testOllamaConnection() {
        // Simple connectivity test
        String response = chatLanguageModel.generate("Say 'Ollama connected' in exactly those words.");

        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        System.out.println("Ollama response: " + response);
    }

    @Test
    void testCreditScoringPrompt() {
        // Build a sample merchant profile
        MerchantCreditProfile sampleProfile = buildSampleMerchantProfile();

        String peerContext = "No comparable merchants found in database.";

        // Create AI service manually
        CreditScoringAiService creditScoringService = dev.langchain4j.service.AiServices.create(
                CreditScoringAiService.class,
                chatLanguageModel
        );

        // Execute evaluation
        long startTime = System.currentTimeMillis();
        CreditScoringAiService.CreditEvaluationResponse response = creditScoringService.evaluate(sampleProfile, peerContext);
        long duration = System.currentTimeMillis() - startTime;

        // Verify response
        assertThat(response).isNotNull();
        assertThat(response.creditScore()).isBetween(0, 100);
        assertThat(response.recommendedCreditLimit()).isGreaterThan(0);
        assertThat(response.recommendation()).isIn("APPROVE", "INCREASE", "DECREASE", "REJECT", "MAINTAIN");
        assertThat(response.reasoning()).isNotBlank();
        assertThat(response.strengthFactors()).isNotEmpty();
        assertThat(response.riskFactors()).isNotEmpty();

        // Print results
        System.out.println("=== Credit Evaluation Results ===");
        System.out.println("Credit Score: " + response.creditScore());
        System.out.println("Recommended Limit: KES " + String.format("%,.2f", response.recommendedCreditLimit()));
        System.out.println("Recommendation: " + response.recommendation());
        System.out.println("Reasoning: " + response.reasoning());
        System.out.println("\nStrength Factors:");
        response.strengthFactors().forEach(factor -> System.out.println("  + " + factor));
        System.out.println("\nRisk Factors:");
        response.riskFactors().forEach(factor -> System.out.println("  - " + factor));
        System.out.println("\nRecommendations:");
        response.recommendations().forEach(rec -> System.out.println("  → " + rec));
        System.out.println("\nEvaluation took: " + duration + "ms");
    }

    /**
     * Build a sample merchant profile for testing.
     */
    private MerchantCreditProfile buildSampleMerchantProfile() {
        // Create realistic merchant features
        MerchantFeatures features = MerchantFeatures.builder()
                .merchantId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                // Order features
                .totalOrders(45)
                .orderFrequencyPerWeek(2.5)
                .avgOrderValue(BigDecimal.valueOf(35000))
                .orderValueTrendSlope12w(0.15) // Growing
                .orderConsistencyStddev(5000.0)
                .cancellationRate(0.02)
                .returnRate(0.01)
                .daysSinceLastOrder(3)
                .uniqueSkusOrdered(25)
                .topSkuConcentration(0.30)
                // Payment features
                .totalPayments(42)
                .onTimePaymentPct(0.93)
                .avgDaysToPay(12.5)
                .worstDaysToPay(28)
                .partialPaymentFrequency(0.05)
                .paymentMethodDistribution(Map.of("MPESA", 35, "CASH", 7))
                .consecutiveOnTimeStreak(8)
                .totalOverdueAmount(BigDecimal.ZERO)
                // Credit features
                .currentCreditLimit(BigDecimal.valueOf(150000))
                .currentUtilizationRatio(0.45)
                .peakUtilizationRatio(0.75)
                .utilizationTrendSlope(-0.05) // Decreasing (good)
                .limitIncreaseCount(1)
                .daysSinceLastLimitChange(45)
                // Profile features
                .businessCategoryEncoded("Hardware Store")
                .relationshipTenureDays(120)
                .verificationStatus("VERIFIED")
                .geographicCluster("Nairobi")
                .build();

        return MerchantCreditProfile.fromFeatures(features, "ABC Hardware Ltd", "LOW");
    }
}
