package com.zuqi.ai.crm;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.customer.CustomerCategory;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAnalyticsFeatureServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;

    private CustomerAnalyticsFeatureServiceImpl service;

    private UUID customerId;
    private UUID distributorId;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new CustomerAnalyticsFeatureServiceImpl(
                customerRepository, orderRepository, paymentRepository);
        customerId = UUID.randomUUID();
        distributorId = UUID.randomUUID();

        CustomerCategory cat = new CustomerCategory();
        cat.setName("retail");

        customer = Customer.builder()
                .id(customerId)
                .businessName("Test Shop")
                .category(cat)
                .creditLimit(BigDecimal.valueOf(50_000))
                .createdAt(LocalDateTime.now().minusMonths(6))
                .build();
    }

    @Test
    void computeFeatures_emptyOrders_returnsZeroRevenue() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomerIdAndDistributorId(customerId, distributorId))
                .thenReturn(List.of());
        when(paymentRepository.findByMerchantId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(orderRepository.sumOutstandingByCustomerId(customerId)).thenReturn(BigDecimal.ZERO);

        CustomerAnalyticsFeatures f = service.computeFeatures(customerId, distributorId);

        assertThat(f.totalRevenue90d()).isEqualTo(0.0);
        assertThat(f.lifetimeRevenue()).isEqualTo(0.0);
        assertThat(f.orderCount90d()).isEqualTo(0.0);
        assertThat(f.daysSinceLastOrder()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void computeFeatures_tenureComputedFromCreatedAt() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomerIdAndDistributorId(customerId, distributorId))
                .thenReturn(List.of());
        when(paymentRepository.findByMerchantId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(orderRepository.sumOutstandingByCustomerId(customerId)).thenReturn(BigDecimal.ZERO);

        CustomerAnalyticsFeatures f = service.computeFeatures(customerId, distributorId);

        // Customer was created 6 months ago → tenure ≥ 5 months
        assertThat(f.tenureMonths()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void computeFeatures_categoryExtracted() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomerIdAndDistributorId(customerId, distributorId))
                .thenReturn(List.of());
        when(paymentRepository.findByMerchantId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(orderRepository.sumOutstandingByCustomerId(customerId)).thenReturn(BigDecimal.ZERO);

        CustomerAnalyticsFeatures f = service.computeFeatures(customerId, distributorId);

        assertThat(f.customerCategory()).isEqualTo("retail");
    }

    @Test
    void computeFeatures_withTwoOrders_computesRevenueAndFrequency() {
        Distributor distributor = new Distributor();
        distributor.setId(distributorId);

        Order o1 = Order.builder()
                .id(UUID.randomUUID())
                .merchant(customer)
                .distributor(distributor)
                .status(OrderStatus.DELIVERED)
                .totalAmount(BigDecimal.valueOf(10_000))
                .createdAt(LocalDateTime.now().minusDays(10))
                .build();
        Order o2 = Order.builder()
                .id(UUID.randomUUID())
                .merchant(customer)
                .distributor(distributor)
                .status(OrderStatus.DELIVERED)
                .totalAmount(BigDecimal.valueOf(20_000))
                .createdAt(LocalDateTime.now().minusDays(5))
                .build();

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomerIdAndDistributorId(customerId, distributorId))
                .thenReturn(List.of(o1, o2));
        when(paymentRepository.findByMerchantId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(orderRepository.sumOutstandingByCustomerId(customerId)).thenReturn(BigDecimal.ZERO);

        CustomerAnalyticsFeatures f = service.computeFeatures(customerId, distributorId);

        assertThat(f.totalRevenue90d()).isEqualTo(30_000.0);
        assertThat(f.lifetimeRevenue()).isEqualTo(30_000.0);
        assertThat(f.avgOrderValue()).isEqualTo(15_000.0);
        assertThat(f.orderCount90d()).isEqualTo(2.0);
        assertThat(f.daysSinceLastOrder()).isLessThanOrEqualTo(6);
    }

    @Test
    void computeFeatures_creditUtilization_computedWhenLimitPresent() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomerIdAndDistributorId(customerId, distributorId))
                .thenReturn(List.of());
        when(paymentRepository.findByMerchantId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        // 25000 outstanding on 50000 limit = 50% utilization
        when(orderRepository.sumOutstandingByCustomerId(customerId))
                .thenReturn(BigDecimal.valueOf(25_000));

        CustomerAnalyticsFeatures f = service.computeFeatures(customerId, distributorId);

        assertThat(f.creditUtilizationPct()).isEqualTo(50.0);
    }

    @Test
    void computeFeatures_noCreditLimit_utilizationIsZero() {
        customer.setCreditLimit(BigDecimal.ZERO);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomerIdAndDistributorId(customerId, distributorId))
                .thenReturn(List.of());
        when(paymentRepository.findByMerchantId(eq(customerId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        CustomerAnalyticsFeatures f = service.computeFeatures(customerId, distributorId);

        assertThat(f.creditUtilizationPct()).isEqualTo(0.0);
    }
}
