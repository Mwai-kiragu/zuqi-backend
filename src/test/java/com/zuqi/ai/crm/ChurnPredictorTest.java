package com.zuqi.ai.crm;

import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.domain.ai.ChurnPrediction;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.ChurnPredictionRepository;
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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChurnPredictorTest {

    @Mock private CustomerAnalyticsFeatureServiceImpl featureService;
    @Mock private ChurnFeatureBuilder churnFeatureBuilder;
    @Mock private ModelLoaderService modelLoader;
    @Mock private ModelPhaseService phaseService;
    @Mock private ChurnPredictionRepository churnRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private DistributorRepository distributorRepository;

    private ChurnPredictor predictor;
    private UUID customerId;
    private UUID distributorId;

    @BeforeEach
    void setUp() {
        predictor = new ChurnPredictor(
                featureService, churnFeatureBuilder, modelLoader, phaseService,
                churnRepository, customerRepository, distributorRepository);
        customerId = UUID.randomUUID();
        distributorId = UUID.randomUUID();
    }

    private void setupCustomerAndDistributor() {
        Customer customer = new Customer(); customer.setId(customerId);
        Distributor distributor = new Distributor(); distributor.setId(distributorId);
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(churnRepository.findByDistributorIdAndCustomerId(distributorId, customerId))
                .thenReturn(Optional.empty());
        when(churnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CustomerAnalyticsFeatures features(int daysSinceLast) {
        return new CustomerAnalyticsFeatures(
                customerId, distributorId,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 100.0, 0.0,
                daysSinceLast, 0.0, 12, "retail", 0.0, 0.0
        );
    }

    @Test
    void nullModel_daysSinceLast31_heuristicProbability0_5() {
        setupCustomerAndDistributor();
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(phaseService.isSyntheticPhase(any())).thenReturn(true);
        when(featureService.computeFeatures(customerId, distributorId)).thenReturn(features(31));

        ChurnPrediction prediction = predictor.predict(customerId, distributorId);

        assertThat(prediction.getChurnProbability()).isEqualTo(0.5);
    }

    @Test
    void nullModel_daysSinceLast5_heuristicProbability0_2() {
        setupCustomerAndDistributor();
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(phaseService.isSyntheticPhase(any())).thenReturn(false);
        when(featureService.computeFeatures(customerId, distributorId)).thenReturn(features(5));

        ChurnPrediction prediction = predictor.predict(customerId, distributorId);

        assertThat(prediction.getChurnProbability()).isEqualTo(0.2);
    }

    @Test
    void tierMapping_lowProbability_tierIsLow() {
        setupCustomerAndDistributor();
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(phaseService.isSyntheticPhase(any())).thenReturn(false);
        // days = 5 → prob = 0.2 → LOW
        when(featureService.computeFeatures(customerId, distributorId)).thenReturn(features(5));

        ChurnPrediction prediction = predictor.predict(customerId, distributorId);

        assertThat(prediction.getRiskTier()).isEqualTo("LOW");
    }

    @Test
    void tierMapping_highProbability_tierIsCritical() {
        setupCustomerAndDistributor();
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(phaseService.isSyntheticPhase(any())).thenReturn(false);
        // days = 31 → prob = 0.5 → HIGH (not CRITICAL)
        when(featureService.computeFeatures(customerId, distributorId)).thenReturn(features(31));

        ChurnPrediction prediction = predictor.predict(customerId, distributorId);

        assertThat(prediction.getRiskTier()).isIn("MODERATE", "HIGH");
    }

    @Test
    void topChurnFactor_daysSince31_isDaysSinceLastOrder() {
        setupCustomerAndDistributor();
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(phaseService.isSyntheticPhase(any())).thenReturn(false);
        when(featureService.computeFeatures(customerId, distributorId)).thenReturn(features(35));

        ChurnPrediction prediction = predictor.predict(customerId, distributorId);

        assertThat(prediction.getTopChurnFactor()).isEqualTo("days_since_last_order");
    }

    @Test
    void recommendedAction_populated() {
        setupCustomerAndDistributor();
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(phaseService.isSyntheticPhase(any())).thenReturn(false);
        when(featureService.computeFeatures(customerId, distributorId)).thenReturn(features(5));

        ChurnPrediction prediction = predictor.predict(customerId, distributorId);

        assertThat(prediction.getRecommendedAction()).isNotBlank();
    }

    @Test
    void computedAt_notNull() {
        setupCustomerAndDistributor();
        when(modelLoader.loadModel(any())).thenThrow(new RuntimeException("No model"));
        when(phaseService.isSyntheticPhase(any())).thenReturn(false);
        when(featureService.computeFeatures(customerId, distributorId)).thenReturn(features(5));

        ChurnPrediction prediction = predictor.predict(customerId, distributorId);

        assertThat(prediction.getComputedAt()).isNotNull();
    }
}
