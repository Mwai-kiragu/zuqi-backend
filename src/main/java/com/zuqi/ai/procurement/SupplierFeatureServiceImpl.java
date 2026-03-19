package com.zuqi.ai.procurement;

import com.zuqi.domain.procurement.PoStatus;
import com.zuqi.domain.procurement.PurchaseOrder;
import com.zuqi.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Computes supplier risk features from PurchaseOrder history.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierFeatureServiceImpl {

    private final PurchaseOrderRepository purchaseOrderRepository;

    @Cacheable(value = "supplierFeatures", key = "#supplierId + ':' + #distributorId")
    public SupplierFeatures computeFeatures(UUID supplierId, UUID distributorId) {
        List<PurchaseOrder> orders = purchaseOrderRepository
                .findBySupplierIdAndDistributorId(supplierId, distributorId);

        if (orders.isEmpty()) {
            return new SupplierFeatures(supplierId, distributorId,
                    0, 0, 0, 0, 0, 0, 0, 0);
        }

        int total = orders.size();
        double totalValue = orders.stream()
                .filter(o -> o.getTotalAmount() != null)
                .mapToDouble(o -> o.getTotalAmount().doubleValue())
                .sum();

        // Delivery timeliness — only RECEIVED orders with both dates
        List<PurchaseOrder> received = orders.stream()
                .filter(o -> o.getStatus() == PoStatus.RECEIVED
                          || o.getStatus() == PoStatus.PARTIALLY_RECEIVED)
                .filter(o -> o.getReceivedAt() != null && o.getExpectedDeliveryDate() != null)
                .toList();

        long onTime = received.stream()
                .filter(o -> !o.getReceivedAt().toLocalDate().isAfter(o.getExpectedDeliveryDate()))
                .count();

        double onTimePct = received.isEmpty() ? 100.0 : (double) onTime / received.size() * 100.0;

        double avgDelay = received.stream()
                .filter(o -> o.getReceivedAt().toLocalDate().isAfter(o.getExpectedDeliveryDate()))
                .mapToLong(o -> ChronoUnit.DAYS.between(
                        o.getExpectedDeliveryDate(), o.getReceivedAt().toLocalDate()))
                .average()
                .orElse(0.0);

        // Response time: sentAt - createdAt
        double avgResponseDays = orders.stream()
                .filter(o -> o.getSentAt() != null && o.getCreatedAt() != null)
                .mapToDouble(o -> ChronoUnit.HOURS.between(o.getCreatedAt(), o.getSentAt()) / 24.0)
                .average()
                .orElse(0.0);

        // Price consistency CV from JSONB items
        double priceConsistencyCv = computePriceCv(orders);

        // Tenure
        LocalDateTime first = orders.stream()
                .filter(o -> o.getCreatedAt() != null)
                .map(PurchaseOrder::getCreatedAt)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        int tenureMonths = (int) ChronoUnit.MONTHS.between(first, LocalDateTime.now());

        return new SupplierFeatures(supplierId, distributorId,
                onTimePct, avgDelay, 0.0, priceConsistencyCv,
                avgResponseDays, total, totalValue, tenureMonths);
    }

    /**
     * Compute coefficient of variation of unit prices across all PO items.
     * PurchaseOrder.items is JSONB: List<Map> with "unitPrice" key.
     */
    private double computePriceCv(List<PurchaseOrder> orders) {
        List<Double> prices = orders.stream()
                .flatMap(o -> o.getItems() != null ? o.getItems().stream() : java.util.stream.Stream.empty())
                .map(item -> item.get("unitPrice"))
                .filter(v -> v != null)
                .mapToDouble(v -> v instanceof Number ? ((Number) v).doubleValue() : 0.0)
                .filter(p -> p > 0)
                .boxed()
                .toList();

        if (prices.size() < 2) return 0.0;

        double mean = prices.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
        if (mean == 0) return 0.0;
        double variance = prices.stream()
                .mapToDouble(p -> Math.pow(p - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance) / mean;
    }
}
