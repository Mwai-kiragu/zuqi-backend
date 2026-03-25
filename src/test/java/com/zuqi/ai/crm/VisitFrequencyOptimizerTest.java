package com.zuqi.ai.crm;

import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.domain.ai.ChurnPrediction;
import com.zuqi.domain.ai.VisitRecommendation;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.user.User;
import com.zuqi.repository.ChurnPredictionRepository;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.UserRepository;
import com.zuqi.repository.VisitRecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.regression.Regressor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitFrequencyOptimizerTest {

    // Functional interface to side-step Model.predict() overload ambiguity
    @FunctionalInterface
    interface SinglePredictor {
        Prediction<Regressor> predict(Example<Regressor> example);
    }

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerAnalyticsFeatureServiceImpl featureService;
    @Mock private VisitFeatureBuilder visitFeatureBuilder;
    @Mock private ModelLoaderService modelLoader;
    @Mock private ModelPhaseService phaseService;
    @Mock private ChurnPredictionRepository churnPredictionRepository;
    @Mock private VisitRecommendationRepository visitRecommendationRepository;
    @Mock private DistributorRepository distributorRepository;
    @Mock private UserRepository userRepository;
    private VisitFrequencyOptimizer optimizer;
    private UUID salesRepId;
    private UUID distributorId;

    @BeforeEach
    void setUp() {
        optimizer = new VisitFrequencyOptimizer(
                customerRepository, featureService, visitFeatureBuilder,
                modelLoader, phaseService, churnPredictionRepository,
                visitRecommendationRepository, distributorRepository, userRepository);

        salesRepId = UUID.randomUUID();
        distributorId = UUID.randomUUID();
    }

    @Test
    void generateVisitPlan_salesRepNotFound_throws() {
        when(userRepository.findById(salesRepId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> optimizer.generateVisitPlan(salesRepId, distributorId));
    }

    @Test
    void generateVisitPlan_distributorNotFound_throws() {
        User rep = new User(); rep.setId(salesRepId);
        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(rep));
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> optimizer.generateVisitPlan(salesRepId, distributorId));
    }

    @Test
    void generateVisitPlan_noCustomers_returnsEmpty() {
        User rep = new User(); rep.setId(salesRepId);
        Distributor distributor = new Distributor(); distributor.setId(distributorId);

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(rep));
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(customerRepository.findByAssignedSalesRepIdAndActiveTrue(eq(salesRepId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<VisitRecommendation> result = optimizer.generateVisitPlan(salesRepId, distributorId);

        assertThat(result).isEmpty();
    }

    @Test
    void generateVisitPlan_noModel_usesHeuristicAndSaves() {
        User rep = new User(); rep.setId(salesRepId);
        Distributor distributor = new Distributor(); distributor.setId(distributorId);
        Customer customer = new Customer(); customer.setId(UUID.randomUUID());

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(rep));
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(customerRepository.findByAssignedSalesRepIdAndActiveTrue(eq(salesRepId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(featureService.computeFeatures(any(), any())).thenReturn(minimalFeatures());
        when(churnPredictionRepository.findByDistributorIdAndCustomerId(any(), any()))
                .thenReturn(Optional.empty());
        when(visitRecommendationRepository.findByDistributorIdAndCustomerId(any(), any()))
                .thenReturn(Optional.empty());
        when(phaseService.isSyntheticPhase(any())).thenReturn(false);
        when(visitRecommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<VisitRecommendation> result = optimizer.generateVisitPlan(salesRepId, distributorId);

        assertThat(result).hasSize(1);
        verify(visitRecommendationRepository).save(any());
    }

    @Test
    void generateVisitPlan_highChurnCustomer_setsFrequencyTwo() {
        User rep = new User(); rep.setId(salesRepId);
        Distributor distributor = new Distributor(); distributor.setId(distributorId);
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(); customer.setId(customerId);

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(rep));
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(customerRepository.findByAssignedSalesRepIdAndActiveTrue(eq(salesRepId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(featureService.computeFeatures(any(), any())).thenReturn(minimalFeatures());
        when(phaseService.isSyntheticPhase(any())).thenReturn(true);

        ChurnPrediction churn = new ChurnPrediction();
        churn.setRiskTier("HIGH");
        when(churnPredictionRepository.findByDistributorIdAndCustomerId(distributorId, customerId))
                .thenReturn(Optional.of(churn));
        when(visitRecommendationRepository.findByDistributorIdAndCustomerId(any(), any()))
                .thenReturn(Optional.empty());
        when(visitRecommendationRepository.save(any(VisitRecommendation.class))).thenAnswer(inv -> {
            VisitRecommendation r = inv.getArgument(0);
            assertThat(r.getRecommendedFrequencyPerWeek()).isEqualTo(2.0);
            assertThat(r.getDataPhase()).isEqualTo("SYNTHETIC");
            return r;
        });

        List<VisitRecommendation> result = optimizer.generateVisitPlan(salesRepId, distributorId);
        assertThat(result).hasSize(1);
    }

    @Test
    void generateVisitPlan_criticalChurnCustomer_setsFrequencyThree() {
        User rep = new User(); rep.setId(salesRepId);
        Distributor distributor = new Distributor(); distributor.setId(distributorId);
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(); customer.setId(customerId);

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(rep));
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(customerRepository.findByAssignedSalesRepIdAndActiveTrue(eq(salesRepId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(featureService.computeFeatures(any(), any())).thenReturn(minimalFeatures());
        when(phaseService.isSyntheticPhase(any())).thenReturn(false);

        ChurnPrediction churn = new ChurnPrediction();
        churn.setRiskTier("CRITICAL");
        when(churnPredictionRepository.findByDistributorIdAndCustomerId(distributorId, customerId))
                .thenReturn(Optional.of(churn));
        when(visitRecommendationRepository.findByDistributorIdAndCustomerId(any(), any()))
                .thenReturn(Optional.empty());
        when(visitRecommendationRepository.save(any(VisitRecommendation.class))).thenAnswer(inv -> {
            VisitRecommendation r = inv.getArgument(0);
            assertThat(r.getRecommendedFrequencyPerWeek()).isEqualTo(3.0);
            return r;
        });

        List<VisitRecommendation> result = optimizer.generateVisitPlan(salesRepId, distributorId);
        assertThat(result).hasSize(1);
    }

    @Test
    void generateVisitPlan_withModel_picksHighestConversionDay() {
        User rep = new User(); rep.setId(salesRepId);
        Distributor distributor = new Distributor(); distributor.setId(distributorId);
        Customer customer = new Customer(); customer.setId(UUID.randomUUID());

        // Use inline model mock — same pattern as SmartPricingRecommenderTest
        Model<Regressor> inlineModel = stubPredict(ex -> {
            Regressor output = new Regressor("order_conversion", 0.5);
            return new Prediction<>(output, 0, ex);
        });

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(rep));
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(customerRepository.findByAssignedSalesRepIdAndActiveTrue(eq(salesRepId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));
        when(modelLoader.loadModel(any())).thenReturn(inlineModel);
        when(featureService.computeFeatures(any(), any())).thenReturn(minimalFeatures());

        @SuppressWarnings("unchecked")
        Example<Regressor> mockEx = org.mockito.Mockito.mock(Example.class);
        when(visitFeatureBuilder.buildExample(any(), anyInt(), anyDouble(),
                anyBoolean(), anyBoolean())).thenReturn(mockEx);
        when(phaseService.isSyntheticPhase(any())).thenReturn(false);
        when(phaseService.applyModifier(anyDouble(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(churnPredictionRepository.findByDistributorIdAndCustomerId(any(), any()))
                .thenReturn(Optional.empty());
        when(visitRecommendationRepository.findByDistributorIdAndCustomerId(any(), any()))
                .thenReturn(Optional.empty());
        when(visitRecommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<VisitRecommendation> result = optimizer.generateVisitPlan(salesRepId, distributorId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRecommendedDay()).isBetween(1, 7);
        assertThat(result.get(0).getPredictedConversion()).isBetween(0.0, 1.0);
    }

    @Test
    void generateVisitPlan_existingRecommendation_updatesInPlace() {
        User rep = new User(); rep.setId(salesRepId);
        Distributor distributor = new Distributor(); distributor.setId(distributorId);
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer(); customer.setId(customerId);

        when(userRepository.findById(salesRepId)).thenReturn(Optional.of(rep));
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(customerRepository.findByAssignedSalesRepIdAndActiveTrue(eq(salesRepId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customer)));
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(featureService.computeFeatures(any(), any())).thenReturn(minimalFeatures());
        when(phaseService.isSyntheticPhase(any())).thenReturn(false);
        when(churnPredictionRepository.findByDistributorIdAndCustomerId(any(), any()))
                .thenReturn(Optional.empty());

        VisitRecommendation existing = VisitRecommendation.builder()
                .distributor(distributor).salesRep(rep).customer(customer).build();
        existing.setRecommendedDay(3);
        when(visitRecommendationRepository.findByDistributorIdAndCustomerId(distributorId, customerId))
                .thenReturn(Optional.of(existing));
        when(visitRecommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<VisitRecommendation> result = optimizer.generateVisitPlan(salesRepId, distributorId);

        assertThat(result).hasSize(1);
        verify(visitRecommendationRepository).save(existing);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CustomerAnalyticsFeatures minimalFeatures() {
        return new CustomerAnalyticsFeatures(
                UUID.randomUUID(),  // customerId
                UUID.randomUUID(),  // distributorId
                0.0,                // totalRevenue90d
                0.0,                // lifetimeRevenue
                0.0,                // revenue3m
                0.0,                // revenue6m
                0.0,                // revenue12m
                0.0,                // orderFrequencyPerWeek
                0.0,                // avgOrderValue
                0.0,                // revenueTrendSlope
                100.0,              // paymentTimelinessScore
                0.0,                // creditUtilizationPct
                30,                 // daysSinceLastOrder
                0.0,                // productDiversityScore
                6,                  // tenureMonths
                "grocery",          // customerCategory
                0.0,                // orderCount30d
                0.0                 // orderCount90d
        );
    }

    @SuppressWarnings("unchecked")
    private Model<Regressor> stubPredict(SinglePredictor fn) {
        Model<Regressor> m = org.mockito.Mockito.mock(Model.class);
        org.mockito.Mockito.lenient()
                .doAnswer(inv -> fn.predict((Example<Regressor>) inv.getArgument(0)))
                .when(m).predict((Example<Regressor>) argThat(arg -> arg instanceof Example));
        return m;
    }
}
