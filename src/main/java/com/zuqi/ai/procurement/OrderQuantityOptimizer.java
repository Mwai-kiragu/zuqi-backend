package com.zuqi.ai.procurement;

import com.zuqi.domain.ai.PriceTrend;
import com.zuqi.domain.ai.ReorderSuggestion;
import com.zuqi.domain.ai.SupplierRiskScore;
import com.zuqi.repository.PriceTrendRepository;
import com.zuqi.repository.ReorderSuggestionRepository;
import com.zuqi.repository.SupplierRiskScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adjusts reorder quantities using price trends and supplier risk scores.
 *
 * <p>Decision logic:
 * <ul>
 *   <li>INCREASING price trend → buy extra (up to +30%) to pre-empt cost rises</li>
 *   <li>DECREASING price trend → buy minimum (−20%) to delay purchases</li>
 *   <li>Preferred supplier (risk score ≥ 80) → apply full EOQ without penalty</li>
 *   <li>AT_RISK / CRITICAL supplier → reduce qty by 25% and flag alternative needed</li>
 * </ul>
 *
 * <p>This produces an {@link OptimizedOrderResult} with adjusted quantity and rationale,
 * and updates the {@link ReorderSuggestion} with the chosen supplier.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderQuantityOptimizer {

    private final ReorderSuggestionRepository reorderSuggestionRepository;
    private final PriceTrendRepository priceTrendRepository;
    private final SupplierRiskScoreRepository supplierRiskScoreRepository;

    /**
     * Optimizes quantity for a pending reorder suggestion.
     *
     * @param suggestion      the EOQ-based suggestion to refine
     * @param distributorId   distributor context
     * @return optimized result with adjusted qty and rationale
     */
    public OptimizedOrderResult optimize(ReorderSuggestion suggestion, UUID distributorId) {
        UUID productId = suggestion.getProduct().getId();

        // 1. Pick the best supplier by risk score
        List<SupplierRiskScore> scores = supplierRiskScoreRepository
                .findByDistributorId(distributorId, Pageable.unpaged()).getContent();

        Optional<SupplierRiskScore> bestSupplierScore = scores.stream()
                .filter(s -> s.getRiskScore() != null)
                .max(Comparator.comparingDouble(SupplierRiskScore::getRiskScore));

        double supplierAdjustmentFactor = 1.0;
        String supplierRationale = "No supplier risk data — using base EOQ";
        UUID selectedSupplierId = null;

        if (bestSupplierScore.isPresent()) {
            SupplierRiskScore best = bestSupplierScore.get();
            selectedSupplierId = best.getSupplier().getId();
            String tier = best.getRiskTier();

            supplierAdjustmentFactor = switch (tier) {
                case "PREFERRED"   -> 1.0;
                case "RELIABLE"    -> 1.0;
                case "ACCEPTABLE"  -> 0.90;
                case "AT_RISK"     -> 0.75;
                case "CRITICAL"    -> 0.60;
                default            -> 0.90;
            };
            supplierRationale = String.format("Supplier tier=%s (score=%.1f) → ×%.2f",
                    tier, best.getRiskScore(), supplierAdjustmentFactor);
        }

        // 2. Find price trend for best supplier + product
        double priceTrendFactor = 1.0;
        String priceTrendRationale = "No price trend data";

        if (selectedSupplierId != null) {
            Optional<PriceTrend> trendOpt = priceTrendRepository
                    .findByDistributorIdAndSupplierIdAndProductId(
                            distributorId, selectedSupplierId, productId);

            if (trendOpt.isPresent()) {
                String direction = trendOpt.get().getTrendDirection();
                priceTrendFactor = switch (direction) {
                    case "INCREASING" -> 1.30; // buy early before price rises
                    case "DECREASING" -> 0.80; // delay to benefit from lower prices
                    default           -> 1.0;  // STABLE — no adjustment
                };
                double pct = trendOpt.get().getPctChange3m() != null
                        ? trendOpt.get().getPctChange3m() : 0.0;
                priceTrendRationale = String.format(
                        "Price trend=%s (3m change=%.1f%%) → ×%.2f",
                        direction, pct, priceTrendFactor);
            }
        }

        // 3. Compute adjusted quantity
        double baseEoq = suggestion.getEconomicOrderQty() != null
                ? suggestion.getEconomicOrderQty()
                : suggestion.getSuggestedQty();

        double adjustedQty = Math.ceil(baseEoq * supplierAdjustmentFactor * priceTrendFactor);
        adjustedQty = Math.max(1.0, adjustedQty);

        // 4. Persist chosen supplier onto the suggestion
        if (selectedSupplierId != null) {
            suggestion.setSupplierId(selectedSupplierId);
            reorderSuggestionRepository.save(suggestion);
        }

        String rationale = supplierRationale + " | " + priceTrendRationale;

        log.info("[OrderQuantityOptimizer] product={} baseEoq={} adjusted={} | {}",
                productId, String.format("%.0f", baseEoq),
                String.format("%.0f", adjustedQty), rationale);

        return new OptimizedOrderResult(
                suggestion.getId(),
                productId,
                selectedSupplierId,
                baseEoq,
                adjustedQty,
                supplierAdjustmentFactor,
                priceTrendFactor,
                rationale
        );
    }

    /**
     * Result of quantity optimization for a single warehouse-SKU.
     */
    public record OptimizedOrderResult(
            UUID suggestionId,
            UUID productId,
            UUID selectedSupplierId,
            double baseEoq,
            double adjustedQty,
            double supplierFactor,
            double priceTrendFactor,
            String rationale
    ) {}
}
