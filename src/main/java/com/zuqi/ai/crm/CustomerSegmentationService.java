package com.zuqi.ai.crm;

import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.CustomerSegment;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.CustomerSegmentRepository;
import com.zuqi.repository.DistributorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tribuo.Model;
import org.tribuo.clustering.ClusterID;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs customer segmentation for all active customers in a distributor.
 *
 * <p>Loads the active K-Means model, computes {@link CustomerAnalyticsFeatures} for each customer,
 * predicts the cluster, maps it to a human-readable label, and saves/updates
 * {@link CustomerSegment}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerSegmentationService {

    private final CustomerRepository customerRepository;
    private final CustomerAnalyticsFeatureServiceImpl featureService;
    private final SegmentationFeatureBuilder featureBuilder;
    private final ModelLoaderService modelLoader;
    private final ModelPhaseService phaseService;
    private final DataPhaseTracker phaseTracker;
    private final CustomerSegmentRepository customerSegmentRepository;
    private final DistributorRepository distributorRepository;

    /**
     * Segment all active customers for the given distributor.
     *
     * @param distributorId distributor to process
     * @return number of customers segmented
     */
    @Transactional
    public int segmentAll(UUID distributorId) {
        log.info("[Segmentation] Starting segmentation for distributor={}", distributorId);

        List<Customer> customers = customerRepository.findByDistributorIdAndActiveTrue(distributorId);
        if (customers.isEmpty()) {
            log.info("[Segmentation] No active customers found for distributor={}", distributorId);
            return 0;
        }

        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        Model<ClusterID> model = loadModel();
        String dataPhase = phaseTracker.getPhase(DataPhaseTracker.MODEL_CUSTOMER_SEGMENTER, null).name();
        int count = 0;

        for (Customer customer : customers) {
            try {
                CustomerAnalyticsFeatures features = featureService.computeFeatures(
                        customer.getId(), distributorId);

                int clusterId;
                double confidence = 0.8; // default

                if (model != null) {
                    org.tribuo.Example<ClusterID> example = featureBuilder.buildExample(features);
                    ClusterID predicted = model.predict(example).getOutput();
                    clusterId = predicted.getID();
                    confidence = phaseService.applyModifier(0.8, DataPhaseTracker.MODEL_CUSTOMER_SEGMENTER);
                } else {
                    // Heuristic fallback: classify by revenue
                    clusterId = heuristicCluster(features);
                    confidence = 0.5;
                }

                String label = clusterLabel(clusterId);
                saveOrUpdateSegment(distributor, customer, clusterId, label, confidence, dataPhase);
                count++;

            } catch (Exception e) {
                log.warn("[Segmentation] Failed for customer={}: {}", customer.getId(), e.getMessage());
            }
        }

        log.info("[Segmentation] Segmented {} customers for distributor={}", count, distributorId);
        return count;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Model<ClusterID> loadModel() {
        try {
            return modelLoader.loadModel(DataPhaseTracker.MODEL_CUSTOMER_SEGMENTER);
        } catch (Exception e) {
            log.warn("[Segmentation] No active model, using heuristic: {}", e.getMessage());
            return null;
        }
    }

    private int heuristicCluster(CustomerAnalyticsFeatures f) {
        if (f.totalRevenue90d() > 100_000 && f.revenueTrendSlope() > 0) return 0; // HIGH_VALUE_GROWING
        if (f.totalRevenue90d() > 50_000) return 1;                                // STABLE_MID_TIER
        if (f.daysSinceLastOrder() > 60) return 2;                                 // AT_RISK_DECLINING
        if (f.tenureMonths() < 3) return 3;                                        // NEW_LOW_ACTIVITY
        return 4;                                                                    // HIGH_VALUE_AT_RISK
    }

    private String clusterLabel(int clusterId) {
        return switch (clusterId) {
            case 0 -> "HIGH_VALUE_GROWING";
            case 1 -> "STABLE_MID_TIER";
            case 2 -> "AT_RISK_DECLINING";
            case 3 -> "NEW_LOW_ACTIVITY";
            default -> "HIGH_VALUE_AT_RISK";
        };
    }

    private void saveOrUpdateSegment(Distributor distributor, Customer customer,
                                      int clusterId, String label,
                                      double confidence, String dataPhase) {
        Optional<CustomerSegment> existing =
                customerSegmentRepository.findByDistributorIdAndCustomerId(
                        distributor.getId(), customer.getId());

        CustomerSegment segment = existing.orElseGet(() -> CustomerSegment.builder()
                .distributor(distributor)
                .customer(customer)
                .build());

        segment.setSegmentId(clusterId);
        segment.setSegmentLabel(label);
        segment.setConfidenceScore(confidence);
        segment.setDataPhase(dataPhase);
        segment.setComputedAt(LocalDateTime.now());

        customerSegmentRepository.save(segment);
    }
}
