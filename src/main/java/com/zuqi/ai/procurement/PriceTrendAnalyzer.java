package com.zuqi.ai.procurement;

import com.zuqi.domain.ai.PriceTrend;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.procurement.PoStatus;
import com.zuqi.domain.procurement.PurchaseOrder;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.PriceTrendRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.PurchaseOrderRepository;
import com.zuqi.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Analyzes price trends for (supplier, product) pairs using linear regression
 * over PO item price history.
 *
 * Trend direction:
 *   slope > +2% of mean price  → INCREASING
 *   slope < -2% of mean price  → DECREASING
 *   else                       → STABLE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceTrendAnalyzer {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PriceTrendRepository priceTrendRepository;
    private final DistributorRepository distributorRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    /**
     * Analyze price trends for all (supplier, product) pairs in a distributor.
     *
     * @return number of trend records saved
     */
    public int analyze(UUID distributorId) {
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        List<PurchaseOrder> orders = purchaseOrderRepository
                .findByDistributorId(distributorId, org.springframework.data.domain.Pageable.unpaged())
                .getContent()
                .stream()
                .filter(o -> o.getStatus() != PoStatus.CANCELLED && o.getItems() != null)
                .toList();

        // Build price history: supplierProductKey → list of (date, unitPrice)
        Map<String, List<PricePoint>> history = new HashMap<>();
        for (PurchaseOrder po : orders) {
            if (po.getSupplier() == null || po.getCreatedAt() == null) continue;
            for (Map<String, Object> item : po.getItems()) {
                Object pidObj    = item.get("productId");
                Object priceObj  = item.get("unitPrice");
                if (pidObj == null || priceObj == null) continue;

                String key = po.getSupplier().getId() + ":" + pidObj;
                double price = priceObj instanceof Number ? ((Number) priceObj).doubleValue() : 0.0;
                if (price <= 0) continue;

                history.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new PricePoint(po.getCreatedAt(), price));
            }
        }

        int saved = 0;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeMonthsAgo = now.minusDays(90);

        for (Map.Entry<String, List<PricePoint>> entry : history.entrySet()) {
            List<PricePoint> points = entry.getValue();
            if (points.size() < 3) continue; // need at least 3 data points

            points.sort((a, b) -> a.date().compareTo(b.date()));

            String[] parts = entry.getKey().split(":");
            UUID supplierId = UUID.fromString(parts[0]);
            UUID productId  = UUID.fromString(parts[1]);

            Optional<Supplier> supplierOpt = supplierRepository.findById(supplierId);
            Optional<Product> productOpt   = productRepository.findById(productId);
            if (supplierOpt.isEmpty() || productOpt.isEmpty()) continue;

            double slope       = computeSlope(points);
            double mean        = points.stream().mapToDouble(PricePoint::price).average().orElse(1.0);
            double stddev      = computeStddev(points, mean);
            double priceVolatility = mean > 0 ? stddev / mean : 0.0;

            // pct change over last 3 months
            double latestPrice = points.get(points.size() - 1).price();
            double price3mAgo  = points.stream()
                    .filter(p -> p.date().isBefore(threeMonthsAgo))
                    .mapToDouble(PricePoint::price)
                    .reduce((a, b) -> b)  // last before cutoff
                    .orElse(latestPrice);
            double pctChange3m = price3mAgo > 0 ? (latestPrice - price3mAgo) / price3mAgo * 100.0 : 0.0;

            // Market avg price for this product across all suppliers
            double marketAvg = history.entrySet().stream()
                    .filter(e -> e.getKey().endsWith(":" + productId))
                    .flatMap(e -> e.getValue().stream())
                    .mapToDouble(PricePoint::price)
                    .average()
                    .orElse(latestPrice);

            // Trend direction: slope threshold = 2% of mean per month
            double threshold = mean * 0.02;
            String direction = slope > threshold ? "INCREASING"
                             : slope < -threshold ? "DECREASING"
                             : "STABLE";

            Optional<PriceTrend> existing = priceTrendRepository
                    .findByDistributorIdAndSupplierIdAndProductId(distributorId, supplierId, productId);

            PriceTrend trend = existing.orElseGet(() -> PriceTrend.builder()
                    .distributor(distributor)
                    .supplier(supplierOpt.get())
                    .product(productOpt.get())
                    .build());

            trend.setTrendDirection(direction);
            trend.setTrendSlope(slope);
            trend.setPctChange3m(pctChange3m);
            trend.setCurrentUnitPrice(latestPrice);
            trend.setMarketAvgPrice(marketAvg);
            trend.setPriceVolatility(priceVolatility);
            trend.setComputedAt(now);

            priceTrendRepository.save(trend);
            saved++;
        }

        log.info("[PriceTrend] distributor={} analyzed {} supplier-product pairs", distributorId, saved);
        return saved;
    }

    // ── Math helpers ──────────────────────────────────────────────────────────

    /**
     * Linear regression slope (price per unit time, where time = index).
     */
    double computeSlope(List<PricePoint> points) {
        int n = points.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX  += i;
            sumY  += points.get(i).price();
            sumXY += i * points.get(i).price();
            sumX2 += (double) i * i;
        }
        double denom = n * sumX2 - sumX * sumX;
        return denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
    }

    double computeStddev(List<PricePoint> points, double mean) {
        double variance = points.stream()
                .mapToDouble(p -> Math.pow(p.price() - mean, 2))
                .average().orElse(0.0);
        return Math.sqrt(variance);
    }

    record PricePoint(LocalDateTime date, double price) {}
}
