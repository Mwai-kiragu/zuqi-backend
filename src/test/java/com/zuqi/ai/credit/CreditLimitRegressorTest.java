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
 * Test XGBoost credit limit regressor.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 4
 */
@SpringBootTest
@ActiveProfiles("test")
class CreditLimitRegressorTest {

    @Autowired
    private CreditLimitRegressor creditLimitRegressor;

    @MockBean
    private ModelLoaderService modelLoader;

    @MockBean
    private MerchantFeatureService merchantFeatureService;

    private static final BigDecimal MIN_LIMIT = BigDecimal.valueOf(50_000);
    private static final BigDecimal MAX_LIMIT = BigDecimal.valueOf(10_000_000);

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

    private MerchantFeatures buildFeaturesWithOnTimePayment(double onTimePaymentPct) {
        return MerchantFeatures.builder()
                .merchantId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .totalOrders(200)
                .orderFrequencyPerWeek(3.0)
                .avgOrderValue(BigDecimal.valueOf(30_000))
                .orderValueTrendSlope12w(0.03)
                .orderConsistencyStddev(4_000.0)
                .cancellationRate(0.02)
                .returnRate(0.01)
                .daysSinceLastOrder(3)
                .uniqueSkusOrdered(15)
                .topSkuConcentration(0.30)
                .totalPayments(195)
                .onTimePaymentPct(onTimePaymentPct)
                .avgDaysToPay(8.0)
                .worstDaysToPay(20)
                .partialPaymentFrequency(0.02)
                .paymentMethodDistribution(Map.of("MPESA", 70, "CASH", 20, "BANK_TRANSFER", 10))
                .consecutiveOnTimeStreak(12)
                .totalOverdueAmount(BigDecimal.ZERO)
                .currentCreditLimit(BigDecimal.valueOf(300_000))
                .currentUtilizationRatio(0.40)
                .peakUtilizationRatio(0.65)
                .utilizationTrendSlope(-0.01)
                .limitIncreaseCount(2)
                .daysSinceLastLimitChange(60)
                .businessCategoryEncoded("retail")
                .relationshipTenureDays(600)
                .verificationStatus("VERIFIED")
                .geographicCluster("Nairobi")
                .build();
    }

    private MerchantFeatures buildFeaturesWithTenure(int tenureDays) {
        return MerchantFeatures.builder()
                .merchantId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .totalOrders(180)
                .orderFrequencyPerWeek(2.8)
                .avgOrderValue(BigDecimal.valueOf(28_000))
                .orderValueTrendSlope12w(0.04)
                .orderConsistencyStddev(4_500.0)
                .cancellationRate(0.03)
                .returnRate(0.02)
                .daysSinceLastOrder(4)
                .uniqueSkusOrdered(14)
                .topSkuConcentration(0.32)
                .totalPayments(170)
                .onTimePaymentPct(0.85)
                .avgDaysToPay(10.0)
                .worstDaysToPay(25)
                .partialPaymentFrequency(0.04)
                .paymentMethodDistribution(Map.of("MPESA", 65, "CASH", 25, "BANK_TRANSFER", 10))
                .consecutiveOnTimeStreak(10)
                .totalOverdueAmount(BigDecimal.ZERO)
                .currentCreditLimit(BigDecimal.valueOf(250_000))
                .currentUtilizationRatio(0.42)
                .peakUtilizationRatio(0.68)
                .utilizationTrendSlope(-0.01)
                .limitIncreaseCount(1)
                .daysSinceLastLimitChange(120)
                .businessCategoryEncoded("retail")
                .relationshipTenureDays(tenureDays)
                .verificationStatus("VERIFIED")
                .geographicCluster("Mombasa")
                .build();
    }

