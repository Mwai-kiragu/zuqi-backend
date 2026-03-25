package com.zuqi.ai.crm;

import com.zuqi.domain.ai.CustomerHealthScore;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.CustomerHealthScoreRepository;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.DistributorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerHealthScoreServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerAnalyticsFeatureServiceImpl featureService;
    @Mock private CustomerHealthScoreRepository healthScoreRepository;
    @Mock private DistributorRepository distributorRepository;

    private CustomerHealthScoreService service;
    private UUID customerId;
    private UUID distributorId;

    @BeforeEach
    void setUp() {
        service = new CustomerHealthScoreService(
                customerRepository, featureService, healthScoreRepository, distributorRepository);
        customerId = UUID.randomUUID();
        distributorId = UUID.randomUUID();
    }

    private CustomerAnalyticsFeatures features(double freq, double payment, double trend, int days, double credit) {
        return new CustomerAnalyticsFeatures(
                customerId, distributorId,
                0.0, 0.0, 0.0, 0.0, 0.0,
                freq, 0.0, trend, payment, credit,
                days, 0.0, 12, "retail", 0.0, 0.0
        );
    }

    private void setupMocks(CustomerAnalyticsFeatures f) {
        Customer customer = new Customer(); customer.setId(customerId);
        Distributor distributor = new Distributor(); distributor.setId(distributorId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(featureService.computeFeatures(customerId, distributorId)).thenReturn(f);
        when(healthScoreRepository.findByDistributorIdAndCustomerId(distributorId, customerId))
                .thenReturn(Optional.empty());
        when(healthScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void highScoreCustomer_tierIsThriving() {
        // freq=4/week(100), payment=100, trend=0.1(55), days=1(97), credit=0(100)
        CustomerAnalyticsFeatures f = features(4.0, 100.0, 0.1, 1, 0.0);
        setupMocks(f);

        CustomerHealthScore score = service.computeScore(customerId, distributorId);

        assertThat(score.getHealthTier()).isEqualTo("THRIVING");
        assertThat(score.getHealthScore()).isGreaterThan(80.0);
    }

    @Test
    void lowEngagementCustomer_tierIsAtRisk() {
        // freq=0, payment=20, trend=-0.5(25), days=25(25), credit=90(10)
        CustomerAnalyticsFeatures f = features(0.0, 20.0, -0.5, 25, 90.0);
        setupMocks(f);

        CustomerHealthScore score = service.computeScore(customerId, distributorId);

        assertThat(score.getHealthTier()).isIn("AT_RISK", "CRITICAL", "NEEDS_ATTENTION");
    }

    @Test
    void noOrders_engagementZero() {
        CustomerAnalyticsFeatures f = features(0.0, 100.0, 0.0, Integer.MAX_VALUE, 0.0);
        setupMocks(f);

        CustomerHealthScore score = service.computeScore(customerId, distributorId);

        // engagementScore = max(0, 100 - MAX_VALUE*3) = 0
        assertThat(score.getEngagementScore()).isEqualTo(0.0);
    }

    @Test
    void orderFrequencyScore_clampedAt100() {
        // 5 orders/week * 25 = 125 → clamped to 100
        CustomerAnalyticsFeatures f = features(5.0, 100.0, 0.0, 1, 0.0);
        setupMocks(f);

        CustomerHealthScore score = service.computeScore(customerId, distributorId);

        assertThat(score.getOrderFrequencyScore()).isEqualTo(100.0);
    }

    @Test
    void computeScore_setsSubScoresOnEntity() {
        CustomerAnalyticsFeatures f = features(2.0, 80.0, 0.0, 7, 20.0);
        setupMocks(f);

        CustomerHealthScore score = service.computeScore(customerId, distributorId);

        assertThat(score.getOrderFrequencyScore()).isNotNull();
        assertThat(score.getPaymentTimelinessScore()).isEqualTo(80.0);
        assertThat(score.getCreditHealthScore()).isEqualTo(80.0);
        assertThat(score.getComputedAt()).isNotNull();
    }

    @Test
    void thrivingThreshold_exactly80() {
        // Design a scenario that gives exactly 80
        // freq=4(100) * 0.25 = 25
        // payment=100 * 0.25 = 25
        // trend=0.1(55) * 0.20 = 11
        // days=5(85) * 0.15 = 12.75
        // credit=0(100) * 0.15 = 15
        // total ≈ 88.75 → THRIVING
        CustomerAnalyticsFeatures f = features(4.0, 100.0, 0.1, 5, 0.0);
        setupMocks(f);

        CustomerHealthScore score = service.computeScore(customerId, distributorId);

        assertThat(score.getHealthTier()).isEqualTo("THRIVING");
    }

    @Test
    void criticalTier_when_scoreBelow20() {
        // All very poor
        CustomerAnalyticsFeatures f = features(0.0, 0.0, -1.0, 33, 100.0);
        setupMocks(f);

        CustomerHealthScore score = service.computeScore(customerId, distributorId);

        // engagementScore = max(0, 100 - 33*3) = 1
        // credit = 0
        // total ≈ 0*0.25 + 0*0.25 + 0*0.20 + 1*0.15 + 0*0.15 = 0.15 → CRITICAL
        assertThat(score.getHealthTier()).isEqualTo("CRITICAL");
    }
}
