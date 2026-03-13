package com.zuqi.ai.credit;

import com.zuqi.ai.config.HybridScoringConfig;
import com.zuqi.ai.monitoring.LlmMetricsService;
import com.zuqi.ai.monitoring.PredictionLogger;
import com.zuqi.ai.synthetic.DataMixer;
import com.zuqi.domain.ai.EntityType;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.MerchantRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreditScoringOrchestrator.
 *
 * Focuses on:
 * - LLM failure propagation (circuit-breaker fallback contract)
 * - Graceful degradation in HYBRID mode when LLM is not required
 * - Confidence modifier delegated to DataMixer
 * - Business rules applied after evaluation
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.5
 */
@ExtendWith(MockitoExtension.class)
class CreditScoringOrchestratorTest {

    @Mock private CreditFeatureBuilder featureBuilder;
    @Mock private ChatLanguageModel chatLanguageModel;
    @Mock private PredictionLogger predictionLogger;
    @Mock private MerchantRepository merchantRepository;
    @Mock private LlmMetricsService llmMetricsService;
    @Mock private CreditClassifier creditClassifier;
    @Mock private CreditLimitRegressor creditLimitRegressor;
    @Mock private HybridScoringConfig hybridConfig;
    @Mock private DataMixer dataMixer;

    @InjectMocks
    private CreditScoringOrchestrator orchestrator;

    private static final UUID MERCHANT_ID    = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID DISTRIBUTOR_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private Merchant merchant;
    private MerchantCreditProfile profile;

