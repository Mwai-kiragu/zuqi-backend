package com.zuqi.ai.feature;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.customer.CustomerCategory;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.payment.PaymentMethod;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.domain.product.Product;
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
class PaymentFeatureServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CustomerRepository merchantRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PaymentFeatureServiceImpl paymentFeatureService;

    private UUID paymentId;
    private UUID merchantId;
    private Payment payment;
    private Customer merchant;
    private LocalDateTime asOfDate;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
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
                .creditLimit(BigDecimal.valueOf(100000))
                .currentBalance(BigDecimal.valueOf(30000))
                .createdAt(asOfDate.minusDays(100))
                .build();

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .totalAmount(BigDecimal.valueOf(5000))
                .paidAmount(BigDecimal.valueOf(5000))
                .createdAt(asOfDate.minusDays(10))
                .paymentDueDate(asOfDate.minusDays(5).toLocalDate())
                .build();

        PaymentMethod mpesa = PaymentMethod.builder()
                .id(1L)
                .name("M-Pesa")
                .code("MPESA")
                .build();

        payment = Payment.builder()
                .id(paymentId)
                .merchant(merchant)
                .order(order)
                .paymentMethod(mpesa)
                .amount(BigDecimal.valueOf(5000))
                .status(PaymentStatus.COMPLETED)
                .createdAt(asOfDate.minusDays(3))
                .build();
    }

    // ===========================
    // Test Payment Not Found
    // ===========================

    @Test
    void computePaymentFeatures_whenPaymentNotFound_shouldThrowException() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentFeatureService.computePaymentFeatures(paymentId, asOfDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment not found");
    }

    @Test
    void computePaymentFeatures_whenPaymentHasNoMerchant_shouldThrowException() {
        payment.setMerchant(null);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentFeatureService.computePaymentFeatures(paymentId, asOfDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no associated merchant");
    }

    // ===========================
    // Test Per-Payment Features
    // ===========================

    @Test
    void computePaymentFeatures_shouldCalculateBasicFeatures() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(List.of(payment));

        PaymentFeatures features = paymentFeatureService.computePaymentFeatures(paymentId, asOfDate);

        assertThat(features.paymentId()).isEqualTo(paymentId);
        assertThat(features.merchantId()).isEqualTo(merchantId);
        assertThat(features.paymentAmount()).isEqualTo(BigDecimal.valueOf(5000));
        assertThat(features.invoiceAmount()).isEqualTo(BigDecimal.valueOf(5000));
        assertThat(features.paymentMethodEncoded()).isEqualTo("M-Pesa");
        assertThat(features.hourOfDay()).isEqualTo(asOfDate.minusDays(3).getHour());
    }

    @Test
    void computePaymentFeatures_shouldCalculateDaysToPay() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(List.of(payment));

        PaymentFeatures features = paymentFeatureService.computePaymentFeatures(paymentId, asOfDate);

        // Payment created 3 days ago, order created 10 days ago = 7 days to pay
        assertThat(features.daysToPay()).isEqualTo(7.0);
    }

    @Test
    void computePaymentFeatures_shouldDetectPartialPayment() {
        payment.setAmount(BigDecimal.valueOf(3000)); // Partial payment
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(List.of(payment));

        PaymentFeatures features = paymentFeatureService.computePaymentFeatures(paymentId, asOfDate);

        assertThat(features.isPartial()).isTrue();
        assertThat(features.amountVsInvoiceRatio()).isEqualTo(0.6); // 3000 / 5000
    }

    @Test
    void computePaymentFeatures_shouldDetectLatePayment() {
        // Payment made after due date
        payment.setCreatedAt(asOfDate.minusDays(2)); // Due date was 5 days ago
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(List.of(payment));

        PaymentFeatures features = paymentFeatureService.computePaymentFeatures(paymentId, asOfDate);

        assertThat(features.isLate()).isTrue();
    }

    @Test
    void computePaymentFeatures_shouldCalculateGapSinceLastPayment() {
        Payment previousPayment = Payment.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .order(payment.getOrder())
                .paymentMethod(payment.getPaymentMethod())
                .amount(BigDecimal.valueOf(2000))
                .status(PaymentStatus.COMPLETED)
                .createdAt(asOfDate.minusDays(10))
                .build();

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(List.of(previousPayment, payment));

        PaymentFeatures features = paymentFeatureService.computePaymentFeatures(paymentId, asOfDate);

        // Gap between previous payment (10 days ago) and current payment (3 days ago) = 7 days
        assertThat(features.gapSinceLastPaymentDays()).isEqualTo(7);
    }

    @Test
    void computePaymentFeatures_shouldCalculateMerchantComparisons() {
        // Create multiple merchant payments for average calculation
        List<Payment> merchantPayments = new ArrayList<>();
        merchantPayments.add(createPayment(BigDecimal.valueOf(4000), asOfDate.minusDays(30), asOfDate.minusDays(35)));
        merchantPayments.add(createPayment(BigDecimal.valueOf(6000), asOfDate.minusDays(20), asOfDate.minusDays(25)));
        merchantPayments.add(payment);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(merchantPayments);

        PaymentFeatures features = paymentFeatureService.computePaymentFeatures(paymentId, asOfDate);

        assertThat(features.merchantTotalPayments()).isEqualTo(3);
        assertThat(features.merchantAvgPayment()).isEqualByComparingTo(BigDecimal.valueOf(5000.00));
        assertThat(features.amountVsMerchantAvg()).isEqualTo(1.0); // 5000 / 5000
    }

    // ===========================
    // Test Merchant Trend Features
    // ===========================

    @Test
    void computeMerchantTrendFeatures_whenMerchantNotFound_shouldThrowException() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentFeatureService.computeMerchantTrendFeatures(merchantId, asOfDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Merchant not found");
    }

    @Test
    void computeMerchantTrendFeatures_withNoData_shouldReturnZeroValues() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantPaymentTrendFeatures features = paymentFeatureService.computeMerchantTrendFeatures(merchantId, asOfDate);

        assertThat(features.merchantId()).isEqualTo(merchantId);
        assertThat(features.daysToPayTrend3m()).isEqualTo(0.0);
        assertThat(features.latePaymentRate3m()).isEqualTo(0.0);
        assertThat(features.orderFrequency3m()).isEqualTo(0.0);
        assertThat(features.totalOutstanding()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void computeMerchantTrendFeatures_shouldCalculatePaymentTimingTrends() {
        List<Payment> payments = createPaymentTrendData();

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(payments);
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantPaymentTrendFeatures features = paymentFeatureService.computeMerchantTrendFeatures(merchantId, asOfDate);

        assertThat(features.daysToPayTrend3m()).isNotNull();
        assertThat(features.daysToPayStddev3m()).isGreaterThanOrEqualTo(0.0);
        assertThat(features.latePaymentRate3m()).isBetween(0.0, 1.0);
    }

    @Test
    void computeMerchantTrendFeatures_shouldCalculateOrderFrequency() {
        List<Order> orders = new ArrayList<>();
        // Add 12 orders over 3 months = 1 order per week
        for (int i = 0; i < 12; i++) {
            orders.add(createOrder(BigDecimal.valueOf(5000), asOfDate.minusWeeks(i)));
        }

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(orders);

        MerchantPaymentTrendFeatures features = paymentFeatureService.computeMerchantTrendFeatures(merchantId, asOfDate);

        // 12 orders in 3 months (13 weeks) ≈ 0.92 orders/week
        assertThat(features.orderFrequency3m()).isCloseTo(0.92, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void computeMerchantTrendFeatures_shouldCalculateCreditUtilization() {
        List<Order> orders = new ArrayList<>();
        // Create orders with unpaid amounts
        orders.add(createUnpaidOrder(BigDecimal.valueOf(20000), BigDecimal.valueOf(10000), asOfDate.minusDays(30)));
        orders.add(createUnpaidOrder(BigDecimal.valueOf(30000), BigDecimal.valueOf(15000), asOfDate.minusDays(60)));

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(orders);

        MerchantPaymentTrendFeatures features = paymentFeatureService.computeMerchantTrendFeatures(merchantId, asOfDate);

        // Average outstanding: (10000 + 15000) / 2 = 12500
        // Utilization: 12500 / 100000 = 0.125
        assertThat(features.creditUtilization3m()).isCloseTo(0.125, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void computeMerchantTrendFeatures_shouldDetectHitCreditLimit() {
        merchant.setCreditLimit(BigDecimal.valueOf(10000));

        List<Order> orders = new ArrayList<>();
        // Create order that uses 96% of credit limit
        orders.add(createUnpaidOrder(BigDecimal.valueOf(9600), BigDecimal.ZERO, asOfDate.minusDays(10)));

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(orders);

        MerchantPaymentTrendFeatures features = paymentFeatureService.computeMerchantTrendFeatures(merchantId, asOfDate);

        assertThat(features.hitCreditLimit3m()).isTrue();
        assertThat(features.peakUtilization3m()).isGreaterThan(0.95);
    }

    @Test
    void computeMerchantTrendFeatures_shouldCalculatePartialPaymentFrequency() {
        List<Payment> payments = new ArrayList<>();
        // 2 full payments
        payments.add(createFullPayment(BigDecimal.valueOf(5000), asOfDate.minusDays(60)));
        payments.add(createFullPayment(BigDecimal.valueOf(5000), asOfDate.minusDays(45)));
        // 1 partial payment
        payments.add(createPartialPayment(BigDecimal.valueOf(3000), BigDecimal.valueOf(5000), asOfDate.minusDays(30)));

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(payments);
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantPaymentTrendFeatures features = paymentFeatureService.computeMerchantTrendFeatures(merchantId, asOfDate);

        // 1 partial out of 3 payments = 33%
        assertThat(features.partialPaymentFreq3m()).isCloseTo(0.33, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void computeMerchantTrendFeatures_shouldCalculateConsecutivePartialPayments() {
        List<Payment> payments = new ArrayList<>();
        // Recent partial payments (should count)
        payments.add(createPartialPayment(BigDecimal.valueOf(3000), BigDecimal.valueOf(5000), asOfDate.minusDays(5)));
        payments.add(createPartialPayment(BigDecimal.valueOf(2000), BigDecimal.valueOf(5000), asOfDate.minusDays(3)));
        payments.add(createPartialPayment(BigDecimal.valueOf(4000), BigDecimal.valueOf(5000), asOfDate.minusDays(1)));
        // Older full payment (breaks streak)
        payments.add(createFullPayment(BigDecimal.valueOf(5000), asOfDate.minusDays(10)));

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(payments);
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());

        MerchantPaymentTrendFeatures features = paymentFeatureService.computeMerchantTrendFeatures(merchantId, asOfDate);

        assertThat(features.consecutivePartialPayments()).isEqualTo(3);
    }

    @Test
    void computeMerchantTrendFeatures_shouldCalculateTotalOutstanding() {
        List<Order> orders = new ArrayList<>();
        orders.add(createUnpaidOrder(BigDecimal.valueOf(10000), BigDecimal.valueOf(4000), asOfDate.minusDays(30)));
        orders.add(createUnpaidOrder(BigDecimal.valueOf(8000), BigDecimal.valueOf(3000), asOfDate.minusDays(15)));
        orders.add(createOrder(BigDecimal.valueOf(5000), asOfDate.minusDays(5))); // Fully paid

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(orders);

        MerchantPaymentTrendFeatures features = paymentFeatureService.computeMerchantTrendFeatures(merchantId, asOfDate);

        // Outstanding: (10000-4000) + (8000-3000) = 6000 + 5000 = 11000
        assertThat(features.totalOutstanding()).isEqualTo(BigDecimal.valueOf(11000));
    }

    @Test
    void computeMerchantTrendFeatures_shouldCalculateMaxDaysOverdue() {
        List<Order> orders = new ArrayList<>();
        // Order 30 days overdue
        Order overdue1 = createOrder(BigDecimal.valueOf(5000), asOfDate.minusDays(60));
        overdue1.setPaymentDueDate(asOfDate.minusDays(30).toLocalDate());
        overdue1.setPaidAmount(BigDecimal.ZERO);
        orders.add(overdue1);

        // Order 15 days overdue
        Order overdue2 = createOrder(BigDecimal.valueOf(3000), asOfDate.minusDays(45));
        overdue2.setPaymentDueDate(asOfDate.minusDays(15).toLocalDate());
        overdue2.setPaidAmount(BigDecimal.ZERO);
        orders.add(overdue2);

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(orders);

        MerchantPaymentTrendFeatures features = paymentFeatureService.computeMerchantTrendFeatures(merchantId, asOfDate);

        assertThat(features.daysOverdueMax()).isEqualTo(30);
    }

    @Test
    void computeMerchantTrendFeatures_shouldCalculatePaymentToOrderRatio() {
        List<Payment> payments = new ArrayList<>();
        payments.add(createPayment(BigDecimal.valueOf(5000), asOfDate.minusDays(60), asOfDate.minusDays(65)));
        payments.add(createPayment(BigDecimal.valueOf(3000), asOfDate.minusDays(45), asOfDate.minusDays(50)));

        List<Order> orders = new ArrayList<>();
        orders.add(createOrder(BigDecimal.valueOf(5000), asOfDate.minusDays(65)));
        orders.add(createOrder(BigDecimal.valueOf(3000), asOfDate.minusDays(50)));

        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(payments);
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate))
                .thenReturn(orders);

        MerchantPaymentTrendFeatures features = paymentFeatureService.computeMerchantTrendFeatures(merchantId, asOfDate);

        // Total payments: 8000, Total orders: 8000, Ratio = 1.0 (healthy)
        assertThat(features.paymentToOrderRatio3m()).isEqualTo(1.0);
    }

    // ===========================
    // Test Cache Eviction
    // ===========================

    @Test
    void evictPaymentCache_shouldNotThrowException() {
        paymentFeatureService.evictPaymentCache(paymentId);

        verify(paymentRepository, never()).findById(any());
    }

    @Test
    void evictMerchantTrendCache_shouldNotThrowException() {
        paymentFeatureService.evictMerchantTrendCache(merchantId);

        verify(merchantRepository, never()).findById(any());
    }

    // ===========================
    // Helper Methods
    // ===========================

    private Payment createPayment(BigDecimal amount, LocalDateTime paymentDate, LocalDateTime orderDate) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .totalAmount(amount)
                .paidAmount(amount)
                .createdAt(orderDate)
                .paymentDueDate(orderDate.plusDays(30).toLocalDate())
                .build();

        PaymentMethod method = PaymentMethod.builder()
                .id(1L)
                .name("M-Pesa")
                .build();

        return Payment.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .order(order)
                .paymentMethod(method)
                .amount(amount)
                .status(PaymentStatus.COMPLETED)
                .createdAt(paymentDate)
                .build();
    }

    private Payment createFullPayment(BigDecimal amount, LocalDateTime paymentDate) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .totalAmount(amount)
                .paidAmount(amount)
                .createdAt(paymentDate.minusDays(5))
                .build();

        PaymentMethod method = PaymentMethod.builder()
                .id(1L)
                .name("M-Pesa")
                .build();

        return Payment.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .order(order)
                .paymentMethod(method)
                .amount(amount)
                .status(PaymentStatus.COMPLETED)
                .createdAt(paymentDate)
                .build();
    }

    private Payment createPartialPayment(BigDecimal paymentAmount, BigDecimal orderAmount, LocalDateTime paymentDate) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .totalAmount(orderAmount)
                .paidAmount(paymentAmount)
                .createdAt(paymentDate.minusDays(5))
                .build();

        PaymentMethod method = PaymentMethod.builder()
                .id(1L)
                .name("Cash")
                .build();

        return Payment.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .order(order)
                .paymentMethod(method)
                .amount(paymentAmount)
                .status(PaymentStatus.COMPLETED)
                .createdAt(paymentDate)
                .build();
    }

    private List<Payment> createPaymentTrendData() {
        List<Payment> payments = new ArrayList<>();
        // Create payments with increasing days-to-pay (worsening trend)
        for (int i = 0; i < 5; i++) {
            LocalDateTime orderDate = asOfDate.minusDays(60 - i * 10);
            LocalDateTime paymentDate = orderDate.plusDays(5 + i * 2); // Increasing delay
            payments.add(createPayment(BigDecimal.valueOf(5000), paymentDate, orderDate));
        }
        return payments;
    }

    private Order createOrder(BigDecimal totalAmount, LocalDateTime createdAt) {
        return Order.builder()
                .id(UUID.randomUUID())
                .totalAmount(totalAmount)
                .paidAmount(totalAmount)
                .status(OrderStatus.DELIVERED)
                .createdAt(createdAt)
                .build();
    }

    private Order createUnpaidOrder(BigDecimal totalAmount, BigDecimal paidAmount, LocalDateTime createdAt) {
        return Order.builder()
                .id(UUID.randomUUID())
                .totalAmount(totalAmount)
                .paidAmount(paidAmount)
                .status(OrderStatus.DELIVERED)
                .createdAt(createdAt)
                .build();
    }
}
