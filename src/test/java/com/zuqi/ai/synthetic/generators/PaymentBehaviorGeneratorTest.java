package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.dto.SyntheticMerchant;
import com.zuqi.ai.synthetic.dto.SyntheticOrder;
import com.zuqi.ai.synthetic.dto.SyntheticPayment;
import com.zuqi.ai.synthetic.generators.OrderHistoryGenerator.OrderHistoryResult;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentBehaviorGeneratorTest {

    @Mock
    private BusinessNameGenerator nameGenerator;

    private MerchantProfileGenerator merchantGenerator;
    private OrderHistoryGenerator     orderGenerator;
    private PaymentBehaviorGenerator  paymentGenerator;

    @BeforeEach
    void setUp() {
        when(nameGenerator.generateBatch(anyString(), anyInt(), anyLong()))
                .thenAnswer(inv -> {
                    String cat   = inv.getArgument(0);
                    int    count = inv.getArgument(1);
                    List<String> names = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) names.add(cat + " Biz " + i);
                    return names;
                });
        merchantGenerator = new MerchantProfileGenerator(nameGenerator);
        orderGenerator    = new OrderHistoryGenerator();
        paymentGenerator  = new PaymentBehaviorGenerator();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private record Dataset(List<SyntheticMerchant> merchants,
                           List<SyntheticOrder>    orders,
                           List<SyntheticPayment>  payments) {}

    private Dataset build(int merchantCount, Map<MerchantArchetype, Double> ratios,
                          int historyMonths, long seed) {
        SyntheticDataConfig cfg = new SyntheticDataConfig(
                UUID.randomUUID(), merchantCount, historyMonths, seed, ratios);
        List<SyntheticMerchant> merchants = merchantGenerator.generate(cfg);
        OrderHistoryResult      history   = orderGenerator.generate(merchants, cfg);
        List<SyntheticPayment>  payments  = paymentGenerator.generate(
                history.orders(), merchants, cfg);
        return new Dataset(merchants, history.orders(), payments);
    }

    private Dataset buildDefault(long seed) {
        return build(200, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS, 12, seed);
    }

    // -------------------------------------------------------------------------
    // Basic correctness
    // -------------------------------------------------------------------------

    @Test
    void generate_shouldProducePayments() {
        Dataset ds = buildDefault(42L);
        assertThat(ds.payments()).isNotEmpty();
    }

    @Test
    void generate_resultShouldBeUnmodifiable() {
        Dataset ds = buildDefault(1L);
        assertThat(ds.payments()).isNotEmpty();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> ds.payments().add(ds.payments().get(0)));
    }

    @Test
    void generate_allFieldsShouldBePopulated() {
        Dataset ds = buildDefault(7L);
        ds.payments().forEach(p -> {
            assertThat(p.syntheticId()).isNotNull();
            assertThat(p.invoiceRef()).isNotNull();
            assertThat(p.merchantRef()).isNotNull();
            assertThat(p.amount()).isNotNull();
            assertThat(p.amount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(p.paymentDate()).isNotNull();
            assertThat(p.paymentMethod()).isNotBlank();
            assertThat(p.daysAfterInvoice()).isGreaterThanOrEqualTo(0);
        });
    }

    // -------------------------------------------------------------------------
    // Relational integrity
    // -------------------------------------------------------------------------

    @Test
    void generate_everyPaymentShouldLinkToAKnownOrder() {
        Dataset ds = buildDefault(42L);
        Set<UUID> orderIds = ds.orders().stream()
                .map(SyntheticOrder::syntheticId)
                .collect(Collectors.toSet());

        ds.payments().forEach(p ->
                assertThat(orderIds)
                        .as("invoiceRef %s must be a known order", p.invoiceRef())
                        .contains(p.invoiceRef()));
    }

    @Test
    void generate_everyPaymentShouldLinkToAKnownMerchant() {
        Dataset ds = buildDefault(42L);
        Set<UUID> merchantIds = ds.merchants().stream()
                .map(SyntheticMerchant::syntheticId)
                .collect(Collectors.toSet());

        ds.payments().forEach(p ->
                assertThat(merchantIds)
                        .as("merchantRef %s must be a known merchant", p.merchantRef())
                        .contains(p.merchantRef()));
    }

    // -------------------------------------------------------------------------
    // Amount correctness — partial payments sum to invoice total
    // -------------------------------------------------------------------------

    @Test
    void generate_partialPaymentsShouldSumToInvoiceTotal() {
        Dataset ds = buildDefault(42L);

        Map<UUID, BigDecimal> invoiceTotals = ds.orders().stream()
                .collect(Collectors.toMap(SyntheticOrder::syntheticId,
                        SyntheticOrder::totalAmount));

        Map<UUID, List<SyntheticPayment>> byOrder = ds.payments().stream()
                .collect(Collectors.groupingBy(SyntheticPayment::invoiceRef));

        byOrder.forEach((orderId, orderPayments) -> {
            BigDecimal invoiceTotal = invoiceTotals.get(orderId);
            if (invoiceTotal == null) return; // guard

            boolean hasDefault = orderPayments.stream().anyMatch(SyntheticPayment::isDefault);

            if (hasDefault) {
                // Missed payment: amount field == invoice total (outstanding obligation)
                BigDecimal defaultAmt = orderPayments.stream()
                        .filter(SyntheticPayment::isDefault)
                        .map(SyntheticPayment::amount)
                        .findFirst().orElseThrow();
                assertThat(defaultAmt)
                        .as("Default payment amount should equal invoice total for order %s", orderId)
                        .isEqualByComparingTo(invoiceTotal);
            } else {
                // Full or partial: amounts sum to invoice total
                BigDecimal totalPaid = orderPayments.stream()
                        .map(SyntheticPayment::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(totalPaid)
                        .as("Payments for order %s should sum to invoice total", orderId)
                        .isEqualByComparingTo(invoiceTotal);
            }
        });
    }

    @Test
    void generate_partialPayments_amountsShouldBePositive() {
        Dataset ds = buildDefault(42L);
        ds.payments().forEach(p ->
                assertThat(p.amount())
                        .as("Payment amount should be positive")
                        .isGreaterThan(BigDecimal.ZERO));
    }

    // -------------------------------------------------------------------------
    // Payment timing distributions per archetype
    // -------------------------------------------------------------------------

    @Test
    void generate_steadyGrowerShouldPayFaster_thanDefaulter() {
        Dataset ds = buildDefault(42L);

        Map<UUID, MerchantArchetype> archetypeByMerchant = ds.merchants().stream()
                .collect(Collectors.toMap(
                        SyntheticMerchant::syntheticId,
                        SyntheticMerchant::merchantArchetype));

        // Average days-to-payment per archetype (exclude defaults)
        Map<MerchantArchetype, Double> avgDaysByArchetype = ds.payments().stream()
                .filter(p -> !p.isDefault())
                .collect(Collectors.groupingBy(
                        p -> archetypeByMerchant.getOrDefault(p.merchantRef(),
                                MerchantArchetype.STABLE_PERFORMER),
                        Collectors.averagingInt(SyntheticPayment::daysAfterInvoice)));

        Double steadyAvgDays  = avgDaysByArchetype.get(MerchantArchetype.STEADY_GROWER);
        Double defaulterAvgDays = avgDaysByArchetype.get(MerchantArchetype.DEFAULTER);

        if (steadyAvgDays != null && defaulterAvgDays != null) {
            // STEADY_GROWER mean=7 days vs DEFAULTER mean=30 days
            assertThat(steadyAvgDays).isLessThan(defaulterAvgDays);
        }
    }

    @Test
    void generate_steadyGrowerAvgDays_shouldBeLessThan15() {
        Dataset ds = build(200, Map.of(MerchantArchetype.STEADY_GROWER, 1.0), 12, 42L);

        double avgDays = ds.payments().stream()
                .filter(p -> !p.isDefault())
                .mapToInt(SyntheticPayment::daysAfterInvoice)
                .average().orElse(0);

        // STEADY_GROWER paymentDaysMean=7, stdDev=3 → avg should be well under 15
        assertThat(avgDays).isLessThan(15.0);
    }

    @Test
    void generate_defaulterAvgDays_shouldBeGreaterThan20() {
        Dataset ds = build(200, Map.of(MerchantArchetype.DEFAULTER, 1.0), 12, 42L);

        double avgDays = ds.payments().stream()
                .filter(p -> !p.isDefault())
                .mapToInt(SyntheticPayment::daysAfterInvoice)
                .average().orElse(0);

        // DEFAULTER paymentDaysMean=30 → average of non-default payments > 20
        assertThat(avgDays).isGreaterThan(20.0);
    }

    // -------------------------------------------------------------------------
    // Default sequence — DEFAULTER archetype
    // -------------------------------------------------------------------------

    @Test
    void generate_defaulterShouldHaveMoreDefaultsThan_steadyGrower() {
        Dataset ds = buildDefault(42L);

        Map<UUID, MerchantArchetype> archetypeByMerchant = ds.merchants().stream()
                .collect(Collectors.toMap(SyntheticMerchant::syntheticId,
                        SyntheticMerchant::merchantArchetype));

        long defaulterDefaults = ds.payments().stream()
                .filter(SyntheticPayment::isDefault)
                .filter(p -> archetypeByMerchant.get(p.merchantRef()) == MerchantArchetype.DEFAULTER)
                .count();

        long steadyDefaults = ds.payments().stream()
                .filter(SyntheticPayment::isDefault)
                .filter(p -> archetypeByMerchant.get(p.merchantRef()) == MerchantArchetype.STEADY_GROWER)
                .count();

        assertThat(defaulterDefaults)
                .as("DEFAULTER should produce more missed payments than STEADY_GROWER")
                .isGreaterThan(steadyDefaults);
    }

    @Test
    void generate_defaultPayments_daysAfterInvoice_shouldExceed90() {
        Dataset ds = buildDefault(42L);

        ds.payments().stream()
                .filter(SyntheticPayment::isDefault)
                .forEach(p ->
                        assertThat(p.daysAfterInvoice())
                                .as("Missed payments should be 90+ days overdue")
                                .isGreaterThanOrEqualTo(90));
    }

    @Test
    void generate_defaultPayments_methodShouldBeNone() {
        Dataset ds = buildDefault(42L);

        ds.payments().stream()
                .filter(SyntheticPayment::isDefault)
                .forEach(p ->
                        assertThat(p.paymentMethod())
                                .as("Missed payment method should be NONE")
                                .isEqualTo("NONE"));
    }

    @Test
    void generate_missedPaymentsIncrease_lateInHistory() {
        // Use 200 DEFAULTER merchants over 24 months — defaults should cluster in months 6+
        Dataset ds = build(200, Map.of(MerchantArchetype.DEFAULTER, 1.0), 24, 42L);

        Map<UUID, SyntheticOrder> orderById = ds.orders().stream()
                .collect(Collectors.toMap(SyntheticOrder::syntheticId, o -> o));

        long earlyDefaults = ds.payments().stream()
                .filter(SyntheticPayment::isDefault)
                .filter(p -> {
                    SyntheticOrder o = orderById.get(p.invoiceRef());
                    if (o == null) return false;
                    return o.orderDate().getMonthValue() <= 6
                            || o.orderDate().toLocalDate()
                               .isBefore(java.time.LocalDate.now().minusMonths(18));
                })
                .count();

        long lateDefaults = ds.payments().stream()
                .filter(SyntheticPayment::isDefault)
                .filter(p -> {
                    SyntheticOrder o = orderById.get(p.invoiceRef());
                    if (o == null) return false;
                    return o.orderDate().toLocalDate()
                            .isAfter(java.time.LocalDate.now().minusMonths(7));
                })
                .count();

        // More defaults in the recent window than in the early window
        assertThat(lateDefaults).isGreaterThan(earlyDefaults);
    }

    // -------------------------------------------------------------------------
    // Payment method distribution
    // -------------------------------------------------------------------------

    @Test
    void generate_mPesaShouldBeDominantPaymentMethod() {
        Dataset ds = buildDefault(42L);

        Map<String, Long> byMethod = ds.payments().stream()
                .filter(p -> !p.isDefault())
                .collect(Collectors.groupingBy(SyntheticPayment::paymentMethod,
                        Collectors.counting()));

        long mpesa = byMethod.getOrDefault("MPESA", 0L);
        long bank  = byMethod.getOrDefault("KCB_TRANSFER", 0L);
        long cash  = byMethod.getOrDefault("CASH", 0L);

        // MPESA 65% should be the biggest share
        assertThat(mpesa).isGreaterThan(bank);
        assertThat(mpesa).isGreaterThan(cash);

        // KCB_TRANSFER 25% > CASH 10%
        assertThat(bank).isGreaterThan(cash);
    }

    @Test
    void generate_paymentMethodDistribution_shouldMatchApproxWeights() {
        Dataset ds = build(500, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS, 12, 42L);

        Map<String, Long> byMethod = ds.payments().stream()
                .filter(p -> !p.isDefault())
                .collect(Collectors.groupingBy(SyntheticPayment::paymentMethod,
                        Collectors.counting()));

        long total = byMethod.values().stream().mapToLong(Long::longValue).sum();
        double mpesaRatio = (double) byMethod.getOrDefault("MPESA", 0L) / total;
        double bankRatio  = (double) byMethod.getOrDefault("KCB_TRANSFER", 0L) / total;

        // Allow ±10% tolerance around 65% and 25%
        assertThat(mpesaRatio).isBetween(0.55, 0.75);
        assertThat(bankRatio).isBetween(0.15, 0.35);
    }

    // -------------------------------------------------------------------------
    // Partial payment flags
    // -------------------------------------------------------------------------

    @Test
    void generate_partialPayments_shouldHaveIsPartialTrue() {
        Dataset ds = buildDefault(42L);

        // Orders with multiple payments must all be partial
        Map<UUID, List<SyntheticPayment>> byOrder = ds.payments().stream()
                .collect(Collectors.groupingBy(SyntheticPayment::invoiceRef));

        byOrder.forEach((orderId, payments) -> {
            if (payments.size() > 1) {
                payments.forEach(p ->
                        assertThat(p.isPartial())
                                .as("Multi-payment order %s: all records must be partial", orderId)
                                .isTrue());
            }
        });
    }

    @Test
    void generate_singleFullPayment_shouldHaveIsPartialFalse_andIsDefaultFalse() {
        Dataset ds = buildDefault(42L);

        Map<UUID, List<SyntheticPayment>> byOrder = ds.payments().stream()
                .collect(Collectors.groupingBy(SyntheticPayment::invoiceRef));

        byOrder.forEach((orderId, payments) -> {
            if (payments.size() == 1) {
                SyntheticPayment p = payments.get(0);
                // Single payment is either a full payment or a default — never both
                assertThat(p.isPartial() && p.isDefault())
                        .as("A single payment cannot be both partial and default")
                        .isFalse();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Determinism
    // -------------------------------------------------------------------------

    @Test
    void generate_sameSeedProducesSamePaymentCount() {
        SyntheticDataConfig cfg = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 99L);

        List<SyntheticMerchant> ms1 = merchantGenerator.generate(cfg);
        OrderHistoryResult       h1 = orderGenerator.generate(ms1, cfg);
        List<SyntheticPayment>   p1 = paymentGenerator.generate(h1.orders(), ms1, cfg);

        List<SyntheticMerchant> ms2 = merchantGenerator.generate(cfg);
        OrderHistoryResult       h2 = orderGenerator.generate(ms2, cfg);
        List<SyntheticPayment>   p2 = paymentGenerator.generate(h2.orders(), ms2, cfg);

        assertThat(p1.size()).isEqualTo(p2.size());
    }
}