    @BeforeEach
    void setUp() {
        Distributor distributor = new Distributor();
        distributor.setId(DISTRIBUTOR_ID);

        merchant = new Merchant();
        merchant.setId(MERCHANT_ID);
        merchant.setDistributor(distributor);

        profile = buildProfile(365, 80.0, 5.0, BigDecimal.valueOf(150_000));

        when(hybridConfig.getMode()).thenReturn(HybridScoringConfig.ScoringMode.LLM_ONLY);
        // lenient: these are only reached when evaluation succeeds (not in LLM-failure tests)
        lenient().when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
        lenient().when(dataMixer.applyConfidenceModifier(anyDouble(), anyString(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // Circuit-breaker fallback contract: LLM failure → RuntimeException
    // -------------------------------------------------------------------------

    @Test
    void evaluateMerchant_llmMode_whenLlmThrows_wrapsInRuntimeException() throws Exception {
        when(featureBuilder.buildLlmProfile(MERCHANT_ID)).thenReturn(profile);
        when(featureBuilder.buildPeerContext(MERCHANT_ID)).thenReturn("No peers.");
        when(llmMetricsService.recordOperation(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused — Ollama unreachable"));

        assertThatThrownBy(() -> orchestrator.evaluateMerchant(MERCHANT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Credit evaluation failed");
    }

    @Test
    void evaluateMerchant_llmMode_whenLlmThrowsTimeoutException_wrapsInRuntimeException() throws Exception {
        when(featureBuilder.buildLlmProfile(MERCHANT_ID)).thenReturn(profile);
        when(featureBuilder.buildPeerContext(MERCHANT_ID)).thenReturn("No peers.");
        when(llmMetricsService.recordOperation(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Read timed out after 30000ms"));

        assertThatThrownBy(() -> orchestrator.evaluateMerchant(MERCHANT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Credit evaluation failed");
    }

    // -------------------------------------------------------------------------
    // Graceful degradation: HYBRID mode, ML-only path (no LLM needed)
    // -------------------------------------------------------------------------

    @Test
    void evaluateMerchant_hybridMode_whenLlmNotRequired_succeedsUsingMlOnly() throws Exception { // NOSONAR — verify() on recordOperation(Callable) requires this
        when(hybridConfig.getMode()).thenReturn(HybridScoringConfig.ScoringMode.HYBRID);
        when(hybridConfig.getLlmValidationTriggers()).thenReturn(triggersWithHighThreshold());

        CreditClassifier.CreditClassifierResult mlResult = new CreditClassifier.CreditClassifierResult(
                72, 0.28, 0.72, 0.72, "NO_DEFAULT", java.util.Map.of(), "credit_classifier-v1");
        when(creditClassifier.predict(MERCHANT_ID)).thenReturn(mlResult);
        when(creditLimitRegressor.predictCreditLimit(MERCHANT_ID)).thenReturn(BigDecimal.valueOf(200_000));
        when(featureBuilder.buildLlmProfile(MERCHANT_ID)).thenReturn(profile);

        CreditEvaluation result = orchestrator.evaluateMerchant(MERCHANT_ID);

        // Result is present and LLM was never called
        assertThat(result).isNotNull();
        assertThat(result.creditScore()).isGreaterThan(0);
        verifyNoInteractions(chatLanguageModel);
        verify(llmMetricsService, never()).recordOperation(any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Confidence modifier is applied via DataMixer before logging
    // -------------------------------------------------------------------------

    @Test
    void evaluateMerchant_llmMode_delegatesConfidenceToDataMixer() throws Exception {
        when(featureBuilder.buildLlmProfile(MERCHANT_ID)).thenReturn(profile);
        when(featureBuilder.buildPeerContext(MERCHANT_ID)).thenReturn("No peers.");

        CreditScoringAiService.CreditEvaluationResponse llmResp = new CreditScoringAiService.CreditEvaluationResponse(
                78, 250_000.0, "APPROVE", "Strong history",
                java.util.List.of("on-time"), java.util.List.of(), java.util.List.of());
        when(llmMetricsService.recordOperation(any(), any(), any(), any())).thenReturn(llmResp);

        orchestrator.evaluateMerchant(MERCHANT_ID);

        // DataMixer.applyConfidenceModifier must be invoked with the model name and distributor id
        verify(dataMixer).applyConfidenceModifier(anyDouble(), eq("credit_scoring"), eq(DISTRIBUTOR_ID));
    }

    // -------------------------------------------------------------------------
    // ML_ONLY mode — succeeds without any LLM interaction
    // -------------------------------------------------------------------------

    @Test
    void evaluateMerchant_mlMode_neverInvokesLlm() throws Exception {
        when(hybridConfig.getMode()).thenReturn(HybridScoringConfig.ScoringMode.ML_ONLY);

        CreditClassifier.CreditClassifierResult mlResult = new CreditClassifier.CreditClassifierResult(
                65, 0.35, 0.65, 0.65, "NO_DEFAULT", java.util.Map.of(), "credit_classifier-v1");
        when(creditClassifier.predict(MERCHANT_ID)).thenReturn(mlResult);
        when(creditLimitRegressor.predictCreditLimit(MERCHANT_ID)).thenReturn(BigDecimal.valueOf(180_000));
        when(featureBuilder.buildLlmProfile(MERCHANT_ID)).thenReturn(profile);

        CreditEvaluation result = orchestrator.evaluateMerchant(MERCHANT_ID);

        assertThat(result).isNotNull();
        verifyNoInteractions(chatLanguageModel);
        verify(llmMetricsService, never()).recordOperation(any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Business rules applied correctly
    // -------------------------------------------------------------------------

    @Test
    void evaluateMerchant_llmMode_whenTenureLessThan30Days_capsScoreAtMediumRisk() throws Exception {
        MerchantCreditProfile newMerchantProfile = buildProfile(15, 90.0, 2.0, BigDecimal.ZERO);
        when(featureBuilder.buildLlmProfile(MERCHANT_ID)).thenReturn(newMerchantProfile);
        when(featureBuilder.buildPeerContext(MERCHANT_ID)).thenReturn("No peers.");

        // LLM returns a very high score for a new merchant
        CreditScoringAiService.CreditEvaluationResponse llmResp = new CreditScoringAiService.CreditEvaluationResponse(
                95, 1_000_000.0, "APPROVE", "Excellent history",
                java.util.List.of(), java.util.List.of(), java.util.List.of());
        when(llmMetricsService.recordOperation(any(), any(), any(), any())).thenReturn(llmResp);

        CreditEvaluation result = orchestrator.evaluateMerchant(MERCHANT_ID);

        // Business rule: tenure < 30 days → score capped at 59 (MEDIUM risk ceiling)
        assertThat(result.creditScore()).isLessThanOrEqualTo(59);
    }

    @Test
    void evaluateMerchant_llmMode_whenMerchantHas90DaysOverdue_forcesReject() throws Exception {
        MerchantCreditProfile overdueProfile = buildProfile(400, 40.0, 5.0, BigDecimal.valueOf(100_000),
                120 /* worstDaysToPay */);
        when(featureBuilder.buildLlmProfile(MERCHANT_ID)).thenReturn(overdueProfile);
        when(featureBuilder.buildPeerContext(MERCHANT_ID)).thenReturn("No peers.");

        CreditScoringAiService.CreditEvaluationResponse llmResp = new CreditScoringAiService.CreditEvaluationResponse(
                50, 100_000.0, "MAINTAIN", "Borderline",
                java.util.List.of(), java.util.List.of(), java.util.List.of());
        when(llmMetricsService.recordOperation(any(), any(), any(), any())).thenReturn(llmResp);

        CreditEvaluation result = orchestrator.evaluateMerchant(MERCHANT_ID);

        assertThat(result.creditScore()).isLessThanOrEqualTo(15);
        assertThat(result.recommendation()).isEqualTo("REJECT");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MerchantCreditProfile buildProfile(int tenureDays, double onTimePct,
                                                double orderFreqPerWeek, BigDecimal currentLimit) {
        return buildProfile(tenureDays, onTimePct, orderFreqPerWeek, currentLimit, 10);
    }

    private MerchantCreditProfile buildProfile(int tenureDays, double onTimePct,
                                                double orderFreqPerWeek, BigDecimal currentLimit,
                                                int worstDaysToPay) {
        return MerchantCreditProfile.builder()
                .merchantId(MERCHANT_ID.toString())
                .businessName("Kamau General Store")
                .businessCategory("General Store")
                .relationshipTenureDays(tenureDays)
                .orderBehavior(MerchantCreditProfile.OrderBehavior.builder()
                        .totalOrders(52)
                        .orderFrequencyPerWeek(orderFreqPerWeek)
                        .avgOrderValue(BigDecimal.valueOf(5_000))
                        .orderTrend("GROWING")
                        .orderConsistencyScore(8.0)
                        .daysSinceLastOrder(3)
                        .uniqueSkusOrdered(12)
                        .productDiversification(0.6)
                        .build())
                .paymentHistory(MerchantCreditProfile.PaymentHistory.builder()
                        .totalPayments(50)
                        .onTimePaymentPct(onTimePct)
                        .avgDaysToPay(5.0)
                        .worstDaysToPay(worstDaysToPay)
                        .consecutiveOnTimeStreak(12)
                        .totalOverdueAmount(BigDecimal.ZERO)
                        .preferredPaymentMethod("MPESA")
                        .build())
                .creditUtilization(MerchantCreditProfile.CreditUtilization.builder()
                        .currentCreditLimit(currentLimit)
                        .currentUtilizationPct(40.0)
                        .peakUtilizationPct(70.0)
                        .utilizationTrend("STABLE")
                        .limitIncreaseCount(1)
                        .daysSinceLastLimitChange(90)
                        .build())
                .riskIndicators(MerchantCreditProfile.RiskIndicators.builder()
                        .cancellationRate(0.02)
                        .returnRate(0.01)
                        .partialPaymentFrequency(0.05)
                        .hasOverdueBalance(false)
                        .verificationStatus("VERIFIED")
                        .geographicRisk("LOW")
                        .build())
                .build();
    }

    /**
     * Returns trigger config where LLM validation is only triggered for limits
     * well above the ML-predicted KES 200k — so the test ML-only path stays ML-only.
     */
    private HybridScoringConfig.LlmValidationTriggers triggersWithHighThreshold() {
        HybridScoringConfig.LlmValidationTriggers t = new HybridScoringConfig.LlmValidationTriggers();
        t.setHighValueLimit(BigDecimal.valueOf(10_000_000)); // way above predicted limit
        t.setLowMlConfidence(0.0);                          // always "confident"
        t.setNewCategoryValidation(false);
        t.setSampleRate(0.0);                               // no random sampling
        return t;
    }
}
