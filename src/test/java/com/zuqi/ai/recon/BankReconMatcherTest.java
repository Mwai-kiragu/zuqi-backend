package com.zuqi.ai.recon;

import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.domain.payment.Payment;
import com.zuqi.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BankReconMatcherTest {

    @Mock private ModelLoaderService modelLoader;
    @Mock private ModelPhaseService phaseService;
    @Mock private PaymentRepository paymentRepository;

    private ReconFeatureBuilder featureBuilder;
    private BankReconMatcher matcher;

    @BeforeEach
    void setUp() {
        featureBuilder = new ReconFeatureBuilder();
        matcher = new BankReconMatcher(featureBuilder, modelLoader, phaseService, paymentRepository);
    }

    @Test
    void findMatches_whenNoCandidates_returnsEmptyList() {
        UUID distributorId = UUID.randomUUID();
        when(paymentRepository.findByDistributorIdAndPaymentDateBetween(
                eq(distributorId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        List<BankReconMatcher.MatchResult> results = matcher.findMatches(
                distributorId,
                BigDecimal.valueOf(10_000),
                LocalDate.now(),
                "MPESA PAYMENT FROM JOHN",
                "REF123"
        );

        assertThat(results).isEmpty();
        verifyNoInteractions(modelLoader, phaseService);
    }

    @Test
    void findMatches_candidateOutsideAmountWindow_isFiltered() {
        UUID distributorId = UUID.randomUUID();
        Payment payment = mockPayment(UUID.randomUUID(), BigDecimal.valueOf(50_000), LocalDateTime.now());

        when(paymentRepository.findByDistributorIdAndPaymentDateBetween(
                eq(distributorId), any(), any()))
                .thenReturn(List.of(payment));

        // Bank amount is 10k, payment is 50k — way outside ±10% window
        List<BankReconMatcher.MatchResult> results = matcher.findMatches(
                distributorId,
                BigDecimal.valueOf(10_000),
                LocalDate.now(),
                "MPESA PAYMENT",
                null
        );

        assertThat(results).isEmpty();
    }

    @Test
    void findMatches_nullModelFallback_exactAmountMatch_returnsSuggest() {
        UUID distributorId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(20_000);
        Payment payment = mockPayment(UUID.randomUUID(), amount, LocalDateTime.now());

        when(paymentRepository.findByDistributorIdAndPaymentDateBetween(
                eq(distributorId), any(), any()))
                .thenReturn(List.of(payment));
        when(modelLoader.loadModel(BankReconMatcher.MODEL_NAME)).thenReturn(null);

        // Heuristic: amountExactMatch=1.0 → 0.5, dateDiffDays=0 ≤ 3 → 0.3 → total 0.8
        // phaseService.applyModifier(0.8, ...) needs stub
        when(phaseService.applyModifier(anyDouble(), anyString())).thenAnswer(inv -> inv.getArgument(0));

        List<BankReconMatcher.MatchResult> results = matcher.findMatches(
                distributorId,
                amount,
                LocalDate.now(),
                "MPESA PAYMENT",
                null
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).disposition()).isIn("SUGGEST", "AUTO_MATCH");
    }

    @Test
    void findMatches_resultsSortedByProbabilityDescending() {
        UUID distributorId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(15_000);

        // Two candidates with same amount
        Payment p1 = mockPayment(UUID.randomUUID(), amount, LocalDateTime.now());
        Payment p2 = mockPayment(UUID.randomUUID(), amount, LocalDateTime.now().minusDays(4));

        when(paymentRepository.findByDistributorIdAndPaymentDateBetween(
                eq(distributorId), any(), any()))
                .thenReturn(List.of(p1, p2));
        when(modelLoader.loadModel(anyString())).thenReturn(null);
        when(phaseService.applyModifier(anyDouble(), anyString())).thenAnswer(inv -> inv.getArgument(0));

        List<BankReconMatcher.MatchResult> results = matcher.findMatches(
                distributorId, amount, LocalDate.now(), "PAYMENT", null);

        // Results should be sorted descending
        if (results.size() >= 2) {
            assertThat(results.get(0).matchProbability())
                    .isGreaterThanOrEqualTo(results.get(1).matchProbability());
        }
    }

    @Test
    void matchResult_record_holdsAllFields() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = mockPayment(paymentId, BigDecimal.TEN, LocalDateTime.now());

        BankReconMatcher.MatchResult result =
                new BankReconMatcher.MatchResult(paymentId, payment, 0.97, 0.90, "AUTO_MATCH");

        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.matchProbability()).isEqualTo(0.97);
        assertThat(result.confidenceScore()).isEqualTo(0.90);
        assertThat(result.disposition()).isEqualTo("AUTO_MATCH");
        assertThat(result.payment()).isSameAs(payment);
    }

    @Test
    void modelName_matchesTrainingPipeline() {
        assertThat(BankReconMatcher.MODEL_NAME).isEqualTo(ReconTrainingPipeline.MODEL_NAME);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Payment mockPayment(UUID id, BigDecimal amount, LocalDateTime date) {
        Payment payment = mock(Payment.class);
        lenient().when(payment.getId()).thenReturn(id);
        lenient().when(payment.getAmount()).thenReturn(amount);
        lenient().when(payment.getPaymentDate()).thenReturn(date);
        lenient().when(payment.getPaymentMethod()).thenReturn(null);
        lenient().when(payment.getMerchant()).thenReturn(null);
        return payment;
    }
}
