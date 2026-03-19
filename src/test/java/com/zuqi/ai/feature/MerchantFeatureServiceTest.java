package com.zuqi.ai.feature;

import com.zuqi.domain.credit.CreditLimit;
import com.zuqi.domain.credit.CreditLimitStatus;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.customer.CustomerCategory;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderItem;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.payment.PaymentMethod;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.user.User;
import com.zuqi.repository.CreditLimitRepository;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.PaymentRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantFeatureServiceTest {

    @Mock
    private CustomerRepository merchantRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CreditLimitRepository creditLimitRepository;

    @InjectMocks
    private MerchantFeatureServiceImpl merchantFeatureService;

    private UUID merchantId;
    private Customer merchant;
    private LocalDateTime asOfDate;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        asOfDate = LocalDateTime.now();

        CustomerCategory category = CustomerCategory.builder()
                .id(1L)
                .name("Retail")
                .build();

        merchant = Customer.builder()
                .id(merchantId)
                .businessName("Test Merchant")
                .city("Nairobi")
                .category(category)
                .createdAt(asOfDate.minusDays(100))
                .build();
    }

    // ===========================
    // Test Merchant Not Found
    // ===========================

    @Test
    void computeFeatures_whenMerchantNotFound_shouldThrowException() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantFeatureService.computeFeatures(merchantId, asOfDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer not found");
    }

    // ===========================
    // Test Order Features
    // ===========================

    @Test
    void computeFeatures_withNoOrders_shouldReturnZeroOrderFeatures() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.totalOrders()).isEqualTo(0);
        assertThat(features.orderFrequencyPerWeek()).isEqualTo(0.0);
        assertThat(features.avgOrderValue()).isEqualTo(BigDecimal.ZERO);
        assertThat(features.orderValueTrendSlope12w()).isEqualTo(0.0);
        assertThat(features.orderConsistencyStddev()).isEqualTo(0.0);
        assertThat(features.cancellationRate()).isEqualTo(0.0);
        assertThat(features.returnRate()).isEqualTo(0.0);
        assertThat(features.daysSinceLastOrder()).isEqualTo(Integer.MAX_VALUE);
        assertThat(features.uniqueSkusOrdered()).isEqualTo(0);
        assertThat(features.topSkuConcentration()).isEqualTo(0.0);
    }

    @Test
    void computeFeatures_withMultipleOrders_shouldCalculateOrderFeatures() {
        List<Order> orders = createSampleOrders(5);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.totalOrders()).isEqualTo(5);
        assertThat(features.orderFrequencyPerWeek()).isGreaterThan(0.0);
        assertThat(features.avgOrderValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(features.uniqueSkusOrdered()).isGreaterThan(0);
    }

    @Test
    void computeFeatures_withCancelledOrders_shouldCalculateCancellationRate() {
        List<Order> orders = new ArrayList<>();
        orders.add(createOrder(OrderStatus.DELIVERED, BigDecimal.valueOf(1000), asOfDate.minusDays(10)));
        orders.add(createOrder(OrderStatus.DELIVERED, BigDecimal.valueOf(2000), asOfDate.minusDays(20)));
        orders.add(createOrder(OrderStatus.CANCELLED, BigDecimal.valueOf(1500), asOfDate.minusDays(15)));

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.cancellationRate()).isEqualTo(1.0 / 3.0);
    }

    @Test
    void computeFeatures_withDiverseProducts_shouldCalculateTopSkuConcentration() {
        List<Order> orders = createOrdersWithDiverseProducts();

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.topSkuConcentration()).isBetween(0.0, 1.0);
    }

    // ===========================
    // Test Payment Features
    // ===========================

    @Test
    void computeFeatures_withNoPayments_shouldReturnZeroPaymentFeatures() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.totalPayments()).isEqualTo(0);
        assertThat(features.onTimePaymentPct()).isNull();
        assertThat(features.avgDaysToPay()).isNull();
        assertThat(features.worstDaysToPay()).isNull();
        assertThat(features.partialPaymentFrequency()).isEqualTo(0.0);
        assertThat(features.consecutiveOnTimeStreak()).isEqualTo(0);
    }

    @Test
    void computeFeatures_withOnTimePayments_shouldCalculatePaymentFeatures() {
        List<Payment> payments = createOnTimePayments(3);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(payments);
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.totalPayments()).isEqualTo(3);
        assertThat(features.onTimePaymentPct()).isEqualTo(1.0);
        assertThat(features.consecutiveOnTimeStreak()).isEqualTo(3);
    }

    @Test
    void computeFeatures_withLatePayments_shouldCalculateCorrectMetrics() {
        List<Payment> payments = createMixedPayments();

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(payments);
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.onTimePaymentPct()).isLessThan(1.0);
        assertThat(features.avgDaysToPay()).isNotNull();
        assertThat(features.worstDaysToPay()).isNotNull();
    }

    @Test
    void computeFeatures_withPaymentMethods_shouldCalculateDistribution() {
        List<Payment> payments = createPaymentsWithDifferentMethods();

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(payments);
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.paymentMethodDistribution()).isNotEmpty();
        assertThat(features.paymentMethodDistribution().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(payments.size());
    }

    // ===========================
    // Test Credit Features
    // ===========================

    @Test
    void computeFeatures_withNoCreditLimit_shouldReturnZeroCreditFeatures() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.currentCreditLimit()).isEqualTo(BigDecimal.ZERO);
        assertThat(features.currentUtilizationRatio()).isNull();
        assertThat(features.limitIncreaseCount()).isEqualTo(0);
        assertThat(features.daysSinceLastLimitChange()).isNull();
    }

    @Test
    void computeFeatures_withActiveCreditLimit_shouldCalculateCreditFeatures() {
        CreditLimit creditLimit = createCreditLimit(BigDecimal.valueOf(100000), asOfDate.minusDays(30));

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.of(creditLimit));
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(List.of(creditLimit));

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.currentCreditLimit()).isEqualTo(BigDecimal.valueOf(100000));
        assertThat(features.currentUtilizationRatio()).isEqualTo(0.0);
        assertThat(features.daysSinceLastLimitChange()).isEqualTo(30);
    }

    @Test
    void computeFeatures_withCreditLimitHistory_shouldCalculateLimitIncreases() {
        List<CreditLimit> creditHistory = new ArrayList<>();
        creditHistory.add(createCreditLimit(BigDecimal.valueOf(50000), asOfDate.minusDays(90)));
        creditHistory.add(createCreditLimit(BigDecimal.valueOf(75000), asOfDate.minusDays(60)));
        creditHistory.add(createCreditLimit(BigDecimal.valueOf(100000), asOfDate.minusDays(30)));

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.of(creditHistory.get(2)));
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(creditHistory);

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.limitIncreaseCount()).isEqualTo(2);
    }

    @Test
    void computeFeatures_withUtilizedCredit_shouldCalculateUtilizationRatio() {
        CreditLimit creditLimit = createCreditLimit(BigDecimal.valueOf(100000), asOfDate.minusDays(30));
        List<Order> orders = createUnpaidOrders();

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.of(creditLimit));
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(List.of(creditLimit));

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.currentUtilizationRatio()).isGreaterThan(0.0);
    }

    // ===========================
    // Test Profile Features
    // ===========================

    @Test
    void computeFeatures_shouldCalculateProfileFeatures() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.businessCategoryEncoded()).isEqualTo("Retail");
        assertThat(features.relationshipTenureDays()).isEqualTo(100);
        assertThat(features.geographicCluster()).isEqualTo("Nairobi");
        assertThat(features.verificationStatus()).isEqualTo("PENDING");
    }

    @Test
    void computeFeatures_withNullCategory_shouldReturnUnknownCategory() {
        merchant.setCategory(null);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.businessCategoryEncoded()).isEqualTo("UNKNOWN");
    }

    @Test
    void computeFeatures_withNullCity_shouldReturnUnknownCluster() {
        merchant.setCity(null);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, asOfDate);

        assertThat(features.geographicCluster()).isEqualTo("UNKNOWN");
    }

    // ===========================
    // Test Historical Mode
    // ===========================

    @Test
    void computeFeatures_withHistoricalDate_shouldOnlyIncludeDataBeforeDate() {
        LocalDateTime historicalDate = asOfDate.minusDays(50);
        List<Order> allOrders = new ArrayList<>();
        allOrders.add(createOrder(OrderStatus.DELIVERED, BigDecimal.valueOf(1000), asOfDate.minusDays(60)));
        allOrders.add(createOrder(OrderStatus.DELIVERED, BigDecimal.valueOf(2000), asOfDate.minusDays(40)));

        List<Order> historicalOrders = List.of(allOrders.get(0));

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(eq(merchantId), eq(historicalDate)))
                .thenReturn(historicalOrders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(eq(merchantId), eq(historicalDate)))
                .thenReturn(Collections.emptyList());
        when(creditLimitRepository.findActiveLimitByMerchantId(eq(merchantId), eq(historicalDate)))
                .thenReturn(Optional.empty());
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(eq(merchantId), eq(historicalDate)))
                .thenReturn(Collections.emptyList());

        MerchantFeatures features = merchantFeatureService.computeFeatures(merchantId, historicalDate);

        assertThat(features.totalOrders()).isEqualTo(1);
        assertThat(features.computedAt()).isEqualTo(historicalDate);
    }

    // ===========================
    // Test Cache Eviction
    // ===========================

    @Test
    void evictCache_shouldNotThrowException() {
        merchantFeatureService.evictCache(merchantId);

        verify(merchantRepository, never()).findById(any());
    }

    // ===========================
    // Helper Methods
    // ===========================

    private List<Order> createSampleOrders(int count) {
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            orders.add(createOrder(OrderStatus.DELIVERED, BigDecimal.valueOf(1000 + i * 500), asOfDate.minusDays(i * 7)));
        }
        return orders;
    }

    private Order createOrder(OrderStatus status, BigDecimal totalAmount, LocalDateTime createdAt) {
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Product " + UUID.randomUUID())
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .quantity(BigDecimal.valueOf(10))
                .unitPrice(totalAmount.divide(BigDecimal.valueOf(10), 2, java.math.RoundingMode.HALF_UP))
                .totalAmount(totalAmount)
                .build();

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .status(status)
                .totalAmount(totalAmount)
                .paidAmount(status == OrderStatus.DELIVERED ? totalAmount : BigDecimal.ZERO)
                .items(new ArrayList<>(List.of(item)))
                .createdAt(createdAt)
                .paymentDueDate(createdAt.toLocalDate().plusDays(30))
                .build();

        item.setOrder(order);
        return order;
    }

    private List<Order> createOrdersWithDiverseProducts() {
        UUID product1 = UUID.randomUUID();
        UUID product2 = UUID.randomUUID();
        UUID product3 = UUID.randomUUID();

        List<Order> orders = new ArrayList<>();

        // Order with product1 (3 times)
        for (int i = 0; i < 3; i++) {
            orders.add(createOrderWithProduct(product1, asOfDate.minusDays(i * 5)));
        }

        // Order with product2 (2 times)
        for (int i = 0; i < 2; i++) {
            orders.add(createOrderWithProduct(product2, asOfDate.minusDays(15 + i * 5)));
        }

        // Order with product3 (1 time)
        orders.add(createOrderWithProduct(product3, asOfDate.minusDays(25)));

        return orders;
    }

    private Order createOrderWithProduct(UUID productId, LocalDateTime createdAt) {
        Product product = Product.builder()
                .id(productId)
                .name("Product " + productId)
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .quantity(BigDecimal.valueOf(5))
                .unitPrice(BigDecimal.valueOf(200))
                .totalAmount(BigDecimal.valueOf(1000))
                .build();

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.DELIVERED)
                .totalAmount(BigDecimal.valueOf(1000))
                .paidAmount(BigDecimal.valueOf(1000))
                .items(new ArrayList<>(List.of(item)))
                .createdAt(createdAt)
                .build();

        item.setOrder(order);
        return order;
    }

    private List<Payment> createOnTimePayments(int count) {
        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            payments.add(createPayment(asOfDate.minusDays(i * 10), asOfDate.minusDays(i * 10 + 5)));
        }
        return payments;
    }

    private Payment createPayment(LocalDateTime orderDate, LocalDateTime paymentDate) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .totalAmount(BigDecimal.valueOf(5000))
                .createdAt(orderDate)
                .paymentDueDate(orderDate.toLocalDate().plusDays(30))
                .build();

        PaymentMethod method = PaymentMethod.builder()
                .id(1L)
                .name("M-Pesa")
                .build();

        return Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .paymentMethod(method)
                .amount(BigDecimal.valueOf(5000))
                .status(PaymentStatus.COMPLETED)
                .createdAt(paymentDate)
                .build();
    }

    private List<Payment> createMixedPayments() {
        List<Payment> payments = new ArrayList<>();

        // On-time payment
        payments.add(createPayment(asOfDate.minusDays(40), asOfDate.minusDays(35)));

        // Late payment
        payments.add(createPayment(asOfDate.minusDays(70), asOfDate.minusDays(35)));

        // On-time payment
        payments.add(createPayment(asOfDate.minusDays(20), asOfDate.minusDays(15)));

        return payments;
    }

    private List<Payment> createPaymentsWithDifferentMethods() {
        List<Payment> payments = new ArrayList<>();

        PaymentMethod mpesa = PaymentMethod.builder().id(1L).name("M-Pesa").build();
        PaymentMethod cash = PaymentMethod.builder().id(2L).name("Cash").build();
        PaymentMethod bank = PaymentMethod.builder().id(3L).name("Bank Transfer").build();

        for (int i = 0; i < 3; i++) {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .totalAmount(BigDecimal.valueOf(5000))
                    .createdAt(asOfDate.minusDays(i * 10))
                    .build();

            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .order(order)
                    .amount(BigDecimal.valueOf(5000))
                    .status(PaymentStatus.COMPLETED)
                    .createdAt(asOfDate.minusDays(i * 10 + 5))
                    .build();

            if (i == 0) payment.setPaymentMethod(mpesa);
            else if (i == 1) payment.setPaymentMethod(cash);
            else payment.setPaymentMethod(bank);

            payments.add(payment);
        }

        return payments;
    }

    private CreditLimit createCreditLimit(BigDecimal approvedLimit, LocalDateTime createdAt) {
        return CreditLimit.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .approvedLimit(approvedLimit)
                .utilizedAmount(BigDecimal.ZERO)
                .availableLimit(approvedLimit)
                .status(CreditLimitStatus.ACTIVE)
                .createdAt(createdAt)
                .build();
    }

    private List<Order> createUnpaidOrders() {
        List<Order> orders = new ArrayList<>();

        Order unpaidOrder1 = createOrder(OrderStatus.DELIVERED, BigDecimal.valueOf(30000), asOfDate.minusDays(10));
        unpaidOrder1.setPaidAmount(BigDecimal.valueOf(10000));
        orders.add(unpaidOrder1);

        Order unpaidOrder2 = createOrder(OrderStatus.DELIVERED, BigDecimal.valueOf(20000), asOfDate.minusDays(20));
        unpaidOrder2.setPaidAmount(BigDecimal.ZERO);
        orders.add(unpaidOrder2);

        return orders;
    }
}
