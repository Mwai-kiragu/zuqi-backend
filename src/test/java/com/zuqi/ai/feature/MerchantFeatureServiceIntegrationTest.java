package com.zuqi.ai.feature;

import com.zuqi.domain.credit.CreditLimit;
import com.zuqi.domain.credit.CreditLimitStatus;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.merchant.MerchantCategory;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderItem;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.payment.PaymentMethod;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.CreditLimitRepository;
import com.zuqi.repository.MerchantRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

/**
 * Integration test for MerchantFeatureService verifying exact feature computation
 * against fully controlled, known test data.
 *
 * Unlike MerchantFeatureServiceTest (which tests individual features in isolation
 * with "isGreaterThan" style assertions), these tests use precise input data and
 * assert exact computed values — validating the arithmetic, not just the direction.
 *
 * Each test method documents the scenario in-line so the expected values are
 * self-evident to future readers.
 *
 * Blueprint reference: implementation_plan.md Task 1.4
 */
@ExtendWith(MockitoExtension.class)
class MerchantFeatureServiceIntegrationTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CreditLimitRepository creditLimitRepository;

    @InjectMocks
    private MerchantFeatureServiceImpl service;

    // Fixed reference date — avoids LocalDateTime.now() flakiness across tests
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 6, 15, 12, 0);

    private UUID merchantId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        MerchantCategory category = MerchantCategory.builder().id(1L).name("Retail").build();
        merchant = Merchant.builder()
                .id(merchantId)
                .businessName("Kamau Supermarket")
                .city("Nairobi")
                .category(category)
                .createdAt(AS_OF.minusDays(100))  // 100-day tenure
                .build();
    }

    // =========================================================================
    // FULL SCENARIO: STEADY_GROWER — all features verified in one test
    // =========================================================================

    @Test
    void fullScenario_steadyGrower_allFeaturesComputedCorrectly() {
        // Setup:
        //   - 4 orders, each KES 1,000, same product, all DELIVERED
        //     at -28, -21, -14, -7 days relative to AS_OF
        //   - 4 payments each paying their order in full, 5 days after order date
        //   - 1 credit limit: KES 50,000 set 30 days ago
        //
        // Expected:
        //   orderFrequencyPerWeek = 4 / (28/7) = 4/4 = 1.0
        //   avgOrderValue = 1000
        //   daysSinceLastOrder = 7
        //   uniqueSkusOrdered = 1 (same product every time)
        //   topSkuConcentration = 4/4 = 1.0
        //   onTimePaymentPct = 1.0 (all paid 5 days before 30-day due date)
        //   avgDaysToPay = 5.0, worstDaysToPay = 5
        //   consecutiveOnTimeStreak = 4
        //   currentCreditLimit = 50000, utilization = 0.0 (all paid)
        //   daysSinceLastLimitChange = 30

        UUID productId = UUID.randomUUID();
        PaymentMethod mpesa = PaymentMethod.builder().id(1L).name("M-Pesa").build();

        List<Order> orders = new ArrayList<>();
        List<Payment> payments = new ArrayList<>();

        // Build 4 orders + 4 payments (oldest first: -28, -21, -14, -7)
        for (int weeksAgo = 4; weeksAgo >= 1; weeksAgo--) {
            LocalDateTime orderDate = AS_OF.minusDays(weeksAgo * 7L);
            Order order = buildFullyPaidOrder(productId, BigDecimal.valueOf(1000), orderDate);
            orders.add(order);

            // Payment 5 days after order — well within 30-day due date
            Payment payment = buildPayment(order, BigDecimal.valueOf(1000), orderDate.plusDays(5), mpesa);
            payments.add(payment);
        }

        CreditLimit creditLimit = buildCreditLimit(BigDecimal.valueOf(50000), AS_OF.minusDays(30));

        stubRepositories(orders, payments, Optional.of(creditLimit), List.of(creditLimit));

        // Execute
        MerchantFeatures features = service.computeFeatures(merchantId, AS_OF);

        // Order features
        assertThat(features.totalOrders()).isEqualTo(4);
        // First order at -28 days: daysSinceFirst=28, weeks=4.0 → 4 orders / 4 weeks = 1.0
        assertThat(features.orderFrequencyPerWeek()).isEqualTo(1.0);
        assertThat(features.avgOrderValue()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(features.cancellationRate()).isEqualTo(0.0);
        assertThat(features.returnRate()).isEqualTo(0.0);
        assertThat(features.daysSinceLastOrder()).isEqualTo(7);   // most recent order at -7
        assertThat(features.uniqueSkusOrdered()).isEqualTo(1);    // same product every time
        assertThat(features.topSkuConcentration()).isEqualTo(1.0); // 4/4 items = same SKU

        // Payment features
        assertThat(features.totalPayments()).isEqualTo(4);
        assertThat(features.onTimePaymentPct()).isEqualTo(1.0);
        assertThat(features.avgDaysToPay()).isEqualTo(5.0);
        assertThat(features.worstDaysToPay()).isEqualTo(5);
        assertThat(features.partialPaymentFrequency()).isEqualTo(0.0);
        assertThat(features.consecutiveOnTimeStreak()).isEqualTo(4);
        assertThat(features.paymentMethodDistribution()).containsEntry("M-Pesa", 4);

        // Credit features
        assertThat(features.currentCreditLimit()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(features.currentUtilizationRatio()).isEqualTo(0.0); // all orders fully paid
        assertThat(features.limitIncreaseCount()).isEqualTo(0);        // single limit in history
        assertThat(features.daysSinceLastLimitChange()).isEqualTo(30);

        // Profile features
        assertThat(features.businessCategoryEncoded()).isEqualTo("Retail");
        assertThat(features.relationshipTenureDays()).isEqualTo(100);
        assertThat(features.verificationStatus()).isEqualTo("UNVERIFIED");
        assertThat(features.geographicCluster()).isEqualTo("Nairobi");
        assertThat(features.computedAt()).isEqualTo(AS_OF);
    }

    // =========================================================================
    // ORDER FREQUENCY — boundary values
    // =========================================================================

    @Test
    void orderFrequency_twoOrdersSevenDaysApart_computesCorrectly() {
        // First order at -7 days, second at -1 day.
        // daysSinceFirst = DAYS.between(-7, 0) = 7
        // weeks = 7 / 7.0 = 1.0
        // frequency = 2 / 1.0 = 2.0
        UUID productId = UUID.randomUUID();
        Order order1 = buildFullyPaidOrder(productId, BigDecimal.valueOf(500), AS_OF.minusDays(7));
        Order order2 = buildFullyPaidOrder(productId, BigDecimal.valueOf(500), AS_OF.minusDays(1));

        stubRepositories(List.of(order1, order2), List.of(), Optional.empty(), List.of());

        MerchantFeatures features = service.computeFeatures(merchantId, AS_OF);

        assertThat(features.orderFrequencyPerWeek()).isEqualTo(2.0);
        assertThat(features.daysSinceLastOrder()).isEqualTo(1);
    }

    @Test
    void orderFrequency_singleOrderSameDay_returnsSizeAsFrequency() {
        // daysSinceFirst = 0 → implementation returns (double) orders.size() = 1.0
        UUID productId = UUID.randomUUID();
        Order order = buildFullyPaidOrder(productId, BigDecimal.valueOf(1000), AS_OF.minusMinutes(30));

        stubRepositories(List.of(order), List.of(), Optional.empty(), List.of());

        MerchantFeatures features = service.computeFeatures(merchantId, AS_OF);

        assertThat(features.orderFrequencyPerWeek()).isEqualTo(1.0);
        assertThat(features.daysSinceLastOrder()).isEqualTo(0);
    }

    // =========================================================================
    // CREDIT UTILIZATION — partially paid orders
    // =========================================================================

    @Test
    void creditUtilization_partiallyPaidOrders_raisesRatioCorrectly() {
        // Order 1: KES 30,000 total, KES 10,000 paid → KES 20,000 outstanding
        // Order 2: KES 20,000 total, KES 20,000 paid → no outstanding balance
        // Credit limit: KES 100,000
        // Expected utilization = 20,000 / 100,000 = 0.2
        Order unpaid = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.DELIVERED)
                .totalAmount(BigDecimal.valueOf(30000))
                .paidAmount(BigDecimal.valueOf(10000))
                .items(new ArrayList<>())
                .createdAt(AS_OF.minusDays(10))
                .paymentDueDate(AS_OF.plusDays(20).toLocalDate())
                .build();

        Order paid = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.DELIVERED)
                .totalAmount(BigDecimal.valueOf(20000))
                .paidAmount(BigDecimal.valueOf(20000))
                .items(new ArrayList<>())
                .createdAt(AS_OF.minusDays(20))
                .paymentDueDate(AS_OF.plusDays(10).toLocalDate())
                .build();

        CreditLimit limit = buildCreditLimit(BigDecimal.valueOf(100000), AS_OF.minusDays(60));
        stubRepositories(List.of(unpaid, paid), List.of(), Optional.of(limit), List.of(limit));

        MerchantFeatures features = service.computeFeatures(merchantId, AS_OF);

        // 20,000 / 100,000 = 0.2 (scale 4 → 0.2000 → doubleValue = 0.2)
        assertThat(features.currentUtilizationRatio()).isEqualTo(0.2);
        assertThat(features.currentCreditLimit()).isEqualByComparingTo(BigDecimal.valueOf(100000));
    }

    // =========================================================================
    // CREDIT LIMIT HISTORY — increase count with mixed increases and decreases
    // =========================================================================

    @Test
    void creditLimitHistory_twoIncreasesAndOneDecrease_countedCorrectly() {
        // History (chronological):
        //   -90 days: KES 30,000  (initial)
        //   -60 days: KES 50,000  (+20k — increase #1)
        //   -30 days: KES 70,000  (+20k — increase #2)
        //    -5 days: KES 60,000  (-10k — decrease, not counted)
        // Expected: limitIncreaseCount = 2, daysSinceLastLimitChange = 5
        CreditLimit l1 = buildCreditLimit(BigDecimal.valueOf(30000), AS_OF.minusDays(90));
        CreditLimit l2 = buildCreditLimit(BigDecimal.valueOf(50000), AS_OF.minusDays(60));
        CreditLimit l3 = buildCreditLimit(BigDecimal.valueOf(70000), AS_OF.minusDays(30));
        CreditLimit l4 = buildCreditLimit(BigDecimal.valueOf(60000), AS_OF.minusDays(5));

        stubRepositories(List.of(), List.of(), Optional.of(l4), List.of(l1, l2, l3, l4));

        MerchantFeatures features = service.computeFeatures(merchantId, AS_OF);

        assertThat(features.limitIncreaseCount()).isEqualTo(2);
        assertThat(features.daysSinceLastLimitChange()).isEqualTo(5);
    }

    // =========================================================================
    // CONSECUTIVE ON-TIME PAYMENT STREAK
    // =========================================================================

    @Test
    void paymentStreak_twoConsecutiveOnTime_thenOneLate_streakEqualsTwo() {
        // Payments sorted by date descending: P3 → P2 → P1
        // P1 (oldest, March 20): paid March 20, due March 3 → LATE (after due)
        // P2 (mid,   April 25):  paid April 25, due April 25 → ON TIME (same day = not after)
        // P3 (newest, May 20):   paid May 20,   due May 20   → ON TIME
        // Streak from most-recent: P3 on-time (1), P2 on-time (2), P1 late → break
        // Expected: streak = 2

        Order o1 = buildOrderWithDueDate(
                LocalDateTime.of(2026, 2, 1, 0, 0),
                LocalDate.of(2026, 3, 3));   // due March 3

        Order o2 = buildOrderWithDueDate(
                LocalDateTime.of(2026, 3, 26, 0, 0),
                LocalDate.of(2026, 4, 25));  // due April 25

        Order o3 = buildOrderWithDueDate(
                LocalDateTime.of(2026, 4, 20, 0, 0),
                LocalDate.of(2026, 5, 20));  // due May 20

        Payment p1 = buildPayment(o1, BigDecimal.valueOf(1000),
                LocalDateTime.of(2026, 3, 20, 0, 0), null);  // LATE: after due March 3

        Payment p2 = buildPayment(o2, BigDecimal.valueOf(1000),
                LocalDateTime.of(2026, 4, 25, 0, 0), null);  // ON TIME: same as due date

        Payment p3 = buildPayment(o3, BigDecimal.valueOf(1000),
                LocalDateTime.of(2026, 5, 20, 0, 0), null);  // ON TIME: same as due date

        stubRepositories(List.of(), List.of(p1, p2, p3), Optional.empty(), List.of());

        MerchantFeatures features = service.computeFeatures(merchantId, AS_OF);

        assertThat(features.consecutiveOnTimeStreak()).isEqualTo(2);
        // 2 of 3 payments on time
        assertThat(features.onTimePaymentPct())
                .isCloseTo(2.0 / 3.0, within(0.001));
    }

    @Test
    void paymentStreak_allOnTime_streakEqualsCount() {
        // 3 payments all on time → streak = 3
        Order o = buildOrderWithDueDate(AS_OF.minusDays(30), AS_OF.plusDays(0).toLocalDate());
        Payment p1 = buildPayment(o, BigDecimal.valueOf(500),
                LocalDateTime.of(2026, 5, 20, 0, 0), null);  // before due
        Payment p2 = buildPayment(o, BigDecimal.valueOf(500),
                LocalDateTime.of(2026, 5, 25, 0, 0), null);  // before due
        Payment p3 = buildPayment(o, BigDecimal.valueOf(500),
                LocalDateTime.of(2026, 6, 1, 0, 0), null);   // before due

        stubRepositories(List.of(), List.of(p1, p2, p3), Optional.empty(), List.of());

        MerchantFeatures features = service.computeFeatures(merchantId, AS_OF);

        assertThat(features.consecutiveOnTimeStreak()).isEqualTo(3);
    }

    // =========================================================================
    // CANCELLATION RATE — exact fractions
    // =========================================================================

    @Test
    void cancellationRate_twoDeliveredOneCancel_equalsOneThird() {
        UUID productId = UUID.randomUUID();
        Order delivered1 = buildFullyPaidOrder(productId, BigDecimal.valueOf(1000), AS_OF.minusDays(10));
        Order delivered2 = buildFullyPaidOrder(productId, BigDecimal.valueOf(1000), AS_OF.minusDays(20));
        Order cancelled = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.CANCELLED)
                .totalAmount(BigDecimal.valueOf(1000))
                .paidAmount(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .createdAt(AS_OF.minusDays(15))
                .paymentDueDate(AS_OF.plusDays(15).toLocalDate())
                .build();

        stubRepositories(List.of(delivered1, delivered2, cancelled), List.of(), Optional.empty(), List.of());

        MerchantFeatures features = service.computeFeatures(merchantId, AS_OF);

        assertThat(features.totalOrders()).isEqualTo(3);
        assertThat(features.cancellationRate()).isCloseTo(1.0 / 3.0, within(0.001));
    }

    // =========================================================================
    // AVERAGE ORDER VALUE — equal-amount orders
    // =========================================================================

    @Test
    void avgOrderValue_threeEqualOrders_returnsExactAmount() {
        UUID productId = UUID.randomUUID();
        List<Order> orders = List.of(
                buildFullyPaidOrder(productId, BigDecimal.valueOf(2500), AS_OF.minusDays(10)),
                buildFullyPaidOrder(productId, BigDecimal.valueOf(2500), AS_OF.minusDays(20)),
                buildFullyPaidOrder(productId, BigDecimal.valueOf(2500), AS_OF.minusDays(30))
        );

        stubRepositories(orders, List.of(), Optional.empty(), List.of());

        MerchantFeatures features = service.computeFeatures(merchantId, AS_OF);

        assertThat(features.avgOrderValue()).isEqualByComparingTo(BigDecimal.valueOf(2500));
    }

    // =========================================================================
    // PROFILE — category and geography edge cases
    // =========================================================================

    @Test
    void profile_nullCategory_returnsUnknown() {
        merchant.setCategory(null);
        stubRepositories(List.of(), List.of(), Optional.empty(), List.of());

        MerchantFeatures features = service.computeFeatures(merchantId, AS_OF);

        assertThat(features.businessCategoryEncoded()).isEqualTo("UNKNOWN");
    }

    @Test
    void profile_nullCity_returnsUnknown() {
        merchant.setCity(null);
        stubRepositories(List.of(), List.of(), Optional.empty(), List.of());

        MerchantFeatures features = service.computeFeatures(merchantId, AS_OF);

        assertThat(features.geographicCluster()).isEqualTo("UNKNOWN");
    }

    @Test
    void profile_tenureDays_computedFromCreatedAt() {
        // Merchant created 200 days before AS_OF
        merchant.setCreatedAt(AS_OF.minusDays(200));
        stubRepositories(List.of(), List.of(), Optional.empty(), List.of());

        MerchantFeatures features = service.computeFeatures(merchantId, AS_OF);

        assertThat(features.relationshipTenureDays()).isEqualTo(200);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void stubRepositories(List<Order> orders, List<Payment> payments,
                                  Optional<CreditLimit> activeLimit, List<CreditLimit> history) {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, AS_OF)).thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, AS_OF)).thenReturn(payments);
        when(creditLimitRepository.findActiveLimitByMerchantId(merchantId, AS_OF)).thenReturn(activeLimit);
        when(creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, AS_OF)).thenReturn(history);
    }

    private Order buildFullyPaidOrder(UUID productId, BigDecimal amount, LocalDateTime createdAt) {
        Product product = Product.builder().id(productId).name("Test Product").build();
        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .quantity(BigDecimal.ONE)
                .unitPrice(amount)
                .totalAmount(amount)
                .build();
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.DELIVERED)
                .totalAmount(amount)
                .paidAmount(amount)
                .items(new ArrayList<>(List.of(item)))
                .createdAt(createdAt)
                .paymentDueDate(createdAt.toLocalDate().plusDays(30))
                .build();
        item.setOrder(order);
        return order;
    }

    private Order buildOrderWithDueDate(LocalDateTime createdAt, LocalDate dueDate) {
        return Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.DELIVERED)
                .totalAmount(BigDecimal.valueOf(1000))
                .paidAmount(BigDecimal.valueOf(1000))
                .items(new ArrayList<>())
                .createdAt(createdAt)
                .paymentDueDate(dueDate)
                .build();
    }

    private Payment buildPayment(Order order, BigDecimal amount, LocalDateTime paidAt, PaymentMethod method) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .paymentMethod(method)
                .amount(amount)
                .status(PaymentStatus.COMPLETED)
                .createdAt(paidAt)
                .build();
    }

    private CreditLimit buildCreditLimit(BigDecimal approvedLimit, LocalDateTime createdAt) {
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
}