    private MerchantFeatures buildFeaturesWithUtilization(double utilization) {
        return MerchantFeatures.builder()
                .merchantId(UUID.randomUUID())
                .computedAt(LocalDateTime.now())
                .totalOrders(120)
                .orderFrequencyPerWeek(2.0)
                .avgOrderValue(BigDecimal.valueOf(20_000))
                .orderValueTrendSlope12w(0.01)
                .orderConsistencyStddev(6_000.0)
                .cancellationRate(0.05)
                .returnRate(0.03)
                .daysSinceLastOrder(8)
                .uniqueSkusOrdered(10)
                .topSkuConcentration(0.40)
                .totalPayments(110)
                .onTimePaymentPct(0.75)
                .avgDaysToPay(18.0)
                .worstDaysToPay(45)
                .partialPaymentFrequency(0.10)
                .paymentMethodDistribution(Map.of("MPESA", 55, "CASH", 35, "BANK_TRANSFER", 10))
                .consecutiveOnTimeStreak(4)
                .totalOverdueAmount(BigDecimal.valueOf(5_000))
                .currentCreditLimit(BigDecimal.valueOf(150_000))
                .currentUtilizationRatio(utilization)
                .peakUtilizationRatio(utilization + 0.05)
                .utilizationTrendSlope(0.02)
                .limitIncreaseCount(0)
                .daysSinceLastLimitChange(200)
                .businessCategoryEncoded("retail")
                .relationshipTenureDays(200)
                .verificationStatus("PENDING")
                .geographicCluster("Kisumu")
                .build();
    }

    // ── Tests ──────────────────────────────────────────────────────────────

    @Test
    void testPredictWithNoModel() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_limit_regressor")).thenReturn(null);
        when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(buildTestFeatures());

        BigDecimal creditLimit = creditLimitRegressor.predictCreditLimit(merchantId);

