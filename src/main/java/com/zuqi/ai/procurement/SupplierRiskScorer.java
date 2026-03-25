package com.zuqi.ai.procurement;

import com.zuqi.domain.ai.SupplierRiskScore;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.SupplierRepository;
import com.zuqi.repository.SupplierRiskScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Computes composite supplier risk scores using formula-based sub-scores.
 *
 * <pre>
 * composite = delivery(0.35) + quality(0.25) + priceConsistency(0.20)
 *           + responsiveness(0.10) + tenure_bonus(0.10)
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierRiskScorer {

    private final SupplierFeatureServiceImpl featureService;
    private final SupplierRiskScoreRepository riskScoreRepository;
    private final SupplierRepository supplierRepository;
    private final DistributorRepository distributorRepository;

    public SupplierRiskScore score(UUID supplierId, UUID distributorId) {
        SupplierFeatures f = featureService.computeFeatures(supplierId, distributorId);

        double deliveryScore      = f.deliveryOnTimePct();                              // 0–100
        double qualityScore       = (1.0 - f.qualityRejectionPct()) * 100.0;           // 0–100
        double priceConsistScore  = Math.max(0, 100.0 - f.priceConsistencyCv() * 200); // 0–100
        double responsivenessScore = Math.max(0, 100.0 - f.avgResponseTimeDays() * 10);// 0–100
        double tenureBonus        = Math.min(100.0, f.tenureMonths() * 5.0);           // 5 pts/month, capped 100

        double composite = deliveryScore      * 0.35
                         + qualityScore       * 0.25
                         + priceConsistScore  * 0.20
                         + responsivenessScore * 0.10
                         + tenureBonus        * 0.10;

        composite = Math.max(0, Math.min(100, composite));
        String tier = computeTier(composite);

        log.debug("[SupplierRisk] supplier={} score={} tier={}", supplierId,
                String.format("%.1f", composite), tier);

        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + supplierId));
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        Optional<SupplierRiskScore> existing =
                riskScoreRepository.findByDistributorIdAndSupplierId(distributorId, supplierId);

        SupplierRiskScore entity = existing.orElseGet(() -> SupplierRiskScore.builder()
                .distributor(distributor)
                .supplier(supplier)
                .build());

        entity.setRiskScore(composite);
        entity.setRiskTier(tier);
        entity.setDeliveryReliabilityScore(deliveryScore);
        entity.setQualityScore(qualityScore);
        entity.setPriceConsistencyScore(priceConsistScore);
        entity.setResponsivenessScore(responsivenessScore);
        entity.setDataPhase("SYNTHETIC");
        entity.setComputedAt(LocalDateTime.now());

        return riskScoreRepository.save(entity);
    }

    /** Higher score = lower risk. */
    private String computeTier(double score) {
        if (score >= 80) return "PREFERRED";
        if (score >= 65) return "RELIABLE";
        if (score >= 50) return "ACCEPTABLE";
        if (score >= 35) return "AT_RISK";
        return "CRITICAL";
    }
}
