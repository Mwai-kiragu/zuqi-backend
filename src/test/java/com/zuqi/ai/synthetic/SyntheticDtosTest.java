package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that all synthetic DTOs are correctly structured and that
 * SyntheticDataBundle cross-references are navigable.
 */
class SyntheticDtosTest {

    // -------------------------------------------------------------------------
    // SyntheticDataConfig
    // -------------------------------------------------------------------------

    @Test
    void syntheticDataConfig_defaultConfig_shouldHaveCorrectValues() {
        UUID distributorId = UUID.randomUUID();
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(distributorId, 42L);

        assertThat(config.distributorId()).isEqualTo(distributorId);
        assertThat(config.merchantCount()).isEqualTo(500);
        assertThat(config.historyMonths()).isEqualTo(12);
        assertThat(config.randomSeed()).isEqualTo(42L);
        assertThat(config.archetypeRatios()).containsKey(MerchantArchetype.STEADY_GROWER);
    }

    @Test
    void syntheticDataConfig_archetypeRatios_shouldSumToOne() {
        double sum = SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS.values()
                .stream().mapToDouble(Double::doubleValue).sum();
        assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void syntheticDataConfig_shouldThrow_whenRatiosDoNotSumToOne() {
        UUID distributorId = UUID.randomUUID();
        Map<MerchantArchetype, Double> badRatios = Map.of(
                MerchantArchetype.STEADY_GROWER, 0.5,
                MerchantArchetype.DEFAULTER,     0.1   // sum = 0.6, not 1.0
        );

        assertThatThrownBy(() -> new SyntheticDataConfig(distributorId, 100, 12, 1L, badRatios))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 1.0");
    }

    @Test
    void syntheticDataConfig_shouldThrow_whenMerchantCountIsZero() {
        assertThatThrownBy(() -> new SyntheticDataConfig(null, 0, 12, 1L,
                SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("merchantCount");
    }

    // -------------------------------------------------------------------------
    // Individual DTO field checks
    // -------------------------------------------------------------------------

    @Test
    void syntheticMerchant_shouldHoldAllRequiredFields() {
        UUID id = UUID.randomUUID();
        SyntheticMerchant m = new SyntheticMerchant(
                id, "Wanjiku Stores", "retail",
                "Nairobi", "Westlands",
                -1.2635, 36.8030,
                LocalDate.of(2023, 1, 15),
                BigDecimal.valueOf(80_000),
                MerchantArchetype.STEADY_GROWER);

        assertThat(m.syntheticId()).isEqualTo(id);
        assertThat(m.businessName()).isEqualTo("Wanjiku Stores");
        assertThat(m.county()).isEqualTo("Nairobi");
        assertThat(m.merchantArchetype()).isEqualTo(MerchantArchetype.STEADY_GROWER);
        assertThat(m.initialCreditLimit()).isEqualByComparingTo("80000");
    }

    @Test
    void syntheticOrder_shouldCarryMerchantReference() {
        UUID orderId    = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        SyntheticOrder o = new SyntheticOrder(
                orderId, merchantId, UUID.randomUUID(),
                LocalDateTime.now(), BigDecimal.valueOf(15_000),
                "DELIVERED", MerchantArchetype.STABLE_PERFORMER);

        assertThat(o.syntheticId()).isEqualTo(orderId);
        assertThat(o.merchantRef()).isEqualTo(merchantId);
        assertThat(o.merchantArchetype()).isEqualTo(MerchantArchetype.STABLE_PERFORMER);
    }

    @Test
    void syntheticOrderItem_shouldReferenceItsOrder() {
        UUID orderId = UUID.randomUUID();
        UUID skuId   = UUID.randomUUID();

        SyntheticOrderItem item = new SyntheticOrderItem(
                orderId, skuId,
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(5_000));

        assertThat(item.orderRef()).isEqualTo(orderId);
        assertThat(item.lineTotal()).isEqualByComparingTo("5000");
    }

    @Test
    void syntheticPayment_flagsShouldBeAccessible() {
        UUID paymentId  = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        UUID orderId    = UUID.randomUUID();

        SyntheticPayment p = new SyntheticPayment(
                paymentId, orderId, merchantId,
                BigDecimal.valueOf(5_000),
                LocalDateTime.now(), "MPESA", 7, true, false);

        assertThat(p.isPartial()).isTrue();
        assertThat(p.isDefault()).isFalse();
        assertThat(p.daysAfterInvoice()).isEqualTo(7);
        assertThat(p.merchantRef()).isEqualTo(merchantId);
        assertThat(p.invoiceRef()).isEqualTo(orderId);
    }

    @Test
    void syntheticInventoryMovement_shrinkageFlagsShouldBeAccessible() {
        SyntheticInventoryMovement m = new SyntheticInventoryMovement(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ADJUSTMENT", BigDecimal.valueOf(5),
                BigDecimal.valueOf(100), BigDecimal.valueOf(95),
                LocalDateTime.now(), UUID.randomUUID(),
                true, "GRADUAL");

        assertThat(m.isShrinkage()).isTrue();
        assertThat(m.shrinkagePattern()).isEqualTo("GRADUAL");
    }

    @Test
    void syntheticInventoryMovement_normalMovement_shouldHaveNullShrinkagePattern() {
        SyntheticInventoryMovement m = new SyntheticInventoryMovement(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "OUT", BigDecimal.valueOf(20),
                BigDecimal.valueOf(200), BigDecimal.valueOf(180),
                LocalDateTime.now(), UUID.randomUUID(),
                false, null);

        assertThat(m.isShrinkage()).isFalse();
        assertThat(m.shrinkagePattern()).isNull();
    }

    @Test
    void syntheticRepActivity_shouldExposeUnderperformingFlag() {
        SyntheticRepActivity a = new SyntheticRepActivity(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), false, BigDecimal.ZERO, 15, true);

        assertThat(a.orderPlaced()).isFalse();
        assertThat(a.isUnderperforming()).isTrue();
        assertThat(a.visitDurationMinutes()).isEqualTo(15);
    }

    @Test
    void syntheticCreditEvaluation_defaultedWithDaysToDefault() {
        UUID merchantId = UUID.randomUUID();
        SyntheticCreditEvaluation e = new SyntheticCreditEvaluation(
                UUID.randomUUID(), merchantId,
                LocalDate.of(2024, 3, 1), "D",
                BigDecimal.valueOf(30_000), true, 45);

        assertThat(e.defaulted()).isTrue();
        assertThat(e.daysToDefault()).isEqualTo(45);
        assertThat(e.grade()).isEqualTo("D");
        assertThat(e.merchantRef()).isEqualTo(merchantId);
    }

    @Test
    void syntheticCreditEvaluation_nonDefaulted_shouldHaveNullDaysToDefault() {
        SyntheticCreditEvaluation e = new SyntheticCreditEvaluation(
                UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), "A",
                BigDecimal.valueOf(150_000), false, null);

        assertThat(e.defaulted()).isFalse();
        assertThat(e.daysToDefault()).isNull();
    }

    // -------------------------------------------------------------------------
    // SyntheticDataBundle — cross-reference navigability
    // -------------------------------------------------------------------------

    @Test
    void syntheticDataBundle_crossReferences_shouldBeNavigable() {
        UUID merchantId = UUID.randomUUID();
        UUID orderId1   = UUID.randomUUID();
        UUID orderId2   = UUID.randomUUID();

        SyntheticMerchant merchant = new SyntheticMerchant(
                merchantId, "Kamau Wholesale", "wholesale",
                "Kiambu", "Thika", -1.033, 37.069,
                LocalDate.of(2022, 6, 1), BigDecimal.valueOf(200_000),
                MerchantArchetype.STEADY_GROWER);

        SyntheticOrder order1 = new SyntheticOrder(
                orderId1, merchantId, UUID.randomUUID(),
                LocalDateTime.now().minusDays(30), BigDecimal.valueOf(20_000),
                "DELIVERED", MerchantArchetype.STEADY_GROWER);

        SyntheticOrder order2 = new SyntheticOrder(
                orderId2, merchantId, UUID.randomUUID(),
                LocalDateTime.now().minusDays(10), BigDecimal.valueOf(15_000),
                "DELIVERED", MerchantArchetype.STEADY_GROWER);

        SyntheticOrderItem item1 = new SyntheticOrderItem(
                orderId1, UUID.randomUUID(), BigDecimal.TEN,
                BigDecimal.valueOf(2_000), BigDecimal.valueOf(20_000));

        SyntheticPayment payment1 = new SyntheticPayment(
                UUID.randomUUID(), orderId1, merchantId,
                BigDecimal.valueOf(20_000), LocalDateTime.now().minusDays(25),
                "MPESA", 5, false, false);

        SyntheticPayment payment2 = new SyntheticPayment(
                UUID.randomUUID(), orderId2, merchantId,
                BigDecimal.valueOf(15_000), LocalDateTime.now().minusDays(5),
                "CASH", 5, false, false);

        SyntheticRepActivity activity = new SyntheticRepActivity(
                UUID.randomUUID(), UUID.randomUUID(), merchantId,
                LocalDate.now().minusDays(10), true,
                BigDecimal.valueOf(15_000), 30, false);

        SyntheticCreditEvaluation credit = new SyntheticCreditEvaluation(
                UUID.randomUUID(), merchantId,
                LocalDate.now().minusDays(60), "A",
                BigDecimal.valueOf(200_000), false, null);

        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 99L);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(merchant),
                List.of(order1, order2),
                List.of(item1),
                List.of(payment1, payment2),
                List.of(),
                List.of(activity),
                List.of(credit),
                List.of(),
                99L, config);

