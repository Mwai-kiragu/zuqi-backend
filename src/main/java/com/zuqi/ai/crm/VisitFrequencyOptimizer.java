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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tribuo.Model;
import org.tribuo.regression.Regressor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Generates visit scheduling recommendations for a sales rep.
 *
 * <p>For each customer assigned to the rep, predicts order-conversion probability
 * for each day of the week and recommends the best day. High-churn customers
 * are flagged for increased visit frequency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VisitFrequencyOptimizer {

    private final CustomerRepository customerRepository;
    private final CustomerAnalyticsFeatureServiceImpl featureService;
    private final VisitFeatureBuilder visitFeatureBuilder;
    private final ModelLoaderService modelLoader;
    private final ModelPhaseService phaseService;
    private final ChurnPredictionRepository churnPredictionRepository;
    private final VisitRecommendationRepository visitRecommendationRepository;
    private final DistributorRepository distributorRepository;
    private final UserRepository userRepository;

    private static final String[] DAY_NAMES = {
            "", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };

    /**
     * Generate visit recommendations for all customers assigned to a sales rep.
     *
     * @param salesRepId    sales rep UUID (User id)
     * @param distributorId distributor context
     * @return list of saved VisitRecommendation entities
     */
    @Transactional
    public List<VisitRecommendation> generateVisitPlan(UUID salesRepId, UUID distributorId) {
        log.info("[VisitOptimizer] Generating visit plan for rep={} distributor={}",
                salesRepId, distributorId);

        User salesRep = userRepository.findById(salesRepId)
                .orElseThrow(() -> new IllegalArgumentException("Sales rep not found: " + salesRepId));
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        List<Customer> customers = customerRepository
                .findByAssignedSalesRepIdAndActiveTrue(salesRepId, org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        Model<Regressor> model = loadModel();
        List<VisitRecommendation> results = new ArrayList<>();

        for (Customer customer : customers) {
            try {
                VisitRecommendation rec = generateForCustomer(
                        salesRep, distributor, customer, model);
                if (rec != null) results.add(rec);
            } catch (Exception e) {
                log.warn("[VisitOptimizer] Failed for customer={}: {}", customer.getId(), e.getMessage());
            }
        }

        log.info("[VisitOptimizer] Generated {} recommendations for rep={}", results.size(), salesRepId);
        return results;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private VisitRecommendation generateForCustomer(User salesRep,
                                                      Distributor distributor,
                                                      Customer customer,
                                                      Model<Regressor> model) {
        CustomerAnalyticsFeatures features = featureService.computeFeatures(
                customer.getId(), distributor.getId());

        // Find best day of week by conversion probability
        int bestDay = 1;
        double bestConversion = -1.0;

        for (int day = 1; day <= 7; day++) {
            double conversionProb;
            if (model != null) {
                org.tribuo.Example<Regressor> ex = visitFeatureBuilder.buildExample(
                        features, day, 0.0, false, false);
                conversionProb = model.predict(ex).getOutput().getValues()[0];
                conversionProb = phaseService.applyModifier(conversionProb, VisitTrainingPipeline.MODEL_NAME);
            } else {
                // Heuristic: prefer weekdays (Mon-Fri = higher conversion)
                conversionProb = day <= 5 ? 0.6 : 0.3;
            }
            if (conversionProb > bestConversion) {
                bestConversion = conversionProb;
                bestDay = day;
            }
        }

        // Determine visit frequency based on churn risk
        double frequencyPerWeek = computeFrequency(customer.getId(), distributor.getId());
        String dataPhase = phaseService.isSyntheticPhase(VisitTrainingPipeline.MODEL_NAME)
                ? "SYNTHETIC" : "REAL";

        // Upsert
        Optional<VisitRecommendation> existing =
                visitRecommendationRepository.findByDistributorIdAndCustomerId(
                        distributor.getId(), customer.getId());

        VisitRecommendation rec = existing.orElseGet(() -> VisitRecommendation.builder()
                .distributor(distributor)
                .salesRep(salesRep)
                .customer(customer)
                .build());

        rec.setRecommendedDay(bestDay);
        rec.setPredictedConversion(Math.max(0.0, Math.min(1.0, bestConversion)));
        rec.setRecommendedFrequencyPerWeek(frequencyPerWeek);
        rec.setReason("Best predicted conversion day: " + dayName(bestDay));
        rec.setDataPhase(dataPhase);

        return visitRecommendationRepository.save(rec);
    }

    private double computeFrequency(UUID customerId, UUID distributorId) {
        // Check churn risk tier to adjust frequency
        Optional<ChurnPrediction> churnOpt =
                churnPredictionRepository.findByDistributorIdAndCustomerId(distributorId, customerId);

        if (churnOpt.isPresent()) {
            String tier = churnOpt.get().getRiskTier();
            if ("CRITICAL".equals(tier)) return 3.0;
            if ("HIGH".equals(tier)) return 2.0;
        }
        return 1.0; // standard weekly visit
    }

    @SuppressWarnings("unchecked")
    private Model<Regressor> loadModel() {
        try {
            return modelLoader.loadModel(VisitTrainingPipeline.MODEL_NAME);
        } catch (Exception e) {
            log.warn("[VisitOptimizer] No active model, using heuristic: {}", e.getMessage());
            return null;
        }
    }

    private String dayName(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) return "Unknown";
        return DAY_NAMES[dayOfWeek];
    }
}
