package com.zuqi.ai.crm;

import com.zuqi.domain.ai.ProductRecommendation;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderItem;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.OrderItemRepository;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.ProductRecommendationRepository;
import com.zuqi.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Generates next-best-product recommendations via item-based collaborative filtering.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Load all orders (with items) for the distributor.</li>
 *   <li>Build a per-customer purchased-product set.</li>
 *   <li>Build a co-purchase matrix: productA → (productB → count).</li>
 *   <li>For each customer: score unordered products by co-purchase frequency
 *       with their historically ordered products.</li>
 *   <li>Save top 5 recommendations per customer.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductRecommendationService {

    private static final int TOP_N = 5;

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductRecommendationRepository recommendationRepository;
    private final DistributorRepository distributorRepository;
    private final ProductRecommendationReasoningService reasoningService;
    private final CustomerSegmentationService segmentationService;
    private final com.zuqi.ai.demand.DemandForecaster demandForecaster;

    /**
     * Generate and save product recommendations for all active customers in the distributor.
     *
     * @param distributorId distributor to process
     * @return total number of recommendations saved
     */
    @Transactional
    public int generateRecommendations(UUID distributorId) {
        log.info("[ProductRec] Generating recommendations for distributor={}", distributorId);

        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        List<Customer> customers = customerRepository.findByDistributorIdAndActiveTrue(distributorId);
        if (customers.isEmpty()) {
            log.info("[ProductRec] No active customers for distributor={}", distributorId);
            return 0;
        }

        // Load all orders for the distributor
        List<Order> allOrders = orderRepository.findByDistributorId(distributorId, Pageable.unpaged())
                .getContent();

        // Build customer → purchased products map and co-purchase matrix
        Map<UUID, Set<UUID>> customerProducts = new HashMap<>();
        Map<UUID, Map<UUID, Integer>> coPurchaseMatrix = new HashMap<>();

        for (Order order : allOrders) {
            if (order.getMerchant() == null) continue;
            UUID customerId = order.getMerchant().getId();
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

            Set<UUID> orderProductIds = new HashSet<>();
            for (OrderItem item : items) {
                if (item.getProduct() != null) {
                    orderProductIds.add(item.getProduct().getId());
                }
            }

            customerProducts.computeIfAbsent(customerId, k -> new HashSet<>())
                    .addAll(orderProductIds);

            // Update co-purchase matrix
            List<UUID> productList = new ArrayList<>(orderProductIds);
            for (int i = 0; i < productList.size(); i++) {
                for (int j = i + 1; j < productList.size(); j++) {
                    UUID a = productList.get(i);
                    UUID b = productList.get(j);
                    coPurchaseMatrix.computeIfAbsent(a, k -> new HashMap<>())
                            .merge(b, 1, Integer::sum);
                    coPurchaseMatrix.computeIfAbsent(b, k -> new HashMap<>())
                            .merge(a, 1, Integer::sum);
                }
            }
        }

        int totalSaved = 0;

        for (Customer customer : customers) {
            try {
                int saved = generateForCustomer(
                        customer, distributor, customerProducts, coPurchaseMatrix);
                totalSaved += saved;
            } catch (Exception e) {
                log.warn("[ProductRec] Failed for customer={}: {}", customer.getId(), e.getMessage());
            }
        }

        log.info("[ProductRec] Saved {} recommendations for distributor={}", totalSaved, distributorId);
        return totalSaved;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private int generateForCustomer(Customer customer,
                                     Distributor distributor,
                                     Map<UUID, Set<UUID>> customerProducts,
                                     Map<UUID, Map<UUID, Integer>> coPurchaseMatrix) {
        Set<UUID> purchased = customerProducts.getOrDefault(customer.getId(), Set.of());
        if (purchased.isEmpty()) return 0;

        // Score each unordered product by co-purchase count
        Map<UUID, Integer> candidateScores = new HashMap<>();
        for (UUID orderedProduct : purchased) {
            Map<UUID, Integer> coOrdered = coPurchaseMatrix.getOrDefault(orderedProduct, Map.of());
            for (Map.Entry<UUID, Integer> entry : coOrdered.entrySet()) {
                UUID candidate = entry.getKey();
                if (!purchased.contains(candidate)) {
                    candidateScores.merge(candidate, entry.getValue(), Integer::sum);
                }
            }
        }

        if (candidateScores.isEmpty()) return 0;

        // Take top N by score
        List<Map.Entry<UUID, Integer>> sorted = candidateScores.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry<UUID, Integer>::getValue).reversed())
                .limit(TOP_N)
                .toList();

        int maxScore = sorted.stream().mapToInt(Map.Entry::getValue).max().orElse(1);

        // Delete old recommendations for this customer
        recommendationRepository.deleteByDistributorIdAndCustomerId(
                distributor.getId(), customer.getId());

        // Load product names for context (top 3 already purchased)
        List<String> purchasedProductNames = purchased.stream()
                .limit(3)
                .map(pid -> productRepository.findById(pid)
                        .map(p -> p.getName() != null ? p.getName() : "Unknown")
                        .orElse("Unknown"))
                .toList();

        int saved = 0;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            Product product = productRepository.findById(entry.getKey()).orElse(null);
            if (product == null) continue;

            double score = (double) entry.getValue() / maxScore;
            double pctSimilarMerchants = Math.round(score * 100.0);

            // Build LLM context
            String context = buildReasoningContext(customer, product, purchasedProductNames,
                    pctSimilarMerchants, entry.getValue());

            // Call LLM for sales-ready reason; fall back to template on failure
            String reason;
            try {
                reason = reasoningService.generateReason(context);
                if (reason == null || reason.isBlank()) {
                    reason = fallbackReason(product.getName(), pctSimilarMerchants);
                }
            } catch (Exception e) {
                log.debug("[ProductRec] LLM reason failed for product={}: {}", product.getId(), e.getMessage());
                reason = fallbackReason(product.getName(), pctSimilarMerchants);
            }

            ProductRecommendation rec = ProductRecommendation.builder()
                    .distributor(distributor)
                    .customer(customer)
                    .product(product)
                    .recommendationScore(score)
                    .reason(reason)
                    .source("HYBRID_ASSOCIATION_LLM")
                    .dataPhase("REAL")
                    .build();
            recommendationRepository.save(rec);
            saved++;
        }
        return saved;
    }

    private String buildReasoningContext(Customer customer, Product product,
                                          List<String> purchasedProductNames,
                                          double pctSimilarMerchants,
                                          int coPurchaseCount) {
        String category = product.getCategory() != null ? product.getCategory().getName() : "FMCG";
        String customerCategory = customer.getCategory() != null && customer.getCategory().getName() != null
                ? customer.getCategory().getName() : "retail";
        String location = customer.getCounty() != null ? customer.getCounty() : "Kenya";
        String productName = product.getName() != null ? product.getName() : "this product";
        double unitPrice = product.getUnitPrice() != null ? product.getUnitPrice().doubleValue() : 0.0;

        // Segment label — optional LLM context only, does not affect scoring
        String segment = segmentationService.getSegment(customer.getId(), customer.getDistributor().getId());

        // Demand forecast — optional, skipped if unavailable
        String demandLine = "";
        try {
            com.zuqi.ai.demand.DemandForecaster.DemandForecast forecast =
                    demandForecaster.forecastDemand(customer.getId(), product.getId());
            if (forecast != null && forecast.predictedQuantity() != null
                    && forecast.predictedQuantity().doubleValue() > 0) {
                demandLine = String.format("\nPredicted demand (7d): %.0f units",
                        forecast.predictedQuantity().doubleValue());
            }
        } catch (Exception e) {
            log.debug("[ProductRec] No demand forecast for product={}: {}", product.getId(), e.getMessage());
        }

        return String.format("""
                Merchant: %s
                Location: %s
                Business type: %s
                Segment: %s
                Products they currently buy: %s
                Recommended product: %s (category: %s, price: KES %.0f)
                Co-purchase signal: %d other merchants who buy similar products also buy this
                Percentage of similar merchants stocking this: %.0f%%%s
                """,
                customer.getBusinessName(), location, customerCategory, segment,
                String.join(", ", purchasedProductNames),
                productName, category, unitPrice,
                coPurchaseCount, pctSimilarMerchants, demandLine);
    }

    private String fallbackReason(String productName, double pctSimilarMerchants) {
        return String.format(
                "%.0f%% of similar merchants in your area stock %s — a strong indicator " +
                "of consistent demand. Adding it could boost your basket value.",
                pctSimilarMerchants, productName != null ? productName : "this product");
    }
}
