package com.zuqi.ai.pricing;

import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderItem;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Computes pricing features for smart pricing recommendations.
 * Pulls from product data, order history, and demand forecasts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PricingFeatureServiceImpl {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    @Cacheable(value = "pricingFeatures", key = "#productId + ':' + #distributorId")
    public PricingFeatures computeFeatures(UUID productId, UUID distributorId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        double unitPrice = product.getUnitPrice() != null
                ? product.getUnitPrice().doubleValue() : 0.0;
        double costPrice = product.getCostPrice() != null
                ? product.getCostPrice().doubleValue() : 0.0;
        double marginPct = unitPrice > 0 ? (unitPrice - costPrice) / unitPrice * 100.0 : 0.0;

        // Product age in days
        int productAgeDays = product.getCreatedAt() != null
                ? (int) ChronoUnit.DAYS.between(product.getCreatedAt(), LocalDateTime.now())
                : 0;

        // Load distributor orders for last 90 days
        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
        List<Order> recentOrders = orderRepository
                .findByDistributorId(distributorId, Pageable.unpaged())
                .getContent()
                .stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(ninetyDaysAgo))
                .filter(o -> o.getItems() != null)
                .toList();

        // Compute avg weekly demand for this product
        double totalQty90d = recentOrders.stream()
                .flatMap(o -> o.getItems().stream())
                .filter(item -> item.getProduct() != null
                        && productId.equals(item.getProduct().getId()))
                .mapToDouble(item -> item.getQuantity() != null
                        ? item.getQuantity().doubleValue() : 0.0)
                .sum();
        double avgWeeklyDemand = totalQty90d / 13.0; // 90 days / 7 ≈ 13 weeks

        // Demand trend: compare last 30d vs prior 30d
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime sixtyDaysAgo  = LocalDateTime.now().minusDays(60);

        double demandLast30d = recentOrders.stream()
                .filter(o -> o.getCreatedAt().isAfter(thirtyDaysAgo))
                .flatMap(o -> o.getItems().stream())
                .filter(item -> item.getProduct() != null
                        && productId.equals(item.getProduct().getId()))
                .mapToDouble(item -> item.getQuantity() != null
                        ? item.getQuantity().doubleValue() : 0.0)
                .sum();

        double demandPrior30d = recentOrders.stream()
                .filter(o -> o.getCreatedAt().isAfter(sixtyDaysAgo)
                          && o.getCreatedAt().isBefore(thirtyDaysAgo))
                .flatMap(o -> o.getItems().stream())
                .filter(item -> item.getProduct() != null
                        && productId.equals(item.getProduct().getId()))
                .mapToDouble(item -> item.getQuantity() != null
                        ? item.getQuantity().doubleValue() : 0.0)
                .sum();

        // Trend: positive = demand growing, negative = declining
        double demandTrend = demandLast30d - demandPrior30d;

        // Similar product avg price (same category, same distributor)
        double similarProductAvgPrice = unitPrice; // default to own price
        if (product.getCategory() != null) {
            Long categoryId = product.getCategory().getId();
            List<Product> categoryProducts = productRepository.findAll().stream()
                    .filter(p -> p.getCategory() != null
                            && categoryId.equals(p.getCategory().getId())
                            && !p.getId().equals(productId)
                            && p.getUnitPrice() != null)
                    .toList();
            if (!categoryProducts.isEmpty()) {
                similarProductAvgPrice = categoryProducts.stream()
                        .mapToDouble(p -> p.getUnitPrice().doubleValue())
                        .average()
                        .orElse(unitPrice);
            }
        }

        // Category and price tier encodings
        int categoryEncoded = encodeCategoryId(product);
        int priceTierEncoded = encodePriceTier(unitPrice);

        return new PricingFeatures(
                productId,
                distributorId,
                unitPrice,
                costPrice,
                marginPct,
                0.0,                     // priceChangePct30d: requires historical price data
                avgWeeklyDemand,
                demandTrend,
                0.0,                     // inventoryDaysOfSupply: from StockRepository (injected by caller if needed)
                productAgeDays,
                similarProductAvgPrice,
                categoryEncoded,
                priceTierEncoded
        );
    }

    private int encodeCategoryId(Product product) {
        if (product.getCategory() == null) return 0;
        // Deterministic encoding from Long id (1-20 range)
        return (int) (product.getCategory().getId() % 20) + 1;
    }

    /**
     * Encode price into tier: 0=budget(<500), 1=mid(500-2000), 2=premium(>2000).
     */
    int encodePriceTier(double price) {
        if (price < 500)  return 0;
        if (price < 2000) return 1;
        return 2;
    }
}
