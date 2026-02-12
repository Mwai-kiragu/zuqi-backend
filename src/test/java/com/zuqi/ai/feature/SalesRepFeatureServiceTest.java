package com.zuqi.ai.feature;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.user.User;
import com.zuqi.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesRepFeatureServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private SalesRepFeatureServiceImpl salesRepFeatureService;

    private UUID salesRepId;
    private User salesRep;
    private Distributor distributor;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    @BeforeEach
    void setUp() {
        salesRepId = UUID.randomUUID();
        periodStart = LocalDateTime.of(2026, 2, 1, 0, 0);
        periodEnd = LocalDateTime.of(2026, 2, 28, 23, 59);

        distributor = Distributor.builder()
                .id(UUID.randomUUID())
                .name("Test Distributor")
                .build();

        salesRep = User.builder()
                .id(salesRepId)
                .email("salesrep@example.com")
                .firstName("Sales")
                .lastName("Rep")
                .password("password")
                .build();
    }

    @Test
    void shouldComputeBasicSalesRepFeatures() {
        // Given
        List<Merchant> assignedMerchants = createAssignedMerchants(5);
        List<Order> orders = createOrders(3, assignedMerchants.subList(0, 3));
        List<Payment> payments = createPayments(2, orders.subList(0, 2));

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(salesRep));
        when(merchantRepository.findAll()).thenReturn(assignedMerchants);
        when(orderRepository.findAll()).thenReturn(orders);
        when(paymentRepository.findAll()).thenReturn(payments);

        // When
        SalesRepFeatures features = salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd);

        // Then
        assertThat(features).isNotNull();
        assertThat(features.salesRepId()).isEqualTo(salesRepId);
        assertThat(features.periodStart()).isEqualTo(periodStart);
        assertThat(features.periodEnd()).isEqualTo(periodEnd);
        assertThat(features.ordersCreated()).isEqualTo(3);
        assertThat(features.activeMerchants()).isEqualTo(5);
    }

    @Test
    void shouldComputeVisitMetrics() {
        // Given - 5 assigned merchants, 3 unique merchants placed orders
        List<Merchant> assignedMerchants = createAssignedMerchants(5);
        List<Order> orders = createOrders(3, assignedMerchants.subList(0, 3));

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(salesRep));
        when(merchantRepository.findAll()).thenReturn(assignedMerchants);
        when(orderRepository.findAll()).thenReturn(orders);
        when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        SalesRepFeatures features = salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd);

        // Then
        assertThat(features.visitCount()).isEqualTo(3); // 3 unique merchants visited
        assertThat(features.visitTarget()).isGreaterThan(0); // Based on 4 weeks * 5 merchants
        assertThat(features.visitCountVsTarget()).isGreaterThan(0.0);
    }

    @Test
    void shouldComputeOrderConversionRate() {
        // Given - 5 visits, 3 orders
        List<Merchant> assignedMerchants = createAssignedMerchants(5);
        List<Order> orders = createOrders(3, assignedMerchants.subList(0, 3));

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(salesRep));
        when(merchantRepository.findAll()).thenReturn(assignedMerchants);
        when(orderRepository.findAll()).thenReturn(orders);
        when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        SalesRepFeatures features = salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd);

        // Then
        // 3 orders / 3 visits = 100% conversion
        assertThat(features.orderConversionRate()).isEqualTo(100.0);
    }

    @Test
    void shouldComputeTotalOrderValue() {
        // Given - 3 orders worth 1000 each
        List<Merchant> assignedMerchants = createAssignedMerchants(3);
        List<Order> orders = createOrdersWithValue(3, assignedMerchants, BigDecimal.valueOf(1000));

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(salesRep));
        when(merchantRepository.findAll()).thenReturn(assignedMerchants);
        when(orderRepository.findAll()).thenReturn(orders);
        when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        SalesRepFeatures features = salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd);

        // Then
        assertThat(features.totalOrderValue()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        assertThat(features.avgOrderValue()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    void shouldComputeNewMerchantsAcquired() {
        // Given - 5 assigned merchants, 2 created in period
        List<Merchant> assignedMerchants = createAssignedMerchants(5);
        List<Merchant> newMerchants = createNewMerchantsInPeriod(2);
        assignedMerchants.addAll(newMerchants);

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(salesRep));
        when(merchantRepository.findAll()).thenReturn(assignedMerchants);
        when(orderRepository.findAll()).thenReturn(Collections.emptyList());
        when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        SalesRepFeatures features = salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd);

        // Then
        assertThat(features.newMerchantsAcquired()).isEqualTo(2);
        assertThat(features.activeMerchants()).isEqualTo(7); // 5 old + 2 new
    }

    @Test
    void shouldComputeMerchantRetentionRate() {
        // Given - 5 assigned merchants, 3 placed orders
        List<Merchant> assignedMerchants = createAssignedMerchants(5);
        List<Order> orders = createOrders(3, assignedMerchants.subList(0, 3));

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(salesRep));
        when(merchantRepository.findAll()).thenReturn(assignedMerchants);
        when(orderRepository.findAll()).thenReturn(orders);
        when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        SalesRepFeatures features = salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd);

        // Then
        assertThat(features.merchantRetentionRate()).isEqualTo(60.0); // 3/5 = 60%
    }

    @Test
    void shouldComputeCollectionMetrics() {
        // Given - 3 orders worth 1000 each, 2 payments worth 800 each
        List<Merchant> assignedMerchants = createAssignedMerchants(3);
        List<Order> orders = createOrdersWithValue(3, assignedMerchants, BigDecimal.valueOf(1000));
        List<Payment> payments = createPaymentsWithValue(2, orders.subList(0, 2), BigDecimal.valueOf(800));

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(salesRep));
        when(merchantRepository.findAll()).thenReturn(assignedMerchants);
        when(orderRepository.findAll()).thenReturn(orders);
        when(paymentRepository.findAll()).thenReturn(payments);

        // When
        SalesRepFeatures features = salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd);

        // Then
        assertThat(features.collectionsTarget()).isEqualByComparingTo(BigDecimal.valueOf(3000)); // Total orders
        assertThat(features.collectionsActual()).isEqualByComparingTo(BigDecimal.valueOf(1600)); // Total payments
        assertThat(features.collectionRate()).isGreaterThan(50.0).isLessThan(55.0); // ~53.33%
        assertThat(features.paymentsCollected()).isEqualTo(2);
    }

    @Test
    void shouldComputeRouteAdherencePct() {
        // Given
        List<Merchant> assignedMerchants = createAssignedMerchants(5);
        List<Order> orders = createOrders(3, assignedMerchants.subList(0, 3));

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(salesRep));
        when(merchantRepository.findAll()).thenReturn(assignedMerchants);
        when(orderRepository.findAll()).thenReturn(orders);
        when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        SalesRepFeatures features = salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd);

        // Then
        assertThat(features.routeVisitsPlanned()).isGreaterThan(0);
        assertThat(features.routeVisitsCompleted()).isEqualTo(3);
        assertThat(features.routeAdherencePct()).isGreaterThan(0.0);
    }

    @Test
    void shouldComputeTerritoryPenetrationPct() {
        // Given - 10 assigned merchants, visited 6 unique
        List<Merchant> assignedMerchants = createAssignedMerchants(10);
        List<Order> orders = createOrders(6, assignedMerchants.subList(0, 6));

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(salesRep));
        when(merchantRepository.findAll()).thenReturn(assignedMerchants);
        when(orderRepository.findAll()).thenReturn(orders);
        when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        SalesRepFeatures features = salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd);

        // Then
        assertThat(features.assignedTerritoryMerchants()).isEqualTo(10);
        assertThat(features.visitedTerritoryMerchants()).isEqualTo(6);
        assertThat(features.territoryPenetrationPct()).isEqualTo(60.0); // 6/10 = 60%
    }

    @Test
    void shouldHandleNoOrders() {
        // Given
        List<Merchant> assignedMerchants = createAssignedMerchants(5);

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(salesRep));
        when(merchantRepository.findAll()).thenReturn(assignedMerchants);
        when(orderRepository.findAll()).thenReturn(Collections.emptyList());
        when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        SalesRepFeatures features = salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd);

        // Then
        assertThat(features.ordersCreated()).isEqualTo(0);
        assertThat(features.totalOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(features.avgOrderValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(features.visitCount()).isEqualTo(0);
        assertThat(features.orderConversionRate()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleNoAssignedMerchants() {
        // Given
        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(salesRep));
        when(merchantRepository.findAll()).thenReturn(Collections.emptyList());
        when(orderRepository.findAll()).thenReturn(Collections.emptyList());
        when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        SalesRepFeatures features = salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd);

        // Then
        assertThat(features.activeMerchants()).isEqualTo(0);
        assertThat(features.merchantRetentionRate()).isEqualTo(0.0);
        assertThat(features.territoryPenetrationPct()).isEqualTo(0.0);
    }

    @Test
    void shouldThrowException_WhenSalesRepNotFound() {
        // Given
        when(userRepository.findById(salesRepId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> salesRepFeatureService.computeFeatures(salesRepId, periodStart, periodEnd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sales rep not found");
    }

    @Test
    void shouldEvictCache() {
        // When
        salesRepFeatureService.evictCache(salesRepId, periodStart, periodEnd);

        // Then - no exception
        verify(userRepository, never()).findById(any());
    }

    @Test
    void shouldEvictRepCache() {
        // When
        salesRepFeatureService.evictRepCache(salesRepId);

        // Then - no exception
        verify(userRepository, never()).findById(any());
    }

    // ==================== Helper Methods ====================

    private List<Merchant> createAssignedMerchants(int count) {
        List<Merchant> merchants = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Merchant merchant = Merchant.builder()
                    .id(UUID.randomUUID())
                    .businessName("Merchant " + i)
                    .phone("+254700000" + i)
                    .distributor(distributor)
                    .assignedSalesRep(salesRep)
                    .active(true)
                    .createdAt(periodStart.minusDays(30)) // Created before period
                    .build();
            merchants.add(merchant);
        }
        return merchants;
    }

    private List<Merchant> createNewMerchantsInPeriod(int count) {
        List<Merchant> merchants = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Merchant merchant = Merchant.builder()
                    .id(UUID.randomUUID())
                    .businessName("New Merchant " + i)
                    .phone("+254700100" + i)
                    .distributor(distributor)
                    .assignedSalesRep(salesRep)
                    .active(true)
                    .createdAt(periodStart.plusDays(i + 1)) // Created in period
                    .build();
            merchants.add(merchant);
        }
        return merchants;
    }

    private List<Order> createOrders(int count, List<Merchant> merchants) {
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < count && i < merchants.size(); i++) {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchants.get(i))
                    .salesRep(salesRep)
                    .distributor(distributor)
                    .totalAmount(BigDecimal.valueOf(1000 + i * 100))
                    .createdAt(periodStart.plusDays(i + 1))
                    .build();
            orders.add(order);
        }
        return orders;
    }

    private List<Order> createOrdersWithValue(int count, List<Merchant> merchants, BigDecimal value) {
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < count && i < merchants.size(); i++) {
            Order order = Order.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchants.get(i))
                    .salesRep(salesRep)
                    .distributor(distributor)
                    .totalAmount(value)
                    .createdAt(periodStart.plusDays(i + 1))
                    .build();
            orders.add(order);
        }
        return orders;
    }

    private List<Payment> createPayments(int count, List<Order> orders) {
        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < count && i < orders.size(); i++) {
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .order(orders.get(i))
                    .merchant(orders.get(i).getMerchant())
                    .amount(orders.get(i).getTotalAmount())
                    .createdAt(periodStart.plusDays(i + 2))
                    .build();
            payments.add(payment);
        }
        return payments;
    }

    private List<Payment> createPaymentsWithValue(int count, List<Order> orders, BigDecimal value) {
        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < count && i < orders.size(); i++) {
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .order(orders.get(i))
                    .merchant(orders.get(i).getMerchant())
                    .amount(value)
                    .createdAt(periodStart.plusDays(i + 2))
                    .build();
            payments.add(payment);
        }
        return payments;
    }
}
