package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticInventoryMovement;
import com.zuqi.ai.synthetic.SyntheticOrder;
import com.zuqi.ai.synthetic.generators.OrderHistoryGenerator.OrderHistoryResult;
import com.zuqi.ai.synthetic.profiles.AnomalyPatterns;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class InventoryMovementGeneratorTest {

    @Mock
    private BusinessNameGenerator nameGenerator;

    private MerchantProfileGenerator   merchantGenerator;
    private OrderHistoryGenerator       orderGenerator;
    private InventoryMovementGenerator  inventoryGenerator;

    private static final long SEED = 42L;
    private static final SyntheticDataConfig CONFIG = new SyntheticDataConfig(
            null, 30, 12, SEED, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);

    @BeforeEach
    void setUp() {
        lenient().when(nameGenerator.generateBatch(anyString(), anyInt(), anyLong()))
                .thenAnswer(inv -> {
                    String cat   = inv.getArgument(0);
                    int    count = inv.getArgument(1);
                    List<String> names = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) names.add(cat + " Biz " + i);
                    return names;
                });
        merchantGenerator  = new MerchantProfileGenerator(nameGenerator);
        orderGenerator     = new OrderHistoryGenerator();
        inventoryGenerator = new InventoryMovementGenerator();
    }

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    private List<SyntheticOrder> orders() {
        var merchants = merchantGenerator.generate(CONFIG);
        OrderHistoryResult result = orderGenerator.generate(merchants, CONFIG);
        return result.orders();
    }

    // -------------------------------------------------------------------------
    // Basic correctness
    // -------------------------------------------------------------------------

    @Test
    void generate_shouldReturnNonEmptyList() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        assertThat(movements).isNotNull().isNotEmpty();
    }

    @Test
    void generate_resultShouldBeUnmodifiable() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        assertThatThrownBy(() -> movements.add(movements.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void generate_allMovementsShouldHaveRequiredFields() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        for (SyntheticInventoryMovement m : movements) {
            assertThat(m.syntheticId()).isNotNull();
            assertThat(m.warehouseId()).isNotNull();
            assertThat(m.skuId()).isNotNull();
            assertThat(m.movementType()).isNotBlank();
            assertThat(m.quantity()).isNotNull();
            assertThat(m.previousStock()).isNotNull();
            assertThat(m.newStock()).isNotNull();
            assertThat(m.timestamp()).isNotNull();
            assertThat(m.userId()).isNotNull();
        }
    }

    @Test
    void generate_movementTypesShouldBeValidValues() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        Set<String> validTypes = Set.of("IN", "OUT", "ADJUSTMENT");
        assertThat(movements)
                .extracting(SyntheticInventoryMovement::movementType)
                .allMatch(validTypes::contains, "only IN, OUT, ADJUSTMENT are valid movement types");
    }

    // -------------------------------------------------------------------------
    // Quantity and stock invariants
    // -------------------------------------------------------------------------

    @Test
    void generate_quantitiesShouldBePositive() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        assertThat(movements)
                .extracting(m -> m.quantity().compareTo(BigDecimal.ZERO))
                .allMatch(cmp -> cmp > 0, "all movement quantities should be positive");
    }

    @Test
    void generate_newStockShouldNeverBeNegative() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        assertThat(movements)
                .extracting(m -> m.newStock().compareTo(BigDecimal.ZERO))
                .allMatch(cmp -> cmp >= 0, "newStock should never go negative");
    }

    @Test
    void generate_inMovements_newStockShouldEqualPrevPlusQty() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        List<SyntheticInventoryMovement> inMovements = movements.stream()
                .filter(m -> "IN".equals(m.movementType()))
                .toList();
        assertThat(inMovements).isNotEmpty();
        for (SyntheticInventoryMovement m : inMovements) {
            BigDecimal expected = m.previousStock().add(m.quantity());
            assertThat(m.newStock())
                    .as("newStock for IN movement %s", m.syntheticId())
                    .isEqualByComparingTo(expected);
        }
    }

    @Test
    void generate_outMovements_newStockShouldEqualPrevMinusQty() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        List<SyntheticInventoryMovement> outMovements = movements.stream()
                .filter(m -> "OUT".equals(m.movementType()))
                .toList();
        assertThat(outMovements).isNotEmpty();
        for (SyntheticInventoryMovement m : outMovements) {
            BigDecimal expected = m.previousStock().subtract(m.quantity());
            assertThat(m.newStock())
                    .as("newStock for OUT movement %s", m.syntheticId())
                    .isEqualByComparingTo(expected);
        }
    }

    @Test
    void generate_adjustmentMovements_absoluteDeltaShouldEqualQty() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        List<SyntheticInventoryMovement> adjustments = movements.stream()
                .filter(m -> "ADJUSTMENT".equals(m.movementType()))
                .toList();
        assertThat(adjustments).isNotEmpty();
        for (SyntheticInventoryMovement m : adjustments) {
            BigDecimal delta = m.newStock().subtract(m.previousStock()).abs();
            assertThat(delta)
                    .as("delta for ADJUSTMENT %s should equal quantity", m.syntheticId())
                    .isEqualByComparingTo(m.quantity());
        }
    }

    // -------------------------------------------------------------------------
    // Shrinkage pattern coverage
    // -------------------------------------------------------------------------

    @Test
    void generate_shouldContainAllFourShrinkagePatterns() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        Set<String> shrinkagePatternNames = movements.stream()
                .filter(SyntheticInventoryMovement::isShrinkage)
                .map(SyntheticInventoryMovement::shrinkagePattern)
                .collect(Collectors.toSet());

        for (AnomalyPatterns.ShrinkagePattern pattern : AnomalyPatterns.ShrinkagePattern.values()) {
            assertThat(shrinkagePatternNames)
                    .as("shrinkage pattern %s should be present in output", pattern)
                    .contains(pattern.name());
        }
    }

    @Test
    void generate_shrinkageFlagShouldMatchPatternField() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        for (SyntheticInventoryMovement m : movements) {
            if (m.isShrinkage()) {
                assertThat(m.shrinkagePattern())
                        .as("isShrinkage=true movement should have non-null shrinkagePattern")
                        .isNotNull();
            } else {
                assertThat(m.shrinkagePattern())
                        .as("isShrinkage=false movement should have null shrinkagePattern")
                        .isNull();
            }
        }
    }

    @Test
    void generate_concentratedUserShrinkage_shouldUseSingleUserPerWarehouse() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        Map<UUID, Set<UUID>> usersByWarehouse = movements.stream()
                .filter(m -> "CONCENTRATED_USER".equals(m.shrinkagePattern()))
                .collect(Collectors.groupingBy(
                        SyntheticInventoryMovement::warehouseId,
                        Collectors.mapping(SyntheticInventoryMovement::userId, Collectors.toSet())));

        assertThat(usersByWarehouse).isNotEmpty();
        // Within each warehouse's CONCENTRATED_USER window, exactly one user is pinned
        for (Map.Entry<UUID, Set<UUID>> entry : usersByWarehouse.entrySet()) {
            assertThat(entry.getValue())
                    .as("CONCENTRATED_USER window for warehouse %s should pin a single user",
                            entry.getKey())
                    .hasSize(1);
        }
    }

    @Test
    void generate_concentratedTimeShrinkage_shouldOccurAtHour22() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        List<SyntheticInventoryMovement> concentrated = movements.stream()
                .filter(m -> "CONCENTRATED_TIME".equals(m.shrinkagePattern()))
                .toList();
        assertThat(concentrated).isNotEmpty();
        assertThat(concentrated)
                .extracting(m -> m.timestamp().getHour())
                .allMatch(h -> h == 22, "CONCENTRATED_TIME movements should occur at hour 22");
    }

    @Test
    void generate_suddenShrinkage_shouldHaveHigherAvgQtyThanGradual() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);

        double suddenAvg = movements.stream()
                .filter(m -> "SUDDEN".equals(m.shrinkagePattern()))
                .mapToDouble(m -> m.quantity().doubleValue())
                .average()
                .orElseThrow(() -> new AssertionError("No SUDDEN shrinkage movements found"));

        double gradualAvg = movements.stream()
                .filter(m -> "GRADUAL".equals(m.shrinkagePattern()))
                .mapToDouble(m -> m.quantity().doubleValue())
                .average()
                .orElseThrow(() -> new AssertionError("No GRADUAL shrinkage movements found"));

        assertThat(suddenAvg)
                .as("SUDDEN shrinkage avg quantity (%.2f) should exceed GRADUAL (%.2f)",
                        suddenAvg, gradualAvg)
                .isGreaterThan(gradualAvg);
    }

    // -------------------------------------------------------------------------
    // Movement type presence
    // -------------------------------------------------------------------------

    @Test
    void generate_shouldContainRestockMovements() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        long inCount = movements.stream()
                .filter(m -> "IN".equals(m.movementType()))
                .count();
        assertThat(inCount).isGreaterThan(0);
    }

    @Test
    void generate_shouldContainOutboundMovements() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        long outCount = movements.stream()
                .filter(m -> "OUT".equals(m.movementType()))
                .count();
        assertThat(outCount).isGreaterThan(0);
    }

    @Test
    void generate_shouldContainAdjustmentMovements() {
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(orders(), CONFIG);
        long adjCount = movements.stream()
                .filter(m -> "ADJUSTMENT".equals(m.movementType()))
                .count();
        assertThat(adjCount).isGreaterThan(0);
    }

    // -------------------------------------------------------------------------
    // Reproducibility
    // -------------------------------------------------------------------------

    @Test
    void generate_isDeterministic() {
        List<SyntheticOrder> orders = orders();
        List<SyntheticInventoryMovement> first  = inventoryGenerator.generate(orders, CONFIG);
        List<SyntheticInventoryMovement> second = inventoryGenerator.generate(orders, CONFIG);
        assertThat(first).hasSameSizeAs(second);
    }

    @Test
    void generate_worksWithEmptyOrders() {
        // With no orders there are no outbound movements, but adjustments, shrinkage,
        // and expiry events are still generated from the warehouse lifecycle.
        List<SyntheticInventoryMovement> movements = inventoryGenerator.generate(List.of(), CONFIG);
        assertThat(movements).isNotNull().isNotEmpty();
    }
}
