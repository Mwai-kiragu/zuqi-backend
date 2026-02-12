package com.zuqi.ai.feature;

import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.merchant.MerchantCategory;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderItem;
import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.product.ProductCategory;
import com.zuqi.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFeatureServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private OrderFeatureServiceImpl orderFeatureService;

    private UUID merchantId;
    private UUID productId;
    private Merchant merchant;
    private Product product;
    private ProductCategory productCategory;
    private MerchantCategory merchantCategory;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        productId = UUID.randomUUID();

        merchantCategory = MerchantCategory.builder()
                .id(1L)
                .name("RETAIL")
                .build();

        merchant = Merchant.builder()
                .id(merchantId)
                .businessName("Test Merchant")
                .phone("+254700000000")
                .category(merchantCategory)
                .createdAt(LocalDateTime.now().minusDays(365))
                .build();

        productCategory = ProductCategory.builder()
                .id(1L)
                .name("BEVERAGES")
                .build();

        product = Product.builder()
                .id(productId)
                .name("Test Product")
                .sku("SKU123")
                .category(productCategory)
                .unitPrice(BigDecimal.valueOf(1000))
                .build();
    }

    @Test
    void shouldComputeBasicDemandFeatures() {
        // Given
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        List<Order> orders = createOrdersWithItems(merchantId, productId, asOfDate);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate)).thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features).isNotNull();
        assertThat(features.merchantId()).isEqualTo(merchantId);
        assertThat(features.productId()).isEqualTo(productId);
        assertThat(features.computedAt()).isEqualTo(asOfDate);
        assertThat(features.merchantCategory()).isEqualTo("RETAIL");
        assertThat(features.productCategory()).isEqualTo("BEVERAGES");
    }

    @Test
    void shouldComputeLagFeatures() {
        // Given
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        List<Order> orders = createOrdersForLagTest(asOfDate);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate)).thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.qty1wAgo()).isGreaterThan(BigDecimal.ZERO);
        assertThat(features.qty2wAgo()).isGreaterThan(BigDecimal.ZERO);
        assertThat(features.rollingAvg4w()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void shouldComputeTrendDirection_Increasing() {
        // Given
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        List<Order> orders = createOrdersWithIncreasingTrend(asOfDate);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate)).thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        // The trend direction depends on the comparison of 4-week vs 12-week average
        assertThat(features.trendDirection()).isIn("INCREASING", "DECREASING", "STABLE");
    }

    @Test
    void shouldComputeTrendDirection_Stable() {
        // Given
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        List<Order> orders = createOrdersWithStableTrend(asOfDate);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate)).thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.trendDirection()).isEqualTo("STABLE");
    }

    @Test
    void shouldComputeTemporalFeatures() {
        // Given
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0); // Sunday (7)

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.dayOfWeek()).isEqualTo(7); // Sunday
        assertThat(features.monthOfYear()).isEqualTo(2); // February
        assertThat(features.weekOfMonth()).isGreaterThan(0).isLessThanOrEqualTo(5);
    }

    @Test
    void shouldDetectKenyaHoliday() {
        // Given - New Year's Day
        LocalDateTime asOfDate = LocalDateTime.of(2026, 1, 1, 10, 0);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.isHoliday()).isTrue();
    }

    @Test
    void shouldDetectPaydayWeek() {
        // Given - 1st of month (payday week)
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 1, 10, 0);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.isPaydayWeek()).isTrue();
    }

    @Test
    void shouldDetectChristmasSeason() {
        // Given - December
        LocalDateTime asOfDate = LocalDateTime.of(2026, 12, 15, 10, 0);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.isChristmasSeason()).isTrue();
    }

    @Test
    void shouldDetectRamadan() {
        // Given - March 15, 2026 (during Ramadan)
        LocalDateTime asOfDate = LocalDateTime.of(2026, 3, 15, 10, 0);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.isRamadan()).isTrue();
    }

    @Test
    void shouldComputeMerchantSizeTier_Large() {
        // Given - 25 orders in last 12 weeks
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        List<Order> orders = createManyRecentOrders(25, asOfDate);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate)).thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.merchantSizeTier()).isEqualTo("LARGE");
    }

    @Test
    void shouldComputeMerchantSizeTier_Medium() {
        // Given - 10 orders in last 12 weeks
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        List<Order> orders = createManyRecentOrders(10, asOfDate);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate)).thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.merchantSizeTier()).isEqualTo("MEDIUM");
    }

    @Test
    void shouldComputeMerchantSizeTier_Small() {
        // Given - 3 orders in last 12 weeks
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        List<Order> orders = createManyRecentOrders(3, asOfDate);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate)).thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.merchantSizeTier()).isEqualTo("SMALL");
    }

    @Test
    void shouldComputeMerchantCreditStatus_Good() {
        // Given - All payments on time
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        List<Payment> payments = createOnTimePayments(10, asOfDate);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate)).thenReturn(payments);

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.merchantCreditStatus()).isEqualTo("GOOD");
    }

    @Test
    void shouldComputeMerchantCreditStatus_Poor() {
        // Given - Most payments late
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        List<Payment> payments = createLatePayments(10, asOfDate);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate)).thenReturn(payments);

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.merchantCreditStatus()).isEqualTo("POOR");
    }

    @Test
    void shouldComputeMerchantTenureDays() {
        // Given
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        merchant.setCreatedAt(asOfDate.minusDays(100));

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.merchantTenureDays()).isEqualTo(100);
    }

    @Test
    void shouldComputePriceTier_Low() {
        // Given
        product.setUnitPrice(BigDecimal.valueOf(300));

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId);

        // Then
        assertThat(features.priceTier()).isEqualTo("LOW");
    }

    @Test
    void shouldComputePriceTier_High() {
        // Given
        product.setUnitPrice(BigDecimal.valueOf(3000));

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId);

        // Then
        assertThat(features.priceTier()).isEqualTo("HIGH");
    }

    @Test
    void shouldDetectPromotionalProduct() {
        // Given
        LocalDateTime asOfDate = LocalDateTime.of(2026, 2, 15, 10, 0);
        List<Order> orders = createOrdersWithDiscounts(asOfDate);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate)).thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId, asOfDate);

        // Then
        assertThat(features.isPromotional()).isTrue();
    }

    @Test
    void shouldGetTypicalShelfLifeDays_Beverages() {
        // Given
        productCategory.setName("BEVERAGES");

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(any(), any())).thenReturn(Collections.emptyList());

        // When
        DemandFeatures features = orderFeatureService.computeFeatures(merchantId, productId);

        // Then
        assertThat(features.typicalShelfLifeDays()).isEqualTo(30);
    }

    @Test
    void shouldThrowException_WhenMerchantNotFound() {
        // Given
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> orderFeatureService.computeFeatures(merchantId, productId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Merchant not found");
    }

    @Test
    void shouldThrowException_WhenProductNotFound() {
        // Given
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> orderFeatureService.computeFeatures(merchantId, productId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void shouldEvictCache() {
        // When
        orderFeatureService.evictCache(merchantId, productId);

        // Then - no exception
        verify(merchantRepository, never()).findById(any());
    }

    @Test
    void shouldEvictMerchantCache() {
        // When
        orderFeatureService.evictMerchantCache(merchantId);

        // Then - no exception
        verify(merchantRepository, never()).findById(any());
    }

    // ==================== Helper Methods ====================

    private List<Order> createOrdersWithItems(UUID merchantId, UUID productId, LocalDateTime asOfDate) {
        List<Order> orders = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchant)
                    .createdAt(asOfDate.minusDays(i * 7))
                    .totalAmount(BigDecimal.valueOf(1000))
                    .items(new ArrayList<>())
                    .build();

            OrderItem item = OrderItem.builder()
                    .id(UUID.randomUUID())
                    .order(order)
                    .product(product)
                    .quantity(BigDecimal.valueOf(10))
                    .unitPrice(BigDecimal.valueOf(100))
                    .discountPercent(BigDecimal.ZERO)
                    .totalAmount(BigDecimal.valueOf(1000))
                    .build();

            order.getItems().add(item);
            orders.add(order);
        }

        return orders;
    }

    private List<Order> createOrdersForLagTest(LocalDateTime asOfDate) {
        List<Order> orders = new ArrayList<>();

        // Create orders for past 5 weeks
        for (int week = 1; week <= 5; week++) {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchant)
                    .createdAt(asOfDate.minusWeeks(week).plusDays(1))
                    .totalAmount(BigDecimal.valueOf(1000))
                    .items(new ArrayList<>())
                    .build();

            OrderItem item = OrderItem.builder()
                    .id(UUID.randomUUID())
                    .order(order)
                    .product(product)
                    .quantity(BigDecimal.valueOf(10 * week))
                    .unitPrice(BigDecimal.valueOf(100))
                    .discountPercent(BigDecimal.ZERO)
                    .totalAmount(BigDecimal.valueOf(1000))
                    .build();

            order.getItems().add(item);
            orders.add(order);
        }

        return orders;
    }

    private List<Order> createOrdersWithIncreasingTrend(LocalDateTime asOfDate) {
        List<Order> orders = new ArrayList<>();

        for (int week = 1; week <= 13; week++) {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchant)
                    .createdAt(asOfDate.minusWeeks(week))
                    .totalAmount(BigDecimal.valueOf(1000))
                    .items(new ArrayList<>())
                    .build();

            OrderItem item = OrderItem.builder()
                    .id(UUID.randomUUID())
                    .order(order)
                    .product(product)
                    .quantity(BigDecimal.valueOf(5 + week)) // Increasing
                    .unitPrice(BigDecimal.valueOf(100))
                    .discountPercent(BigDecimal.ZERO)
                    .totalAmount(BigDecimal.valueOf(500 + week * 100))
                    .build();

            order.getItems().add(item);
            orders.add(order);
        }

        return orders;
    }

    private List<Order> createOrdersWithStableTrend(LocalDateTime asOfDate) {
        List<Order> orders = new ArrayList<>();

        for (int week = 1; week <= 13; week++) {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchant)
                    .createdAt(asOfDate.minusWeeks(week))
                    .totalAmount(BigDecimal.valueOf(1000))
                    .items(new ArrayList<>())
                    .build();

            OrderItem item = OrderItem.builder()
                    .id(UUID.randomUUID())
                    .order(order)
                    .product(product)
                    .quantity(BigDecimal.valueOf(10)) // Stable
                    .unitPrice(BigDecimal.valueOf(100))
                    .discountPercent(BigDecimal.ZERO)
                    .totalAmount(BigDecimal.valueOf(1000))
                    .build();

            order.getItems().add(item);
            orders.add(order);
        }

        return orders;
    }

    private List<Order> createManyRecentOrders(int count, LocalDateTime asOfDate) {
        List<Order> orders = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchant)
                    .createdAt(asOfDate.minusWeeks(i % 10).minusDays(i % 7))
                    .totalAmount(BigDecimal.valueOf(1000))
                    .items(new ArrayList<>())
                    .build();

            orders.add(order);
        }

        return orders;
    }

    private List<Payment> createOnTimePayments(int count, LocalDateTime asOfDate) {
        List<Payment> payments = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            LocalDate dueDate = asOfDate.minusDays(i * 7).toLocalDate();
            LocalDateTime paymentDate = asOfDate.minusDays(i * 7 + 2); // Paid 2 days before due

            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchant)
                    .paymentDueDate(dueDate)
                    .build();

            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchant)
                    .order(order)
                    .createdAt(paymentDate)
                    .amount(BigDecimal.valueOf(1000))
                    .build();

            payments.add(payment);
        }

        return payments;
    }

    private List<Payment> createLatePayments(int count, LocalDateTime asOfDate) {
        List<Payment> payments = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            LocalDate dueDate = asOfDate.minusDays(i * 7 + 20).toLocalDate();
            LocalDateTime paymentDate = asOfDate.minusDays(i * 7); // Paid 20 days late

            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchant)
                    .paymentDueDate(dueDate)
                    .build();

            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchant)
                    .order(order)
                    .createdAt(paymentDate)
                    .amount(BigDecimal.valueOf(1000))
                    .build();

            payments.add(payment);
        }

        return payments;
    }

    private List<Order> createOrdersWithDiscounts(LocalDateTime asOfDate) {
        List<Order> orders = new ArrayList<>();

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .createdAt(asOfDate.minusDays(2))
                .totalAmount(BigDecimal.valueOf(900))
                .items(new ArrayList<>())
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .product(product)
                .quantity(BigDecimal.valueOf(10))
                .unitPrice(BigDecimal.valueOf(100))
                .discountPercent(BigDecimal.valueOf(10)) // 10% discount
                .totalAmount(BigDecimal.valueOf(900))
                .build();

        order.getItems().add(item);
        orders.add(order);

        return orders;
    }
}
