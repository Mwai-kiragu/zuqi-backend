package com.zuqi.ai.feature;

import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.StockMovement;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.user.User;
import com.zuqi.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryFeatureServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @InjectMocks
    private InventoryFeatureServiceImpl inventoryFeatureService;

    private UUID warehouseId;
    private UUID productId;
    private Warehouse warehouse;
    private Product product;
    private User user;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        productId = UUID.randomUUID();

        warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Main Warehouse")
                .code("WH001")
                .build();

        product = Product.builder()
                .id(productId)
                .name("Test Product")
                .sku("SKU123")
                .unitPrice(BigDecimal.valueOf(1000))
                .build();

        user = User.builder()
                .id(UUID.randomUUID())
                .email("testuser@example.com")
                .firstName("Test")
                .lastName("User")
                .password("password")
                .build();
    }

    @Test
    void shouldComputeBasicInventoryFeatures() {
        // Given
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(100), BigDecimal.valueOf(10));
        List<StockMovement> movements = createBasicMovements(asOfDate);

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(movements));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        assertThat(features).isNotNull();
        assertThat(features.warehouseId()).isEqualTo(warehouseId);
        assertThat(features.productId()).isEqualTo(productId);
        assertThat(features.currentStock()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(features.pendingReservedQty()).isEqualByComparingTo(BigDecimal.valueOf(10));
    }

    @Test
    void shouldComputeExpectedStockFromMovements() {
        // Given - 150 IN, 50 OUT = 100 expected
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(100), BigDecimal.ZERO);
        List<StockMovement> movements = new ArrayList<>();
        movements.add(createInMovement(BigDecimal.valueOf(150), asOfDate.minusDays(10)));
        movements.add(createOutMovement(BigDecimal.valueOf(50), asOfDate.minusDays(5)));

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(movements));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        assertThat(features.expectedStock()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(features.discrepancy()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(features.discrepancyPct()).isEqualTo(0.0);
    }

    @Test
    void shouldDetectShrinkage() {
        // Given - Expected 100 but actual is 80 (20 units shrinkage)
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(80), BigDecimal.ZERO);
        List<StockMovement> movements = new ArrayList<>();
        movements.add(createInMovement(BigDecimal.valueOf(100), asOfDate.minusDays(10)));

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(movements));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        assertThat(features.expectedStock()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(features.currentStock()).isEqualByComparingTo(BigDecimal.valueOf(80));
        assertThat(features.discrepancy()).isEqualByComparingTo(BigDecimal.valueOf(-20)); // Negative = shrinkage
        assertThat(features.discrepancyPct()).isEqualTo(-20.0);
    }

    @Test
    void shouldDetectSurplus() {
        // Given - Expected 100 but actual is 120 (20 units surplus)
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(120), BigDecimal.ZERO);
        List<StockMovement> movements = new ArrayList<>();
        movements.add(createInMovement(BigDecimal.valueOf(100), asOfDate.minusDays(10)));

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(movements));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        assertThat(features.discrepancy()).isEqualByComparingTo(BigDecimal.valueOf(20)); // Positive = surplus
        assertThat(features.discrepancyPct()).isEqualTo(20.0);
    }

    @Test
    void shouldCountManualAdjustments() {
        // Given - 3 manual adjustments in last 7 days
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(100), BigDecimal.ZERO);
        List<StockMovement> movements = new ArrayList<>();
        movements.add(createAdjustmentMovement(BigDecimal.valueOf(5), asOfDate.minusDays(2), user));
        movements.add(createAdjustmentMovement(BigDecimal.valueOf(-3), asOfDate.minusDays(4), user));
        movements.add(createAdjustmentMovement(BigDecimal.valueOf(2), asOfDate.minusDays(6), user));
        movements.add(createAdjustmentMovement(BigDecimal.valueOf(10), asOfDate.minusDays(10), user)); // Outside 7d window

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(movements));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        assertThat(features.manualAdjustmentCount7d()).isEqualTo(3);
    }

    @Test
    void shouldComputeAdjustmentTimeDistribution() {
        // Given - Adjustments at different hours
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(100), BigDecimal.ZERO);
        List<StockMovement> movements = new ArrayList<>();
        movements.add(createAdjustmentMovement(BigDecimal.valueOf(5), asOfDate.minusDays(1).withHour(9), user));
        movements.add(createAdjustmentMovement(BigDecimal.valueOf(3), asOfDate.minusDays(2).withHour(9), user));
        movements.add(createAdjustmentMovement(BigDecimal.valueOf(2), asOfDate.minusDays(3).withHour(14), user));

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(movements));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        Map<String, Integer> distribution = features.adjustmentTimeDistribution();
        assertThat(distribution).containsEntry("09:00", 2);
        assertThat(distribution).containsEntry("14:00", 1);
    }

    @Test
    void shouldIdentifyAdjustingUsers() {
        // Given - Adjustments by 2 different users
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(100), BigDecimal.ZERO);

        User user1 = User.builder().id(UUID.randomUUID()).email("user1@example.com").firstName("User").lastName("One").password("pass").build();
        User user2 = User.builder().id(UUID.randomUUID()).email("user2@example.com").firstName("User").lastName("Two").password("pass").build();

        List<StockMovement> movements = new ArrayList<>();
        movements.add(createAdjustmentMovement(BigDecimal.valueOf(5), asOfDate.minusDays(1), user1));
        movements.add(createAdjustmentMovement(BigDecimal.valueOf(3), asOfDate.minusDays(2), user2));
        movements.add(createAdjustmentMovement(BigDecimal.valueOf(2), asOfDate.minusDays(3), user1));

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(movements));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        List<UUID> adjustingUsers = features.adjustingUserIds();
        assertThat(adjustingUsers).hasSize(2);
        assertThat(adjustingUsers).contains(user1.getId(), user2.getId());
    }

    @Test
    void shouldComputeConsumptionRate7d() {
        // Given - 70 units out in last 7 days = 10 per day
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(100), BigDecimal.ZERO);
        List<StockMovement> movements = new ArrayList<>();
        movements.add(createInMovement(BigDecimal.valueOf(200), asOfDate.minusDays(30)));
        movements.add(createOutMovement(BigDecimal.valueOf(70), asOfDate.minusDays(3)));

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(movements));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        assertThat(features.consumptionRate7d()).isEqualByComparingTo(BigDecimal.valueOf(10));
    }

    @Test
    void shouldComputeConsumptionRate30d() {
        // Given - 150 units out in last 30 days = 5 per day
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(100), BigDecimal.ZERO);
        List<StockMovement> movements = new ArrayList<>();
        movements.add(createInMovement(BigDecimal.valueOf(300), asOfDate.minusDays(45)));
        movements.add(createOutMovement(BigDecimal.valueOf(150), asOfDate.minusDays(15)));

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(movements));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        assertThat(features.consumptionRate30d()).isEqualByComparingTo(BigDecimal.valueOf(5));
    }

    @Test
    void shouldDetectIncreasingConsumptionTrend() {
        // Given - 7d rate (10/day) > 30d rate (5/day)
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(100), BigDecimal.ZERO);
        List<StockMovement> movements = new ArrayList<>();
        movements.add(createInMovement(BigDecimal.valueOf(500), asOfDate.minusDays(40)));
        movements.add(createOutMovement(BigDecimal.valueOf(70), asOfDate.minusDays(3))); // 10/day in last 7d
        movements.add(createOutMovement(BigDecimal.valueOf(80), asOfDate.minusDays(15))); // Low consumption earlier

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(movements));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        assertThat(features.consumptionTrend()).isIn("INCREASING", "STABLE");
    }

    @Test
    void shouldDetectStableConsumptionTrend() {
        // Given - 7d and 30d rates similar
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(100), BigDecimal.ZERO);
        List<StockMovement> movements = new ArrayList<>();
        movements.add(createInMovement(BigDecimal.valueOf(500), asOfDate.minusDays(40)));
        movements.add(createOutMovement(BigDecimal.valueOf(35), asOfDate.minusDays(3))); // 5/day
        movements.add(createOutMovement(BigDecimal.valueOf(115), asOfDate.minusDays(20))); // 5/day overall

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(movements));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        assertThat(features.consumptionTrend()).isEqualTo("STABLE");
    }

    @Test
    void shouldComputeExpectedIncoming() {
        // Given - PURCHASE movements in last 7 days
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(100), BigDecimal.ZERO);
        List<StockMovement> movements = new ArrayList<>();
        movements.add(createPurchaseMovement(BigDecimal.valueOf(50), asOfDate.minusDays(2)));
        movements.add(createPurchaseMovement(BigDecimal.valueOf(30), asOfDate.minusDays(5)));
        movements.add(createPurchaseMovement(BigDecimal.valueOf(20), asOfDate.minusDays(10))); // Outside 7d window

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(movements));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        assertThat(features.expectedIncomingQty()).isEqualByComparingTo(BigDecimal.valueOf(80));
    }

    @Test
    void shouldHandleNoStockRecord() {
        // Given - No stock record exists
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.empty());
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        assertThat(features.currentStock()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(features.pendingReservedQty()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldHandleNoMovements() {
        // Given - No movements
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        Stock stock = createStock(BigDecimal.valueOf(100), BigDecimal.ZERO);

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)).thenReturn(Optional.of(stock));
        when(stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(
                eq(warehouseId), eq(productId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // When
        InventoryFeatures features = inventoryFeatureService.computeFeatures(warehouseId, productId, asOfDate);

        // Then
        assertThat(features.expectedStock()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(features.manualAdjustmentCount7d()).isEqualTo(0);
        assertThat(features.consumptionRate7d()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(features.consumptionTrend()).isEqualTo("STABLE");
    }

    @Test
    void shouldThrowException_WhenWarehouseNotFound() {
        // Given
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> inventoryFeatureService.computeFeatures(warehouseId, productId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Warehouse not found");
    }

    @Test
    void shouldThrowException_WhenProductNotFound() {
        // Given
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> inventoryFeatureService.computeFeatures(warehouseId, productId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void shouldEvictCache() {
        // When
        inventoryFeatureService.evictCache(warehouseId, productId);

        // Then - no exception
        verify(warehouseRepository, never()).findById(any());
    }

    @Test
    void shouldEvictWarehouseCache() {
        // When
        inventoryFeatureService.evictWarehouseCache(warehouseId);

        // Then - no exception
        verify(warehouseRepository, never()).findById(any());
    }

    // ==================== Helper Methods ====================

    private Stock createStock(BigDecimal quantity, BigDecimal reservedQty) {
        return Stock.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .product(product)
                .quantity(quantity)
                .reservedQuantity(reservedQty)
                .build();
    }

    private List<StockMovement> createBasicMovements(LocalDateTime asOfDate) {
        List<StockMovement> movements = new ArrayList<>();
        movements.add(createInMovement(BigDecimal.valueOf(100), asOfDate.minusDays(10)));
        movements.add(createOutMovement(BigDecimal.valueOf(20), asOfDate.minusDays(5)));
        return movements;
    }

    private StockMovement createInMovement(BigDecimal quantity, LocalDateTime createdAt) {
        return StockMovement.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .product(product)
                .movementType(StockMovement.MovementType.IN)
                .quantity(quantity)
                .referenceType("PURCHASE")
                .createdAt(createdAt)
                .build();
    }

    private StockMovement createOutMovement(BigDecimal quantity, LocalDateTime createdAt) {
        return StockMovement.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .product(product)
                .movementType(StockMovement.MovementType.OUT)
                .quantity(quantity)
                .referenceType("ORDER")
                .createdAt(createdAt)
                .build();
    }

    private StockMovement createAdjustmentMovement(BigDecimal quantity, LocalDateTime createdAt, User createdBy) {
        return StockMovement.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .product(product)
                .movementType(StockMovement.MovementType.ADJUSTMENT)
                .quantity(quantity)
                .referenceType("ADJUSTMENT")
                .createdBy(createdBy)
                .createdAt(createdAt)
                .build();
    }

    private StockMovement createPurchaseMovement(BigDecimal quantity, LocalDateTime createdAt) {
        return StockMovement.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .product(product)
                .movementType(StockMovement.MovementType.IN)
                .quantity(quantity)
                .referenceType("PURCHASE")
                .createdAt(createdAt)
                .build();
    }
}
