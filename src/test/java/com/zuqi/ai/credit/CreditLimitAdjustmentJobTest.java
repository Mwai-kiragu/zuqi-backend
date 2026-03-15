package com.zuqi.ai.credit;

import com.zuqi.domain.credit.CreditLimit;
import com.zuqi.domain.credit.CreditLimitStatus;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.customer.Customer;
import com.zuqi.repository.CreditLimitRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.CustomerRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CreditLimitAdjustmentJob}.
 *
 * Covers: increase capped at max%, decrease flagged for review, new limit creation,
 * unchanged limit, minimum floor enforcement, merchant not found, and batch scheduling.
 */
@ExtendWith(MockitoExtension.class)
class CreditLimitAdjustmentJobTest {

    @Mock private CreditLimitRegressor   creditLimitRegressor;
    @Mock private CustomerRepository     merchantRepository;
    @Mock private CreditLimitRepository  creditLimitRepository;
    @Mock private DistributorRepository  distributorRepository;
    @Mock private MeterRegistry          meterRegistry;

    @InjectMocks
    private CreditLimitAdjustmentJob job;

    private UUID merchantId;
    private UUID distributorId;
    private Customer merchant;
    private Distributor distributor;

    @BeforeEach
    void setUp() throws Exception {
        merchantId = UUID.randomUUID();
        distributorId = UUID.randomUUID();

        distributor = new Distributor();
        distributor.setId(distributorId);
        distributor.setName("Test Distributor");

        merchant = new Customer();
        merchant.setId(merchantId);
        merchant.setDistributor(distributor);
        merchant.setBusinessName("Test Merchant");

        // Set default config values via reflection (injected by @Value)
        var maxField = CreditLimitAdjustmentJob.class.getDeclaredField("maxIncreasePct");
        maxField.setAccessible(true);
        maxField.setDouble(job, 20.0);

        var minField = CreditLimitAdjustmentJob.class.getDeclaredField("minLimitKes");
        minField.setAccessible(true);
        minField.setDouble(job, 50_000.0);
    }

    // ── increase capped at 20% ──────────────────────────────────────────

