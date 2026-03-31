package com.zuqi.ai.crm;

import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.CustomerClv;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.CustomerClvRepository;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.DistributorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tribuo.Model;
import org.tribuo.regression.Regressor;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Predicts Customer Lifetime Value (12-month forward revenue) for a single customer.
 *
 * <p>Loads the active XGBoost CLV regression model. Falls back to using
 * {@code lifetimeRevenue} as a proxy when no trained model exists.
 * Applies the SYNTHETIC-phase confidence modifier and computes ±20% confidence bounds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerLifetimeValuePredictor {

    private final CustomerAnalyticsFeatureServiceImpl featureService;
    private final ClvFeatureBuilder clvFeatureBuilder;
    private final ModelLoaderService modelLoader;
    private final ModelPhaseService phaseService;
    private final CustomerClvRepository clvRepository;
    private final CustomerRepository customerRepository;
    private final DistributorRepository distributorRepository;

    /**
     * Predict and persist the 12-month CLV for a customer.
     *
     * @param customerId    customer to predict for
     * @param distributorId distributor context
     * @return persisted CLV entity
     */
    @Transactional
    public CustomerClv predict(UUID customerId, UUID distributorId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        CustomerAnalyticsFeatures features = featureService.computeFeatures(customerId, distributorId);

        double predictedRevenue;
        double confidence;

        Model<Regressor> model = loadModel();
        if (model != null) {
            org.tribuo.Example<Regressor> example = clvFeatureBuilder.buildExample(features);
            predictedRevenue = model.predict(example).getOutput().getValues()[0];
            predictedRevenue = Math.max(0.0, predictedRevenue);
            predictedRevenue = phaseService.applyModifier(predictedRevenue, DataPhaseTracker.MODEL_CUSTOMER_CLV_PREDICTOR);
            confidence = phaseService.applyModifier(0.8, DataPhaseTracker.MODEL_CUSTOMER_CLV_PREDICTOR);
        } else {
            // Heuristic fallback: annualise lifetime revenue
            predictedRevenue = features.lifetimeRevenue();
            confidence = 0.4;
        }

        // ±20% confidence bounds
        double lowerBound = predictedRevenue * 0.80;
        double upperBound = predictedRevenue * 1.20;
        String dataPhase = phaseService.isSyntheticPhase(DataPhaseTracker.MODEL_CUSTOMER_CLV_PREDICTOR)
                ? "SYNTHETIC" : "REAL";

        Optional<CustomerClv> existing =
                clvRepository.findByDistributorIdAndCustomerId(distributorId, customerId);

        CustomerClv entity = existing.orElseGet(() -> CustomerClv.builder()
                .distributor(distributor)
                .customer(customer)
                .build());

        entity.setPredictedRevenue12m(predictedRevenue);
        entity.setLowerBound(lowerBound);
        entity.setUpperBound(upperBound);
        entity.setConfidenceScore(confidence);
        entity.setDataPhase(dataPhase);
        entity.setComputedAt(LocalDateTime.now());

        return clvRepository.save(entity);
    }

    @SuppressWarnings("unchecked")
    private Model<Regressor> loadModel() {
        try {
            return modelLoader.loadModel(DataPhaseTracker.MODEL_CUSTOMER_CLV_PREDICTOR);
        } catch (Exception e) {
            log.warn("[CLV] No active model, using heuristic: {}", e.getMessage());
            return null;
        } catch (Error e) {
            log.error("[CLV] Fatal error loading model (native library issue?): {}", e.getMessage(), e);
            return null;
        }
    }
}