        // Orders for merchant
        assertThat(bundle.getOrdersForMerchant(merchantId)).hasSize(2);

        // Items for order
        assertThat(bundle.getItemsForOrder(orderId1)).hasSize(1);
        assertThat(bundle.getItemsForOrder(orderId2)).isEmpty();

        // Payments by order and by merchant
        assertThat(bundle.getPaymentsForOrder(orderId1)).hasSize(1);
        assertThat(bundle.getPaymentsForMerchant(merchantId)).hasSize(2);

        // Activities and credit history
        assertThat(bundle.getActivitiesForMerchant(merchantId)).hasSize(1);
        assertThat(bundle.getCreditHistoryForMerchant(merchantId)).hasSize(1);

        // Unknown merchant returns empty
        assertThat(bundle.getOrdersForMerchant(UUID.randomUUID())).isEmpty();
    }

    @Test
    void syntheticDataBundle_recordCounts_shouldMatchInputLists() {
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(null, 0L);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(buildMerchant(), buildMerchant()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0L, config);

        assertThat(bundle.getRecordCounts().get("merchants")).isEqualTo(2);
        assertThat(bundle.getRecordCounts().get("orders")).isEqualTo(0);
    }

    @Test
    void syntheticDataBundle_metadata_shouldBeSet() {
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(null, 77L);

        SyntheticDataBundle bundle = SyntheticDataBundle.create(
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), 77L, config);

        assertThat(bundle.getGenerationSeed()).isEqualTo(77L);
        assertThat(bundle.getConfig()).isSameAs(config);
        assertThat(bundle.getGeneratedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private SyntheticMerchant buildMerchant() {
        return new SyntheticMerchant(
                UUID.randomUUID(), "Test Shop", "retail",
                "Nairobi", "CBD", -1.286, 36.817,
                LocalDate.now().minusYears(1), BigDecimal.valueOf(50_000),
                MerchantArchetype.STABLE_PERFORMER);
    }
}