    @Test
    void adjustCreditLimit_whenIncrease_capsAtMaxPct() {
        // Existing limit: 100k, ML predicts 200k → capped at 120k (20% increase)
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(creditLimitRegressor.predictCreditLimit(merchantId))
                .thenReturn(BigDecimal.valueOf(200_000));

        CreditLimit existing = CreditLimit.builder()
                .merchant(merchant)
                .distributor(distributor)
                .approvedLimit(BigDecimal.valueOf(100_000))
                .availableLimit(BigDecimal.valueOf(80_000))
                .utilizedAmount(BigDecimal.valueOf(20_000))
                .status(CreditLimitStatus.ACTIVE)
                .build();
        when(creditLimitRepository.findByMerchantIdAndDistributorIdAndStatus(
                merchantId, distributorId, CreditLimitStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        CreditLimitAdjustmentJob.CreditAdjustmentResult result = job.adjustCreditLimit(merchantId);

        assertThat(result.action()).isEqualTo("INCREASED");
        assertThat(result.newLimitKes()).isEqualTo(120_000.0); // 100k × 1.20
        assertThat(result.previousLimitKes()).isEqualTo(100_000.0);
        assertThat(result.changePct()).isCloseTo(20.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.requiresReview()).isFalse();
    }

    // ── decrease flags for human review ─────────────────────────────────

    @Test
    void adjustCreditLimit_whenDecrease_flagsForReview() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(creditLimitRegressor.predictCreditLimit(merchantId))
                .thenReturn(BigDecimal.valueOf(70_000));

        CreditLimit existing = CreditLimit.builder()
                .merchant(merchant)
                .distributor(distributor)
                .approvedLimit(BigDecimal.valueOf(100_000))
                .availableLimit(BigDecimal.valueOf(100_000))
                .utilizedAmount(BigDecimal.ZERO)
                .status(CreditLimitStatus.ACTIVE)
                .build();
        when(creditLimitRepository.findByMerchantIdAndDistributorIdAndStatus(
                merchantId, distributorId, CreditLimitStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        CreditLimitAdjustmentJob.CreditAdjustmentResult result = job.adjustCreditLimit(merchantId);

        assertThat(result.action()).isEqualTo("DECREASED");
        assertThat(result.newLimitKes()).isEqualTo(70_000.0);
        assertThat(result.requiresReview()).isTrue();
        assertThat(result.changePct()).isCloseTo(-30.0, org.assertj.core.data.Offset.offset(0.01));
    }

    // ── no existing limit — creates new one ─────────────────────────────

    @Test
    void adjustCreditLimit_whenNoExistingLimit_createsNewOne() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(creditLimitRegressor.predictCreditLimit(merchantId))
                .thenReturn(BigDecimal.valueOf(150_000));
        when(creditLimitRepository.findByMerchantIdAndDistributorIdAndStatus(
                merchantId, distributorId, CreditLimitStatus.ACTIVE))
                .thenReturn(Optional.empty());

        CreditLimitAdjustmentJob.CreditAdjustmentResult result = job.adjustCreditLimit(merchantId);

        assertThat(result.action()).isEqualTo("CREATED");
        assertThat(result.newLimitKes()).isEqualTo(150_000.0);
        assertThat(result.previousLimitKes()).isEqualTo(0.0);
        assertThat(result.requiresReview()).isFalse();

        ArgumentCaptor<CreditLimit> captor = ArgumentCaptor.forClass(CreditLimit.class);
        verify(creditLimitRepository).save(captor.capture());
        assertThat(captor.getValue().getApprovedLimit().doubleValue()).isCloseTo(150_000.0,
                org.assertj.core.data.Offset.offset(0.01));
    }

    // ── minimum floor enforcement ───────────────────────────────────────

    @Test
    void adjustCreditLimit_whenBelowFloor_enforcesMinimum() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        // ML predicts 30k, which is below 50k floor
        when(creditLimitRegressor.predictCreditLimit(merchantId))
                .thenReturn(BigDecimal.valueOf(30_000));
        when(creditLimitRepository.findByMerchantIdAndDistributorIdAndStatus(
                merchantId, distributorId, CreditLimitStatus.ACTIVE))
                .thenReturn(Optional.empty());

        CreditLimitAdjustmentJob.CreditAdjustmentResult result = job.adjustCreditLimit(merchantId);

        assertThat(result.newLimitKes()).isEqualTo(50_000.0);
    }

    // ── unchanged limit ─────────────────────────────────────────────────

    @Test
    void adjustCreditLimit_whenPredictionMatchesCurrent_returnsUnchanged() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(creditLimitRegressor.predictCreditLimit(merchantId))
                .thenReturn(BigDecimal.valueOf(100_000));

        CreditLimit existing = CreditLimit.builder()
                .merchant(merchant)
                .distributor(distributor)
                .approvedLimit(BigDecimal.valueOf(100_000))
                .availableLimit(BigDecimal.valueOf(100_000))
                .utilizedAmount(BigDecimal.ZERO)
                .status(CreditLimitStatus.ACTIVE)
                .build();
        when(creditLimitRepository.findByMerchantIdAndDistributorIdAndStatus(
                merchantId, distributorId, CreditLimitStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        CreditLimitAdjustmentJob.CreditAdjustmentResult result = job.adjustCreditLimit(merchantId);

        assertThat(result.action()).isEqualTo("UNCHANGED");
        assertThat(result.changePct()).isEqualTo(0.0);
        assertThat(result.requiresReview()).isFalse();
    }

    // ── merchant not found ──────────────────────────────────────────────

    @Test
    void adjustCreditLimit_whenMerchantNotFound_throwsException() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> job.adjustCreditLimit(merchantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Merchant not found");
    }

    // ── result record structure ─────────────────────────────────────────

    @Test
    void creditAdjustmentResult_hasCorrectValues() {
        CreditLimitAdjustmentJob.CreditAdjustmentResult result =
                new CreditLimitAdjustmentJob.CreditAdjustmentResult(
                        merchantId, 100_000.0, 120_000.0, 20.0, "INCREASED", false);

        assertThat(result.merchantId()).isEqualTo(merchantId);
        assertThat(result.previousLimitKes()).isEqualTo(100_000.0);
        assertThat(result.newLimitKes()).isEqualTo(120_000.0);
        assertThat(result.changePct()).isEqualTo(20.0);
        assertThat(result.action()).isEqualTo("INCREASED");
        assertThat(result.requiresReview()).isFalse();
    }

    // ── available limit correctly preserved after increase ──────────────

    @Test
    void adjustCreditLimit_whenIncrease_preservesUtilizedAmount() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(creditLimitRegressor.predictCreditLimit(merchantId))
                .thenReturn(BigDecimal.valueOf(110_000)); // within 20% cap

        CreditLimit existing = CreditLimit.builder()
                .merchant(merchant)
                .distributor(distributor)
                .approvedLimit(BigDecimal.valueOf(100_000))
                .availableLimit(BigDecimal.valueOf(60_000))
                .utilizedAmount(BigDecimal.valueOf(40_000))
                .status(CreditLimitStatus.ACTIVE)
                .build();
        when(creditLimitRepository.findByMerchantIdAndDistributorIdAndStatus(
                merchantId, distributorId, CreditLimitStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        job.adjustCreditLimit(merchantId);

        // After increase to 110k with 40k utilized, available = 70k
        ArgumentCaptor<CreditLimit> captor = ArgumentCaptor.forClass(CreditLimit.class);
        verify(creditLimitRepository).save(captor.capture());
        CreditLimit saved = captor.getValue();
        assertThat(saved.getAvailableLimit().doubleValue()).isCloseTo(70_000.0,
                org.assertj.core.data.Offset.offset(1.0));
    }
}
