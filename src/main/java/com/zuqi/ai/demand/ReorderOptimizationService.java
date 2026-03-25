package com.zuqi.ai.demand;

import com.zuqi.ai.feature.ReorderFeatureServiceImpl;
import com.zuqi.ai.feature.ReorderFeatures;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.ReorderSuggestion;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.ReorderSuggestionRepository;
import com.zuqi.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Computes EOQ-based reorder suggestions for warehouse-SKU combinations.
 *
 * Uses Wilson's EOQ formula:
 *   EOQ = sqrt(2 * D * S / H)
 * where D = annual demand, S = ordering cost, H = carrying cost per unit per year.
 *
 * Safety stock:
 *   SS = Z * sqrt(lead_time * sigma_demand^2 + demand^2 * sigma_lead_time^2)
 * where Z = 1.65 (95th percentile service level).
 *
 * Blueprint: phase2-plan.md Section 2.1
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReorderOptimizationService {

    private final ReorderFeatureServiceImpl featureService;
    private final ModelPhaseService phaseService;
    private final DataPhaseTracker phaseTracker;
    private final ReorderSuggestionRepository reorderSuggestionRepository;
    private final DistributorRepository distributorRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;

    private static final String MODEL_NAME = "reorder_optimizer";
    private static final double Z_95 = 1.645; // 95% service level

    /**
     * Compute and persist a reorder suggestion for a warehouse-SKU combination.
     *
     * @return the saved ReorderSuggestion, or null if no reorder needed
     */
    public ReorderSuggestion computeSuggestion(UUID distributorId, UUID warehouseId, UUID productId) {
        ReorderFeatures features = featureService.computeFeatures(distributorId, warehouseId, productId);

        // Compute EOQ
        double annualDemand = features.avgDailyDemand() * 365;
        double eoq = computeEoq(annualDemand, features.orderingCostFixed(),
                features.carryingCostRate(), features.unitCostKes());

        // Compute safety stock (Hadley-Whitin approximation)
        double safetyStock = computeSafetyStock(
                features.avgDailyDemand(),
                features.demandVariabilityCv(),
                features.supplierLeadTimeAvgDays(),
                features.supplierLeadTimeStddev()
        );

        // Reorder point
        double reorderPoint = (features.avgDailyDemand() * features.supplierLeadTimeAvgDays()) + safetyStock;

        // Only suggest if below reorder point
        double effectiveStock = features.currentStockLevel() + features.pendingOrdersQty();
        if (effectiveStock > reorderPoint) {
            log.debug("SKU {} in warehouse {} has {} units (rop={}), no reorder needed",
                    productId, warehouseId, String.format("%.1f", effectiveStock),
                    String.format("%.1f", reorderPoint));
            return null;
        }

        // Apply data phase confidence modifier
        double confidence = phaseService.applyModifier(0.8, MODEL_NAME);

        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + warehouseId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        ReorderSuggestion suggestion = ReorderSuggestion.builder()
                .distributor(distributor)
                .warehouse(warehouse)
                .product(product)
                .suggestedQty(eoq)
                .economicOrderQty(eoq)
                .safetyStock(safetyStock)
                .reorderPoint(reorderPoint)
                .currentStock(features.currentStockLevel())
                .daysOfSupplyRemaining(features.daysOfSupplyRemaining())
                .avgDailyDemand(features.avgDailyDemand())
                .leadTimeDays(features.supplierLeadTimeAvgDays())
                .confidenceScore(confidence)
                .dataPhase(phaseTracker.getPhase(MODEL_NAME, distributorId).name())
                .status("PENDING")
                .computedAt(LocalDateTime.now())
                .build();

        ReorderSuggestion saved = reorderSuggestionRepository.save(suggestion);
        log.info("Reorder suggestion created: SKU {} warehouse {} — qty={} (rop={}, stock={})",
                productId, warehouseId,
                String.format("%.0f", eoq),
                String.format("%.0f", reorderPoint),
                String.format("%.0f", features.currentStockLevel()));
        return saved;
    }

    /**
     * Wilson EOQ formula: EOQ = sqrt(2DS / H)
     */
    double computeEoq(double annualDemand, double orderingCost,
                      double carryingCostRate, double unitCost) {
        double h = carryingCostRate * unitCost; // annual holding cost per unit
        if (h <= 0 || annualDemand <= 0) {
            return Math.max(annualDemand / 12, 1); // fallback: 1 month supply
        }
        double eoq = Math.sqrt((2.0 * annualDemand * orderingCost) / h);
        return Math.max(1.0, Math.ceil(eoq));
    }

    /**
     * Safety stock using Hadley-Whitin formula.
     * SS = Z * sqrt(L * σ_d² + d² * σ_L²)
     */
    double computeSafetyStock(double avgDailyDemand, double demandCv,
                               double leadTimeAvg, double leadTimeStddev) {
        double sigmaDemand = avgDailyDemand * demandCv;
        double ss = Z_95 * Math.sqrt(
                leadTimeAvg * sigmaDemand * sigmaDemand
                + avgDailyDemand * avgDailyDemand * leadTimeStddev * leadTimeStddev
        );
        return Math.max(0.0, Math.ceil(ss));
    }
}
