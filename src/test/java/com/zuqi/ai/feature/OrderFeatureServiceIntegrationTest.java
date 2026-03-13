package com.zuqi.ai.feature;

import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.merchant.MerchantCategory;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderItem;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.product.ProductCategory;
import com.zuqi.repository.MerchantRepository;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.PaymentRepository;
import com.zuqi.repository.ProductRepository;
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
 * Integration tests for OrderFeatureService verifying exact feature computations
 * against fully controlled, known test data.
 *
 * Covers:
 * - Lag features (qty1wAgo … qty4wAgo) with precisely placed order items
 * - Rolling averages (4-week and 12-week)
 * - Trend direction logic (INCREASING / STABLE / DECREASING)
 * - Kenya-specific temporal flags (holidays, payday week, Ramadan, Christmas season)
 * - Merchant credit status classification from payment behaviour
 * - Product price tier classification
 * - Merchant size tier classification
 *
 * Reference date: AS_OF = 2026-03-25 12:00 (Wednesday)
 * Week boundaries from that date:
 *   - 1 week ago: [Mar 16, Mar 23)
 *   - 2 weeks ago: [Mar 9,  Mar 16)
 *   - 3 weeks ago: [Mar 2,  Mar 9)
 *   - 4 weeks ago: [Feb 23, Mar 2)
 *
 * Blueprint reference: implementation_plan.md Task 1.5
 */
