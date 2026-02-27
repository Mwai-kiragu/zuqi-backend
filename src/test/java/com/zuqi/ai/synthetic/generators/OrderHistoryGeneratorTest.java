package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.dto.SyntheticMerchant;
import com.zuqi.ai.synthetic.dto.SyntheticOrder;
import com.zuqi.ai.synthetic.dto.SyntheticOrderItem;
import com.zuqi.ai.synthetic.generators.OrderHistoryGenerator.OrderHistoryResult;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class OrderHistoryGeneratorTest {

    @Mock
    private BusinessNameGenerator nameGenerator;

    private MerchantProfileGenerator merchantGenerator;
    private OrderHistoryGenerator     orderGenerator;

    @BeforeEach
    void setUp() {
        when(nameGenerator.generateBatch(anyString(), anyInt(), anyLong()))
                .thenAnswer(inv -> {
                    String cat   = inv.getArgument(0);
                    int    count = inv.getArgument(1);
                    List<String> names = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) names.add(cat + " Business " + i);
                    return names;
                });
        merchantGenerator = new MerchantProfileGenerator(nameGenerator);
        orderGenerator    = new OrderHistoryGenerator();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<SyntheticMerchant> merchants(int count, long seed) {
        return merchantGenerator.generate(SyntheticDataConfig.defaultConfig(UUID.randomUUID(), seed));
    }

    private List<SyntheticMerchant> allArchetype(MerchantArchetype archetype, int count) {
        Map<MerchantArchetype, Double> ratios = Map.of(archetype, 1.0);
        SyntheticDataConfig cfg = new SyntheticDataConfig(
                UUID.randomUUID(), count, 12, 42L, ratios);
        return merchantGenerator.generate(cfg);
    }

    // -------------------------------------------------------------------------
    // Basic correctness
    // -------------------------------------------------------------------------

    @Test
    void generate_shouldProduceOrdersForMerchants() {
        List<SyntheticMerchant> ms = merchants(500, 42L);
        SyntheticDataConfig cfg    = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L);
        OrderHistoryResult result  = orderGenerator.generate(ms, cfg);
        assertThat(result.orders()).isNotEmpty();
        assertThat(result.items()).isNotEmpty();
    }

    @Test
    void generate_resultListsShouldBeUnmodifiable() {
        List<SyntheticMerchant> ms = merchants(500, 1L);
        SyntheticDataConfig cfg    = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 1L);
        OrderHistoryResult result  = orderGenerator.generate(ms, cfg);

        assertThat(result.orders()).isNotEmpty();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> result.orders().add(result.orders().get(0)));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> result.items().add(result.items().get(0)));
    }

    @Test
    void generate_allOrderFieldsShouldBePopulated() {
        List<SyntheticMerchant> ms = merchants(500, 7L);
        SyntheticDataConfig cfg    = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 7L);

        for (SyntheticOrder order : orderGenerator.generate(ms, cfg).orders()) {
            assertThat(order.syntheticId()).isNotNull();
            assertThat(order.merchantRef()).isNotNull();
            assertThat(order.salesRepRef()).isNotNull();
            assertThat(order.orderDate()).isNotNull();
            assertThat(order.totalAmount()).isNotNull();
            assertThat(order.totalAmount()).isGreaterThan(BigDecimal.ZERO);
            assertThat(order.status()).isIn("DELIVERED", "CANCELLED", "PROCESSING",
                    "CONFIRMED", "PENDING");
            assertThat(order.merchantArchetype()).isNotNull();
        }
    }

    @Test
    void generate_allItemFieldsShouldBePopulated() {
        List<SyntheticMerchant> ms = merchants(500, 9L);
        SyntheticDataConfig cfg    = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 9L);

        for (SyntheticOrderItem item : orderGenerator.generate(ms, cfg).items()) {
            assertThat(item.orderRef()).isNotNull();
            assertThat(item.skuId()).isNotNull();
            assertThat(item.quantity()).isGreaterThan(BigDecimal.ZERO);
            assertThat(item.unitPrice()).isGreaterThan(BigDecimal.ZERO);
            assertThat(item.lineTotal()).isGreaterThan(BigDecimal.ZERO);
        }
    }

    // -------------------------------------------------------------------------
    // Relational integrity
    // -------------------------------------------------------------------------

    @Test
    void generate_everyOrderShouldLinkToAKnownMerchant() {
        List<SyntheticMerchant> ms = merchants(500, 42L);
        SyntheticDataConfig cfg    = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L);
        Set<UUID> merchantIds      = ms.stream()
                .map(SyntheticMerchant::syntheticId)
                .collect(Collectors.toSet());

        orderGenerator.generate(ms, cfg).orders().forEach(order ->
                assertThat(merchantIds).contains(order.merchantRef()));
    }

    @Test
    void generate_everyItemShouldLinkToAKnownOrder() {
        List<SyntheticMerchant> ms = merchants(500, 42L);
        SyntheticDataConfig cfg    = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L);
        OrderHistoryResult result  = orderGenerator.generate(ms, cfg);

        Set<UUID> orderIds = result.orders().stream()
                .map(SyntheticOrder::syntheticId)
                .collect(Collectors.toSet());

        result.items().forEach(item ->
                assertThat(orderIds).contains(item.orderRef()));
    }

    @Test
    void generate_orderTotalShouldEqualSumOfLineTotals() {
        List<SyntheticMerchant> ms = merchants(500, 42L);
        SyntheticDataConfig cfg    = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L);
        OrderHistoryResult result  = orderGenerator.generate(ms, cfg);

        Map<UUID, BigDecimal> itemSumByOrder = result.items().stream()
                .collect(Collectors.groupingBy(
                        SyntheticOrderItem::orderRef,
                        Collectors.reducing(BigDecimal.ZERO,
                                SyntheticOrderItem::lineTotal,
                                BigDecimal::add)));

        result.orders().forEach(order -> {
            BigDecimal expected = itemSumByOrder.getOrDefault(order.syntheticId(), BigDecimal.ZERO);
            assertThat(order.totalAmount())
                    .as("totalAmount for order %s", order.syntheticId())
                    .isEqualByComparingTo(expected);
        });
    }

    // -------------------------------------------------------------------------
    // Archetype ordering
    // -------------------------------------------------------------------------

    @Test
    void generate_steadyGrowerShouldProduceMoreOrdersThanDefaulter() {
        SyntheticDataConfig cfg = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L);
        List<SyntheticMerchant> ms = merchantGenerator.generate(cfg);
        OrderHistoryResult result  = orderGenerator.generate(ms, cfg);

        Map<MerchantArchetype, Long> ordersByMerchant = result.orders().stream()
                .collect(Collectors.groupingBy(SyntheticOrder::merchantArchetype, Collectors.counting()));

        long steadyOrders  = ordersByMerchant.getOrDefault(MerchantArchetype.STEADY_GROWER, 0L);
        long defaultOrders = ordersByMerchant.getOrDefault(MerchantArchetype.DEFAULTER, 0L);

        // STEADY_GROWER has both more merchants (35%) and higher order frequency (2.5/wk)
        assertThat(steadyOrders).isGreaterThan(defaultOrders);
    }

    @Test
    void generate_steadyGrowerShouldHaveHigherAvgOrderValue_thanDefaulter() {
        List<SyntheticMerchant> steadyMs = allArchetype(MerchantArchetype.STEADY_GROWER, 100);
        List<SyntheticMerchant> defaultMs = allArchetype(MerchantArchetype.DEFAULTER, 100);

        SyntheticDataConfig cfg = new SyntheticDataConfig(
                UUID.randomUUID(), 100, 12, 42L,
                Map.of(MerchantArchetype.STEADY_GROWER, 1.0));
        OrderHistoryResult steadyResult = orderGenerator.generate(steadyMs, cfg);

        SyntheticDataConfig cfgD = new SyntheticDataConfig(
                UUID.randomUUID(), 100, 12, 42L,
                Map.of(MerchantArchetype.DEFAULTER, 1.0));
        OrderHistoryResult defaultResult = orderGenerator.generate(defaultMs, cfgD);

        double steadyAvg  = steadyResult.orders().stream()
                .mapToDouble(o -> o.totalAmount().doubleValue()).average().orElse(0);
        double defaultAvg = defaultResult.orders().stream()
                .mapToDouble(o -> o.totalAmount().doubleValue()).average().orElse(0);

        // STEADY_GROWER mean 25,000 KES vs DEFAULTER mean 12,000 KES
        assertThat(steadyAvg).isGreaterThan(defaultAvg);
    }

    // -------------------------------------------------------------------------
    // Seasonality
    // -------------------------------------------------------------------------

    @Test
    void generate_seasonality_decemberOrdersShouldBeHigherThanApril() {
        // Use STABLE_PERFORMER (low stdDev) for cleaner signal, 24-month history
        SyntheticDataConfig cfg = new SyntheticDataConfig(
                UUID.randomUUID(), 200, 24, 42L,
                Map.of(MerchantArchetype.STABLE_PERFORMER, 1.0));
        List<SyntheticMerchant> ms = merchantGenerator.generate(cfg);
        OrderHistoryResult result  = orderGenerator.generate(ms, cfg);

        Map<Integer, Double> avgByMonth = result.orders().stream()
                .collect(Collectors.groupingBy(
                        o -> o.orderDate().getMonthValue(),
                        Collectors.averagingDouble(o -> o.totalAmount().doubleValue())));

        Double decAvg = avgByMonth.get(12);
        Double aprAvg = avgByMonth.get(4);

        // December multiplier 1.30 vs April multiplier 0.90 → Dec should be clearly higher
        assertThat(decAvg).isNotNull();
        assertThat(aprAvg).isNotNull();
        assertThat(decAvg).isGreaterThan(aprAvg);
    }

    @Test
    void generate_seasonality_novemberShouldBeHigherThanMarch() {
        SyntheticDataConfig cfg = new SyntheticDataConfig(
                UUID.randomUUID(), 200, 24, 7L,
                Map.of(MerchantArchetype.STABLE_PERFORMER, 1.0));
        List<SyntheticMerchant> ms = merchantGenerator.generate(cfg);
        OrderHistoryResult result  = orderGenerator.generate(ms, cfg);

        Map<Integer, Double> avgByMonth = result.orders().stream()
                .collect(Collectors.groupingBy(
                        o -> o.orderDate().getMonthValue(),
                        Collectors.averagingDouble(o -> o.totalAmount().doubleValue())));

        Double novAvg = avgByMonth.get(11); // 1.20 multiplier
        Double marAvg = avgByMonth.get(3);  // 0.90 multiplier

        assertThat(novAvg).isNotNull();
        assertThat(marAvg).isNotNull();
        assertThat(novAvg).isGreaterThan(marAvg);
    }

    // -------------------------------------------------------------------------
    // Growth trend
    // -------------------------------------------------------------------------

    @Test
    void generate_steadyGrower_orderValuesShouldIncreaseOverTime() {
        SyntheticDataConfig cfg = new SyntheticDataConfig(
                UUID.randomUUID(), 200, 24, 42L,
                Map.of(MerchantArchetype.STEADY_GROWER, 1.0));
        List<SyntheticMerchant> ms = merchantGenerator.generate(cfg);
        OrderHistoryResult result  = orderGenerator.generate(ms, cfg);

        LocalDate cutoff = LocalDate.now().minusMonths(12);

        double earlyAvg = result.orders().stream()
                .filter(o -> o.orderDate().toLocalDate().isBefore(cutoff))
                .mapToDouble(o -> o.totalAmount().doubleValue())
                .average().orElse(0);

        double recentAvg = result.orders().stream()
                .filter(o -> o.orderDate().toLocalDate().isAfter(cutoff))
                .mapToDouble(o -> o.totalAmount().doubleValue())
                .average().orElse(0);

        // STEADY_GROWER grows +2%/month: after 12 months +26.8% expected
        assertThat(recentAvg)
                .as("Recent STEADY_GROWER orders should be higher than early orders")
                .isGreaterThan(earlyAvg);
    }

    @Test
    void generate_decliningRisk_orderValuesShouldDecreaseOverTime() {
        SyntheticDataConfig cfg = new SyntheticDataConfig(
                UUID.randomUUID(), 200, 24, 42L,
                Map.of(MerchantArchetype.DECLINING_RISK, 1.0));
        List<SyntheticMerchant> ms = merchantGenerator.generate(cfg);
        OrderHistoryResult result  = orderGenerator.generate(ms, cfg);

        LocalDate cutoff = LocalDate.now().minusMonths(12);

        double earlyAvg = result.orders().stream()
                .filter(o -> o.orderDate().toLocalDate().isBefore(cutoff))
                .mapToDouble(o -> o.totalAmount().doubleValue())
                .average().orElse(0);

        double recentAvg = result.orders().stream()
                .filter(o -> o.orderDate().toLocalDate().isAfter(cutoff))
                .mapToDouble(o -> o.totalAmount().doubleValue())
                .average().orElse(0);

        // DECLINING_RISK declines -3%/month: after 12 months -30% expected
        assertThat(recentAvg)
                .as("Recent DECLINING_RISK orders should be lower than early orders")
                .isLessThan(earlyAvg);
    }

    // -------------------------------------------------------------------------
    // Status distribution
    // -------------------------------------------------------------------------

    @Test
    void generate_mostOrdersShouldBeDelivered_givenHistoricalWindow() {
        List<SyntheticMerchant> ms = merchants(500, 42L);
        SyntheticDataConfig cfg    = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L);
        OrderHistoryResult result  = orderGenerator.generate(ms, cfg);

        long total     = result.orders().size();
        long delivered = result.orders().stream()
                .filter(o -> "DELIVERED".equals(o.status()))
                .count();

        // Historical generation: >90% should be DELIVERED
        assertThat((double) delivered / total).isGreaterThan(0.90);
    }

    // -------------------------------------------------------------------------
    // Determinism
    // -------------------------------------------------------------------------

    @Test
    void generate_sameSeedProducesSameOrderCount() {
        SyntheticDataConfig cfg = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 77L);
        List<SyntheticMerchant> ms1 = merchantGenerator.generate(cfg);
        List<SyntheticMerchant> ms2 = merchantGenerator.generate(cfg);

        OrderHistoryResult r1 = orderGenerator.generate(ms1, cfg);
        OrderHistoryResult r2 = orderGenerator.generate(ms2, cfg);

        assertThat(r1.orders().size()).isEqualTo(r2.orders().size());
        assertThat(r1.items().size()).isEqualTo(r2.items().size());
    }

    // -------------------------------------------------------------------------
    // New entrant — shorter history window
    // -------------------------------------------------------------------------

    @Test
    void generate_newEntrant_shouldHaveFewerOrdersThanSteadyGrower_perMerchant() {
        SyntheticDataConfig cfg = new SyntheticDataConfig(
                UUID.randomUUID(), 50, 12, 42L,
                Map.of(MerchantArchetype.NEW_ENTRANT,   0.5,
                       MerchantArchetype.STEADY_GROWER, 0.5));
        List<SyntheticMerchant> ms = merchantGenerator.generate(cfg);
        OrderHistoryResult result  = orderGenerator.generate(ms, cfg);

        double newEntrantAvgOrders = result.orders().stream()
                .filter(o -> o.merchantArchetype() == MerchantArchetype.NEW_ENTRANT)
                .collect(Collectors.groupingBy(SyntheticOrder::merchantRef, Collectors.counting()))
                .values().stream().mapToLong(Long::longValue).average().orElse(0);

        double steadyAvgOrders = result.orders().stream()
                .filter(o -> o.merchantArchetype() == MerchantArchetype.STEADY_GROWER)
                .collect(Collectors.groupingBy(SyntheticOrder::merchantRef, Collectors.counting()))
                .values().stream().mapToLong(Long::longValue).average().orElse(0);

        // NEW_ENTRANT: 0.8 orders/wk vs STEADY_GROWER: 2.5 orders/wk
        assertThat(newEntrantAvgOrders).isLessThan(steadyAvgOrders);
    }
}
