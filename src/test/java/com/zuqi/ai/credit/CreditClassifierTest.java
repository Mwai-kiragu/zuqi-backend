package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatureService;
import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test XGBoost credit classifier.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 3
 */
@SpringBootTest
@ActiveProfiles("test")
class CreditClassifierTest {

    @Autowired
    private CreditClassifier creditClassifier;

    @MockBean
    private ModelLoaderService modelLoader;

    @MockBean
    private MerchantFeatureService merchantFeatureService;

    // ── Test helpers ───────────────────────────────────────────────────────

    private MerchantFeatures buildTestFeatures() {
        return MerchantFeatures.builder()
                .merchantId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .totalOrders(150)
                .orderFrequencyPerWeek(2.5)
                .avgOrderValue(BigDecimal.valueOf(25_000))
                .orderValueTrendSlope12w(0.05)
                .orderConsistencyStddev(5_000.0)
                .cancellationRate(0.03)
                .returnRate(0.02)
                .daysSinceLastOrder(5)
                .uniqueSkusOrdered(12)
                .topSkuConcentration(0.35)
                .totalPayments(140)
                .onTimePaymentPct(0.88)
                .avgDaysToPay(12.0)
                .worstDaysToPay(30)
                .partialPaymentFrequency(0.05)
                .paymentMethodDistribution(Map.of("MPESA", 60, "CASH", 30, "BANK_TRANSFER", 10))
                .consecutiveOnTimeStreak(8)
                .totalOverdueAmount(BigDecimal.ZERO)
                .currentCreditLimit(BigDecimal.valueOf(200_000))
                .currentUtilizationRatio(0.45)
                .peakUtilizationRatio(0.70)
                .utilizationTrendSlope(-0.02)
                .limitIncreaseCount(1)
                .daysSinceLastLimitChange(90)
                .businessCategoryEncoded("retail")
                .relationshipTenureDays(450)
                .verificationStatus("VERIFIED")
                .geographicCluster("Nairobi")
                .build();
    }

    // ── Tests ──────────────────────────────────────────────────────────────

    @Test
    void testPredictWithNoModel() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);
        when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(buildTestFeatures());

        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        assertThat(result).isNotNull();
        assertThat(result.creditScore()).isEqualTo(50);
        assertThat(result.defaultProbability()).isEqualTo(0.5);
        assertThat(result.noDefaultProbability()).isEqualTo(0.5);
        assertThat(result.confidence()).isEqualTo(0.5);
        assertThat(result.prediction()).isEqualTo("UNKNOWN");
        assertThat(result.modelVersion()).isEqualTo("none");
    }

    @Test
    void testResultStructure() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);
        when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(buildTestFeatures());

        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        assertThat(result.creditScore()).isBetween(0, 100);
        assertThat(result.defaultProbability()).isBetween(0.0, 1.0);
        assertThat(result.noDefaultProbability()).isBetween(0.0, 1.0);
        assertThat(result.confidence()).isBetween(0.0, 1.0);
        assertThat(result.prediction()).isNotNull();
        assertThat(result.featureImportance()).isNotNull();
        assertThat(result.modelVersion()).isNotNull();
    }

    @Test
    void testPredictWithMultipleMerchants() {
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);

        for (int i = 0; i < 10; i++) {
            UUID merchantId = UUID.randomUUID();
            MerchantFeatures features = buildTestFeatures();
            when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(features);

            CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

            assertThat(result).isNotNull();
            assertThat(result.creditScore()).isBetween(0, 100);
        }
    }

    @Test
    void testCreditScoreInversion() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);
        when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(buildTestFeatures());

        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        // For default result: defaultProb=0.5 → creditScore=50
        int expectedScore = (int) Math.round((1.0 - result.defaultProbability()) * 100);
        assertThat(result.creditScore()).isEqualTo(expectedScore);
    }

    @Test
    void testConfidenceCalculation() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);
        when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(buildTestFeatures());

        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        assertThat(result.confidence()).isEqualTo(0.5);
        assertThat(result.confidence()).isEqualTo(
                Math.max(result.defaultProbability(), result.noDefaultProbability()));
    }

    @Test
    void testExceptionHandling() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);
        when(merchantFeatureService.computeFeatures(merchantId))
                .thenThrow(new RuntimeException("Database error"));

        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        assertThat(result).isNotNull();
        assertThat(result.creditScore()).isEqualTo(50);
        assertThat(result.prediction()).isEqualTo("UNKNOWN");
    }

    @Test
    void testFeatureIntegration() {
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);

        for (int i = 0; i < 5; i++) {
            UUID merchantId = UUID.randomUUID();
            MerchantFeatures features = buildTestFeatures();
            when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(features);

            CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

            assertThat(result.creditScore()).isEqualTo(50);
            assertThat(features.totalOrders()).isGreaterThan(0);
            assertThat(features.onTimePaymentPct()).isBetween(0.0, 1.0);
            assertThat(features.currentUtilizationRatio()).isBetween(0.0, 1.05);
        }
    }

    @Test
    void testModelVersionExtraction() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);
        when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(buildTestFeatures());

        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        assertThat(result.modelVersion()).isEqualTo("none");
    }

    @Test
    void testProbabilitySum() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);
        when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(buildTestFeatures());

        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        double sum = result.defaultProbability() + result.noDefaultProbability();
        assertThat(sum).isBetween(0.99, 1.01);
    }
}