@ExtendWith(MockitoExtension.class)
class OrderFeatureServiceIntegrationTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private OrderFeatureServiceImpl service;

    // Fixed reference: Wednesday March 25, 2026
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 3, 25, 12, 0);

    private UUID merchantId;
    private UUID productId;
    private Merchant merchant;
    private Product product;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        productId = UUID.randomUUID();

        MerchantCategory merchantCategory = MerchantCategory.builder().id(1L).name("RETAIL").build();
        merchant = Merchant.builder()
                .id(merchantId)
                .businessName("Wanjiku Groceries")
                .city("Kisumu")
                .category(merchantCategory)
                .createdAt(AS_OF.minusDays(180))
                .build();

        ProductCategory productCategory = ProductCategory.builder().id(1L).name("PACKAGED_FOODS").build();
        product = Product.builder()
                .id(productId)
                .name("Unga Pembe 2kg")
                .category(productCategory)
                .unitPrice(BigDecimal.valueOf(180))
                .build();
    }

    // =========================================================================
    // LAG FEATURES — exact quantities per week
    // =========================================================================

    @Test
    void lagFeatures_ordersInKnownWeeks_computedExactly() {
        // Scenario:
        //   - Week 1 ago [Mar 16, Mar 23): 1 item on Mar 17, qty=3
        //   - Week 2 ago [Mar 9,  Mar 16): 1 item on Mar 10, qty=5
        //   - Week 3 ago [Mar 2,  Mar 9):  1 item on Mar 3,  qty=2
        //   - Week 4 ago [Feb 23, Mar 2):  no items → qty=0
        //
        // rollingAvg4w: items within last 4 weeks (cutoff=Feb 25): qty 3+5+2=10, /4 = 2.50
        // rollingAvg12w: same items (all within 12 weeks), /12 = 0.83
        // trendDirection: avg4w=2.50, avg12w=0.83 → diff=(2.50-0.83)/0.83 ≈ 2.01 > 0.15 → INCREASING

        List<Order> orders = List.of(
                buildOrderWithItems(LocalDateTime.of(2026, 3, 17, 9, 0), qty(3)),  // week 1 ago
                buildOrderWithItems(LocalDateTime.of(2026, 3, 10, 9, 0), qty(5)),  // week 2 ago
                buildOrderWithItems(LocalDateTime.of(2026, 3, 3, 9, 0), qty(2))    // week 3 ago
                // no order in week 4 ago [Feb 23, Mar 2)
        );

        stubRepositories(orders, List.of());

        DemandFeatures features = service.computeFeatures(merchantId, productId, AS_OF);

        assertThat(features.qty1wAgo()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(features.qty2wAgo()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(features.qty3wAgo()).isEqualByComparingTo(BigDecimal.valueOf(2));
        assertThat(features.qty4wAgo()).isEqualByComparingTo(BigDecimal.ZERO);

        // rollingAvg4w = (3+5+2) / 4 = 2.50
        assertThat(features.rollingAvg4w()).isEqualByComparingTo(new BigDecimal("2.50"));

        // rollingAvg12w = (3+5+2) / 12 = 0.83 (HALF_UP)
        assertThat(features.rollingAvg12w()).isEqualByComparingTo(new BigDecimal("0.83"));

        // Trend: avg4w (2.50) >> avg12w (0.83) → INCREASING
        assertThat(features.trendDirection()).isEqualTo("INCREASING");
    }

    @Test
    void lagFeatures_noOrders_allZeroAndStable() {
        stubRepositories(List.of(), List.of());

        DemandFeatures features = service.computeFeatures(merchantId, productId, AS_OF);

        assertThat(features.qty1wAgo()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(features.qty2wAgo()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(features.qty3wAgo()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(features.qty4wAgo()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(features.rollingAvg4w()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(features.rollingAvg12w()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(features.trendDirection()).isEqualTo("STABLE");
    }

    @Test
    void lagFeatures_multipleItemsSameWeek_quantitiesAreAddedUp() {
        // Two separate orders in week-1-ago, quantities 4 and 6 → qty1wAgo = 10
        List<Order> orders = List.of(
                buildOrderWithItems(LocalDateTime.of(2026, 3, 17, 9, 0), qty(4)),  // week 1 ago
                buildOrderWithItems(LocalDateTime.of(2026, 3, 19, 9, 0), qty(6))   // week 1 ago (same week)
        );

        stubRepositories(orders, List.of());

        DemandFeatures features = service.computeFeatures(merchantId, productId, AS_OF);

        assertThat(features.qty1wAgo()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(features.qty2wAgo()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // =========================================================================
    // TREND DIRECTION
    // =========================================================================

    @Test
    void trendDirection_recentEqualsLongTerm_returnsStable() {
        // Same quantity every week for 12 weeks → avg4w ≈ avg12w → STABLE
        //
        // NOTE: items must be placed at hour 13 (not 9).
        // The rolling-average cutoff is AS_OF.minusWeeks(N) which preserves AS_OF's 12:00 time.
        // An item at Feb 25 09:00 would be *before* the 4-week cutoff (Feb 25 12:00) and excluded.
        // Hour 13 is safely after the cutoff for boundary weeks (week 4 and week 12).
        List<Order> orders = new ArrayList<>();
        for (int w = 1; w <= 12; w++) {
            LocalDateTime orderDate = AS_OF.minusWeeks(w).withHour(13);
            orders.add(buildOrderWithItems(orderDate, qty(5)));
        }

        stubRepositories(orders, List.of());

        DemandFeatures features = service.computeFeatures(merchantId, productId, AS_OF);

        // avg4w = 20/4 = 5.00, avg12w = 60/12 = 5.00 → diff = 0 → STABLE
        assertThat(features.trendDirection()).isEqualTo("STABLE");
    }

    @Test
    void trendDirection_recentMuchLowerThanLongTerm_returnsDecreasing() {
        // Recent 4 weeks: 1 unit each. Long term 12 weeks: also includes 8 earlier weeks with 10 units.
        // avg4w  = (1+1+1+1) / 4 = 1.00
        // avg12w = (1+1+1+1 + 10+10+10+10+10+10+10+10) / 12 = 84/12 = 7.00
        // diff = (1.00 - 7.00) / 7.00 = -0.857 < -0.15 → DECREASING
        // Use hour 13 so boundary items (week 4 and week 12) are after their respective cutoffs
        List<Order> orders = new ArrayList<>();
        for (int w = 1; w <= 4; w++) {
            orders.add(buildOrderWithItems(AS_OF.minusWeeks(w).withHour(13), qty(1)));
        }
        for (int w = 5; w <= 12; w++) {
            orders.add(buildOrderWithItems(AS_OF.minusWeeks(w).withHour(13), qty(10)));
        }

        stubRepositories(orders, List.of());

        DemandFeatures features = service.computeFeatures(merchantId, productId, AS_OF);

        assertThat(features.trendDirection()).isEqualTo("DECREASING");
    }

    // =========================================================================
    // TEMPORAL FEATURES — Kenya calendar
    // =========================================================================

    @Test
    void temporalFeatures_newYearsDay_isHoliday() {
        // January 1, 2026 is a Kenya public holiday
        DemandFeatures features = computeFeaturesAt(LocalDateTime.of(2026, 1, 1, 12, 0));

        assertThat(features.isHoliday()).isTrue();
    }

    @Test
    void temporalFeatures_goodFriday2026_isHoliday() {
        // Good Friday 2026: April 3
        DemandFeatures features = computeFeaturesAt(LocalDateTime.of(2026, 4, 3, 12, 0));

        assertThat(features.isHoliday()).isTrue();
    }

    @Test
    void temporalFeatures_regularWednesdayMarch_notHoliday() {
        // March 25, 2026 is not a Kenya public holiday
        DemandFeatures features = computeFeaturesAt(AS_OF);

        assertThat(features.isHoliday()).isFalse();
    }

    @Test
    void temporalFeatures_day28_isPaydayWeek() {
        // Day of month >= 28 is payday week
        DemandFeatures features = computeFeaturesAt(LocalDateTime.of(2026, 3, 28, 12, 0));

        assertThat(features.isPaydayWeek()).isTrue();
    }

    @Test
    void temporalFeatures_day3_isPaydayWeek() {
        // Day of month <= 5 is payday week (carries over from previous month)
        DemandFeatures features = computeFeaturesAt(LocalDateTime.of(2026, 4, 3, 12, 0));

        assertThat(features.isPaydayWeek()).isTrue();
    }

    @Test
    void temporalFeatures_day15_notPaydayWeek() {
        DemandFeatures features = computeFeaturesAt(LocalDateTime.of(2026, 3, 15, 12, 0));

        assertThat(features.isPaydayWeek()).isFalse();
    }

    @Test
    void temporalFeatures_march15_isRamadan() {
        // Ramadan 2026: approximately March 1 – March 30
        DemandFeatures features = computeFeaturesAt(LocalDateTime.of(2026, 3, 15, 12, 0));

        assertThat(features.isRamadan()).isTrue();
    }

    @Test
    void temporalFeatures_april5_notRamadan() {
        DemandFeatures features = computeFeaturesAt(LocalDateTime.of(2026, 4, 5, 12, 0));

        assertThat(features.isRamadan()).isFalse();
    }

    @Test
    void temporalFeatures_december_isChristmasSeason() {
        DemandFeatures features = computeFeaturesAt(LocalDateTime.of(2026, 12, 1, 12, 0));

        assertThat(features.isChristmasSeason()).isTrue();
    }

    @Test
    void temporalFeatures_november_isChristmasSeason() {
        DemandFeatures features = computeFeaturesAt(LocalDateTime.of(2026, 11, 15, 12, 0));

        assertThat(features.isChristmasSeason()).isTrue();
    }

    @Test
    void temporalFeatures_march_notChristmasSeason() {
        DemandFeatures features = computeFeaturesAt(AS_OF);

        assertThat(features.isChristmasSeason()).isFalse();
    }

    @Test
    void temporalFeatures_dateComponents_computedCorrectly() {
        // AS_OF = Wednesday March 25, 2026
        // dayOfWeek: WEDNESDAY.getValue() = 3
        // weekOfMonth: (25-1)/7 + 1 = 3+1 = 4
        // monthOfYear: 3
        DemandFeatures features = computeFeaturesAt(AS_OF);

        assertThat(features.dayOfWeek()).isEqualTo(3);      // Wednesday
        assertThat(features.weekOfMonth()).isEqualTo(4);    // 4th week
        assertThat(features.monthOfYear()).isEqualTo(3);    // March
    }

    // =========================================================================
    // MERCHANT CREDIT STATUS
    // =========================================================================

    @Test
    void merchantCreditStatus_allOnTimePayments_returnsGood() {
        // >= 90% on-time → "GOOD"
        List<Payment> payments = List.of(
                buildPaymentOnTime(AS_OF.minusDays(10)),
                buildPaymentOnTime(AS_OF.minusDays(20)),
                buildPaymentOnTime(AS_OF.minusDays(30))
        );

        stubRepositories(List.of(), payments);

        DemandFeatures features = service.computeFeatures(merchantId, productId, AS_OF);

        assertThat(features.merchantCreditStatus()).isEqualTo("GOOD");
    }

    @Test
    void merchantCreditStatus_mostlyOnTime_returnsModerate() {
        // 3 on time + 2 late = 60% — between 70% and 90% threshold = "MODERATE"
        // Wait, 60% is below 70% so "POOR".
        // Use 3 on time + 1 late = 75% → "MODERATE"
        List<Payment> payments = List.of(
                buildPaymentOnTime(AS_OF.minusDays(10)),
                buildPaymentOnTime(AS_OF.minusDays(20)),
                buildPaymentOnTime(AS_OF.minusDays(30)),
                buildPaymentLate(AS_OF.minusDays(40))
        );

        stubRepositories(List.of(), payments);

        DemandFeatures features = service.computeFeatures(merchantId, productId, AS_OF);

        assertThat(features.merchantCreditStatus()).isEqualTo("MODERATE");
    }

    @Test
    void merchantCreditStatus_mostlyLate_returnsPoor() {
        // 1 on time + 3 late = 25% → "POOR" (below 70%)
        List<Payment> payments = List.of(
                buildPaymentOnTime(AS_OF.minusDays(10)),
                buildPaymentLate(AS_OF.minusDays(20)),
                buildPaymentLate(AS_OF.minusDays(30)),
                buildPaymentLate(AS_OF.minusDays(40))
        );

        stubRepositories(List.of(), payments);

        DemandFeatures features = service.computeFeatures(merchantId, productId, AS_OF);

        assertThat(features.merchantCreditStatus()).isEqualTo("POOR");
    }

    @Test
    void merchantCreditStatus_noPayments_returnsUnknown() {
        stubRepositories(List.of(), List.of());

        DemandFeatures features = service.computeFeatures(merchantId, productId, AS_OF);

        assertThat(features.merchantCreditStatus()).isEqualTo("UNKNOWN");
    }

    // =========================================================================
    // PRICE TIER
    // =========================================================================

    @Test
    void priceTier_below500_returnsLow() {
        product.setUnitPrice(BigDecimal.valueOf(200));
        stubRepositories(List.of(), List.of());

        assertThat(service.computeFeatures(merchantId, productId, AS_OF).priceTier()).isEqualTo("LOW");
    }

    @Test
    void priceTier_between500and2000_returnsMedium() {
        product.setUnitPrice(BigDecimal.valueOf(1000));
        stubRepositories(List.of(), List.of());

        assertThat(service.computeFeatures(merchantId, productId, AS_OF).priceTier()).isEqualTo("MEDIUM");
    }

    @Test
    void priceTier_above2000_returnsHigh() {
        product.setUnitPrice(BigDecimal.valueOf(3500));
        stubRepositories(List.of(), List.of());

        assertThat(service.computeFeatures(merchantId, productId, AS_OF).priceTier()).isEqualTo("HIGH");
    }

    // =========================================================================
    // PRODUCT CATEGORY / SHELF LIFE
    // =========================================================================

    @Test
    void shelfLife_packaged_foods_returns90Days() {
        // Product category already set to "PACKAGED_FOODS" in setUp
        stubRepositories(List.of(), List.of());

        DemandFeatures features = service.computeFeatures(merchantId, productId, AS_OF);

        assertThat(features.typicalShelfLifeDays()).isEqualTo(90);
    }

    @Test
    void shelfLife_dairy_returns7Days() {
        ProductCategory dairyCategory = ProductCategory.builder().id(2L).name("DAIRY").build();
        product.setCategory(dairyCategory);
        stubRepositories(List.of(), List.of());

        DemandFeatures features = service.computeFeatures(merchantId, productId, AS_OF);

        assertThat(features.typicalShelfLifeDays()).isEqualTo(7);
    }

    @Test
    void shelfLife_unknownCategory_returns60Days() {
        product.setCategory(null);
        stubRepositories(List.of(), List.of());

        DemandFeatures features = service.computeFeatures(merchantId, productId, AS_OF);

        assertThat(features.typicalShelfLifeDays()).isEqualTo(60);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void stubRepositories(List<Order> orders, List<Payment> payments) {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, AS_OF)).thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, AS_OF)).thenReturn(payments);
    }

    private void stubRepositoriesAt(LocalDateTime asOf, List<Order> orders, List<Payment> payments) {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOf)).thenReturn(orders);
        when(paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOf)).thenReturn(payments);
    }

    /**
     * Compute features at an arbitrary date for testing temporal flags.
     * No orders or payments needed for temporal feature tests.
     */
    private DemandFeatures computeFeaturesAt(LocalDateTime asOf) {
        stubRepositoriesAt(asOf, List.of(), List.of());
        return service.computeFeatures(merchantId, productId, asOf);
    }

    /**
     * Build an order containing a single item of the test product with the given quantity.
     */
    private Order buildOrderWithItems(LocalDateTime orderDate, BigDecimal quantity) {
        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .product(product)
                .quantity(quantity)
                .unitPrice(product.getUnitPrice())
                .totalAmount(product.getUnitPrice().multiply(quantity))
                .build();

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.DELIVERED)
                .totalAmount(product.getUnitPrice().multiply(quantity))
                .paidAmount(product.getUnitPrice().multiply(quantity))
                .items(new ArrayList<>(List.of(item)))
                .createdAt(orderDate)
                .paymentDueDate(orderDate.toLocalDate().plusDays(30))
                .build();

        item.setOrder(order);
        return order;
    }

    private BigDecimal qty(int value) {
        return BigDecimal.valueOf(value);
    }

    /** Payment paid on time (5 days before a 30-day due date). */
    private Payment buildPaymentOnTime(LocalDateTime orderDate) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .paymentDueDate(orderDate.toLocalDate().plusDays(30))
                .build();

        return Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .amount(BigDecimal.valueOf(1000))
                .status(PaymentStatus.COMPLETED)
                .createdAt(orderDate.plusDays(5))  // paid 5 days after order → well within 30-day due
                .build();
    }

    /** Payment paid late (35 days after order which has a 30-day due date). */
    private Payment buildPaymentLate(LocalDateTime orderDate) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .paymentDueDate(orderDate.toLocalDate().plusDays(30))
                .build();

        return Payment.builder()
                .id(UUID.randomUUID())
                .order(order)
                .amount(BigDecimal.valueOf(1000))
                .status(PaymentStatus.COMPLETED)
                .createdAt(orderDate.plusDays(35))  // 35 days after order → 5 days past the due date
                .build();
    }
}