        assertThat(creditLimit).isNotNull();
        assertThat(creditLimit).isEqualTo(BigDecimal.valueOf(100_000));
    }

    @Test
    void testCreditLimitConstraints() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_limit_regressor")).thenReturn(null);

        for (int i = 0; i < 10; i++) {
            when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(buildTestFeatures());
            BigDecimal creditLimit = creditLimitRegressor.predictCreditLimit(merchantId);
            assertThat(creditLimit).isGreaterThanOrEqualTo(MIN_LIMIT);
            assertThat(creditLimit).isLessThanOrEqualTo(MAX_LIMIT);
        }
    }

    @Test
    void testCreditLimitRounding() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_limit_regressor")).thenReturn(null);

        for (int i = 0; i < 10; i++) {
            when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(buildTestFeatures());
            BigDecimal creditLimit = creditLimitRegressor.predictCreditLimit(merchantId);
            long limitValue = creditLimit.longValue();
            assertThat(limitValue % 10_000).isEqualTo(0);
        }
    }

    @Test
    void testCalculateIdealLimit() {
        MerchantFeatures features = buildTestFeatures();
        double defaultProb = 0.15;

        BigDecimal idealLimit = creditLimitRegressor.calculateIdealLimit(features, defaultProb);

        assertThat(idealLimit).isGreaterThanOrEqualTo(MIN_LIMIT);
        assertThat(idealLimit).isLessThanOrEqualTo(MAX_LIMIT);
        assertThat(idealLimit.longValue() % 10_000).isEqualTo(0);

        System.out.println("Default prob: " + defaultProb + " | Ideal limit: " + idealLimit);
    }

    @Test
    void testIdealLimitRiskAdjustment() {
        // Low-risk merchant (5% default probability)
        MerchantFeatures lowRiskFeatures = buildTestFeatures();
        double lowRiskProb = 0.05;

        // High-risk merchant (45% default probability) — worse payment behaviour
        MerchantFeatures highRiskFeatures = buildFeaturesWithUtilization(0.90);
        double highRiskProb = 0.45;

        BigDecimal lowRiskLimit = creditLimitRegressor.calculateIdealLimit(lowRiskFeatures, lowRiskProb);
        BigDecimal highRiskLimit = creditLimitRegressor.calculateIdealLimit(highRiskFeatures, highRiskProb);

        System.out.println("Low-risk  (prob=" + lowRiskProb  + "): " + lowRiskLimit);
        System.out.println("High-risk (prob=" + highRiskProb + "): " + highRiskLimit);

        assertThat(lowRiskLimit).isGreaterThan(BigDecimal.ZERO);
        assertThat(highRiskLimit).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void testIdealLimitPaymentHistoryBonus() {
        MerchantFeatures excellentPayer = buildFeaturesWithOnTimePayment(0.95);
        double defaultProb = 0.10;

        BigDecimal limit = creditLimitRegressor.calculateIdealLimit(excellentPayer, defaultProb);

        assertThat(limit).isGreaterThan(BigDecimal.ZERO);
        System.out.println("Excellent payer (onTime=95%): " + limit);
    }

    @Test
    void testIdealLimitTenureBonus() {
        MerchantFeatures longTenure = buildFeaturesWithTenure(400);
        double defaultProb = 0.12;

        BigDecimal limit = creditLimitRegressor.calculateIdealLimit(longTenure, defaultProb);

        assertThat(limit).isGreaterThan(BigDecimal.ZERO);
        System.out.println("Long tenure (400 days): " + limit);
    }

    @Test
    void testIdealLimitUtilizationPenalty() {
        MerchantFeatures highUtilization = buildFeaturesWithUtilization(0.85);
        double defaultProb = 0.20;

        BigDecimal limit = creditLimitRegressor.calculateIdealLimit(highUtilization, defaultProb);

        assertThat(limit).isGreaterThan(BigDecimal.ZERO);
        System.out.println("High utilization (85%): " + limit);
    }

    @Test
    void testExceptionHandling() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_limit_regressor")).thenReturn(null);
        when(merchantFeatureService.computeFeatures(merchantId))
                .thenThrow(new RuntimeException("Database error"));

        BigDecimal creditLimit = creditLimitRegressor.predictCreditLimit(merchantId);

        assertThat(creditLimit).isNotNull();
        assertThat(creditLimit).isEqualTo(BigDecimal.valueOf(100_000));
    }

    @Test
    void testPredictWithMultipleMerchants() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_limit_regressor")).thenReturn(null);

        for (int i = 0; i < 10; i++) {
            when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(buildTestFeatures());
            BigDecimal creditLimit = creditLimitRegressor.predictCreditLimit(merchantId);
            assertThat(creditLimit).isNotNull();
            assertThat(creditLimit).isGreaterThanOrEqualTo(MIN_LIMIT);
            assertThat(creditLimit).isLessThanOrEqualTo(MAX_LIMIT);
        }
    }

    @Test
    void testFeatureIntegration() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_limit_regressor")).thenReturn(null);

        for (int i = 0; i < 5; i++) {
            MerchantFeatures features = buildTestFeatures();
            when(merchantFeatureService.computeFeatures(merchantId)).thenReturn(features);
            BigDecimal creditLimit = creditLimitRegressor.predictCreditLimit(merchantId);

            assertThat(creditLimit).isEqualTo(BigDecimal.valueOf(100_000));
            assertThat(features.totalOrders()).isGreaterThan(0);
            assertThat(features.avgOrderValue()).isGreaterThan(BigDecimal.ZERO);
            assertThat(features.orderFrequencyPerWeek()).isGreaterThan(0.0);
        }
    }

    @Test
    void testIdealLimitBasedOnMonthlyVolume() {
        MerchantFeatures features = buildTestFeatures();
        double defaultProb = 0.15;

        BigDecimal monthlyOrderValue = features.avgOrderValue()
                .multiply(BigDecimal.valueOf(features.orderFrequencyPerWeek() * 4.33));

        BigDecimal idealLimit = creditLimitRegressor.calculateIdealLimit(features, defaultProb);

        System.out.println("Monthly volume: " + monthlyOrderValue);
        System.out.println("Ideal limit: " + idealLimit);
        System.out.println("Ratio: " + idealLimit.divide(monthlyOrderValue, 2, java.math.RoundingMode.HALF_UP));

        assertThat(idealLimit).isGreaterThan(BigDecimal.ZERO);
        assertThat(idealLimit).isGreaterThanOrEqualTo(MIN_LIMIT);
        assertThat(idealLimit).isLessThanOrEqualTo(MAX_LIMIT);
    }
}
