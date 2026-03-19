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

        int saved = 0;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            Product product = productRepository.findById(entry.getKey()).orElse(null);
            if (product == null) continue;

            double score = (double) entry.getValue() / maxScore;
            ProductRecommendation rec = ProductRecommendation.builder()
                    .distributor(distributor)
                    .customer(customer)
                    .product(product)
                    .recommendationScore(score)
                    .reason("Frequently purchased together with your existing orders")
                    .source("collaborative_filtering")
                    .dataPhase("REAL")
                    .build();
            recommendationRepository.save(rec);
            saved++;
        }
        return saved;
    }
}
