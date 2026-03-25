package com.zuqi.ai.crm;

import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.CustomerSegmentRepository;
import com.zuqi.repository.DistributorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSegmentationServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerAnalyticsFeatureServiceImpl featureService;
    @Mock private SegmentationFeatureBuilder featureBuilder;
    @Mock private ModelLoaderService modelLoader;
    @Mock private ModelPhaseService phaseService;
    @Mock private DataPhaseTracker phaseTracker;
    @Mock private CustomerSegmentRepository customerSegmentRepository;
    @Mock private DistributorRepository distributorRepository;

    private CustomerSegmentationService service;
    private UUID distributorId;

    @BeforeEach
    void setUp() {
        service = new CustomerSegmentationService(
                customerRepository, featureService, featureBuilder,
                modelLoader, phaseService, phaseTracker,
                customerSegmentRepository, distributorRepository);
        distributorId = UUID.randomUUID();
    }

    @Test
    void segmentAll_noCustomers_returnsZero() {
        when(customerRepository.findByDistributorIdAndActiveTrue(distributorId)).thenReturn(List.of());

        int result = service.segmentAll(distributorId);

        assertThat(result).isEqualTo(0);
    }

    @Test
    void segmentAll_noModel_usesHeuristicFallback() {
        Distributor distributor = new Distributor();
        distributor.setId(distributorId);

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());

        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(customerRepository.findByDistributorIdAndActiveTrue(distributorId))
                .thenReturn(List.of(customer));
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(phaseTracker.getPhase(any(), any())).thenReturn(com.zuqi.domain.ai.DataPhase.SYNTHETIC);

        CustomerAnalyticsFeatures features = new CustomerAnalyticsFeatures(
                customer.getId(), distributorId,
                150_000.0, 400_000.0, 150_000.0, 300_000.0, 400_000.0,
                3.0, 50_000.0, 0.2, 90.0, 25.0, 3, 0.9, 18,
                "retail", 12.0, 36.0
        );
        when(featureService.computeFeatures(customer.getId(), distributorId)).thenReturn(features);
        when(customerSegmentRepository.findByDistributorIdAndCustomerId(any(), any()))
                .thenReturn(Optional.empty());

        com.zuqi.domain.ai.CustomerSegment savedSegment = new com.zuqi.domain.ai.CustomerSegment();
        when(customerSegmentRepository.save(any())).thenReturn(savedSegment);

        int result = service.segmentAll(distributorId);

        assertThat(result).isEqualTo(1);
    }

    @Test
    void segmentAll_multipleCustomers_returnsCorrectCount() {
        Distributor distributor = new Distributor();
        distributor.setId(distributorId);

        Customer c1 = new Customer(); c1.setId(UUID.randomUUID());
        Customer c2 = new Customer(); c2.setId(UUID.randomUUID());
        Customer c3 = new Customer(); c3.setId(UUID.randomUUID());

        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(customerRepository.findByDistributorIdAndActiveTrue(distributorId))
                .thenReturn(List.of(c1, c2, c3));
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(phaseTracker.getPhase(any(), any())).thenReturn(com.zuqi.domain.ai.DataPhase.SYNTHETIC);

        CustomerAnalyticsFeatures f = new CustomerAnalyticsFeatures(
                UUID.randomUUID(), distributorId,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 100.0, 0.0,
                Integer.MAX_VALUE, 0.0, 0, "UNKNOWN", 0.0, 0.0
        );
        when(featureService.computeFeatures(any(), any())).thenReturn(f);
        when(customerSegmentRepository.findByDistributorIdAndCustomerId(any(), any()))
                .thenReturn(Optional.empty());
        when(customerSegmentRepository.save(any())).thenReturn(new com.zuqi.domain.ai.CustomerSegment());

        int result = service.segmentAll(distributorId);

        assertThat(result).isEqualTo(3);
    }
}
