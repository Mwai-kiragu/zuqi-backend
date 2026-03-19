package com.zuqi.ai.crm;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.OrderItemRepository;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.ProductRecommendationRepository;
import com.zuqi.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRecommendationServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductRecommendationRepository recommendationRepository;
    @Mock private DistributorRepository distributorRepository;

    private ProductRecommendationService service;
    private UUID distributorId;

    @BeforeEach
    void setUp() {
        service = new ProductRecommendationService(
                customerRepository, orderRepository, orderItemRepository,
                productRepository, recommendationRepository, distributorRepository);
        distributorId = UUID.randomUUID();
    }

    @Test
    void generateRecommendations_noCustomers_returnsZero() {
        Distributor distributor = new Distributor(); distributor.setId(distributorId);
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(customerRepository.findByDistributorIdAndActiveTrue(distributorId)).thenReturn(List.of());

        int result = service.generateRecommendations(distributorId);

        assertThat(result).isEqualTo(0);
    }

    @Test
    void generateRecommendations_noOrders_returnsZero() {
        Distributor distributor = new Distributor(); distributor.setId(distributorId);
        Customer customer = new Customer(); customer.setId(UUID.randomUUID());

        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(customerRepository.findByDistributorIdAndActiveTrue(distributorId))
                .thenReturn(List.of(customer));
        when(orderRepository.findByDistributorId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        int result = service.generateRecommendations(distributorId);

        // No orders means no co-purchase data, so 0 recommendations
        assertThat(result).isEqualTo(0);
    }

    @Test
    void generateRecommendations_distributorNotFound_throws() {
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.generateRecommendations(distributorId));
    }

    @Test
    void generateRecommendations_customerHasNoOrders_returnsZero() {
        Distributor distributor = new Distributor(); distributor.setId(distributorId);
        Customer customer = new Customer(); customer.setId(UUID.randomUUID());

        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(customerRepository.findByDistributorIdAndActiveTrue(distributorId))
                .thenReturn(List.of(customer));
        // Return one order for a different customer (no merchant field)
        when(orderRepository.findByDistributorId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        int result = service.generateRecommendations(distributorId);

        assertThat(result).isEqualTo(0);
    }
}
