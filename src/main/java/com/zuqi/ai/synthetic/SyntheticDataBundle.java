package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Container holding a complete in-memory synthetic dataset for one distributor.
 *
 * Built once per generation run via {@link #create} and passed to feature builders
 * and training pipelines. All lists and maps are unmodifiable.
 *
 * Cross-reference maps enable O(1) lookups during feature computation:
 * <ul>
 *   <li>{@code merchantOrders}       — all orders for a merchant</li>
 *   <li>{@code orderItems}           — line items for an order</li>
 *   <li>{@code orderPayments}        — payments against an order (via invoiceRef)</li>
 *   <li>{@code merchantPayments}     — all payments for a merchant</li>
 *   <li>{@code merchantActivities}   — sales rep visits for a merchant</li>
 *   <li>{@code merchantCreditHistory}— credit evaluations for a merchant</li>
 * </ul>
 */
public final class SyntheticDataBundle {

    private final List<SyntheticMerchant>            merchants;
    private final List<SyntheticOrder>               orders;
    private final List<SyntheticOrderItem>           orderItems;
    private final List<SyntheticPayment>             payments;
    private final List<SyntheticInventoryMovement>   inventoryMovements;
    private final List<SyntheticRepActivity>         repActivities;
    private final List<SyntheticCreditEvaluation>    creditEvaluations;

    // Cross-reference maps
    private final Map<UUID, List<SyntheticOrder>>            merchantOrders;
    private final Map<UUID, List<SyntheticOrderItem>>        orderItems_byOrder;
    private final Map<UUID, List<SyntheticPayment>>          orderPayments;
    private final Map<UUID, List<SyntheticPayment>>          merchantPayments;
    private final Map<UUID, List<SyntheticRepActivity>>      merchantActivities;
    private final Map<UUID, List<SyntheticCreditEvaluation>> merchantCreditHistory;

    // Metadata
    private final long              generationSeed;
    private final SyntheticDataConfig config;
    private final LocalDateTime     generatedAt;

    private SyntheticDataBundle(
            List<SyntheticMerchant> merchants,
            List<SyntheticOrder> orders,
            List<SyntheticOrderItem> orderItems,
            List<SyntheticPayment> payments,
            List<SyntheticInventoryMovement> inventoryMovements,
            List<SyntheticRepActivity> repActivities,
            List<SyntheticCreditEvaluation> creditEvaluations,
            long generationSeed,
            SyntheticDataConfig config) {

        this.merchants          = List.copyOf(merchants);
        this.orders             = List.copyOf(orders);
        this.orderItems         = List.copyOf(orderItems);
        this.payments           = List.copyOf(payments);
        this.inventoryMovements = List.copyOf(inventoryMovements);
        this.repActivities      = List.copyOf(repActivities);
        this.creditEvaluations  = List.copyOf(creditEvaluations);
        this.generationSeed     = generationSeed;
        this.config             = config;
        this.generatedAt        = LocalDateTime.now();

        // Build cross-reference maps
        this.merchantOrders = orders.stream()
                .collect(Collectors.groupingBy(
                        SyntheticOrder::merchantRef,
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));

        this.orderItems_byOrder = orderItems.stream()
                .collect(Collectors.groupingBy(
                        SyntheticOrderItem::orderRef,
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));

        this.orderPayments = payments.stream()
                .collect(Collectors.groupingBy(
                        SyntheticPayment::invoiceRef,
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));

        this.merchantPayments = payments.stream()
                .collect(Collectors.groupingBy(
                        SyntheticPayment::merchantRef,
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));

        this.merchantActivities = repActivities.stream()
                .collect(Collectors.groupingBy(
                        SyntheticRepActivity::merchantRef,
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));

        this.merchantCreditHistory = creditEvaluations.stream()
                .collect(Collectors.groupingBy(
                        SyntheticCreditEvaluation::merchantRef,
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));
    }

    /**
     * Factory method — builds the bundle and computes all cross-reference maps.
     */
    public static SyntheticDataBundle create(
            List<SyntheticMerchant> merchants,
            List<SyntheticOrder> orders,
            List<SyntheticOrderItem> orderItems,
            List<SyntheticPayment> payments,
            List<SyntheticInventoryMovement> inventoryMovements,
            List<SyntheticRepActivity> repActivities,
            List<SyntheticCreditEvaluation> creditEvaluations,
            long generationSeed,
            SyntheticDataConfig config) {

        return new SyntheticDataBundle(
                merchants, orders, orderItems, payments,
                inventoryMovements, repActivities, creditEvaluations,
                generationSeed, config);
    }

    // -------------------------------------------------------------------------
    // List accessors
    // -------------------------------------------------------------------------

    public List<SyntheticMerchant>          getMerchants()          { return merchants; }
    public List<SyntheticOrder>             getOrders()             { return orders; }
    public List<SyntheticOrderItem>         getOrderItems()         { return orderItems; }
    public List<SyntheticPayment>           getPayments()           { return payments; }
    public List<SyntheticInventoryMovement> getInventoryMovements() { return inventoryMovements; }
    public List<SyntheticRepActivity>       getRepActivities()      { return repActivities; }
    public List<SyntheticCreditEvaluation>  getCreditEvaluations()  { return creditEvaluations; }

    // -------------------------------------------------------------------------
    // Cross-reference accessors — return empty list when no data found
    // -------------------------------------------------------------------------

    public List<SyntheticOrder> getOrdersForMerchant(UUID merchantId) {
        return merchantOrders.getOrDefault(merchantId, List.of());
    }

    public List<SyntheticOrderItem> getItemsForOrder(UUID orderId) {
        return orderItems_byOrder.getOrDefault(orderId, List.of());
    }

    public List<SyntheticPayment> getPaymentsForOrder(UUID orderId) {
        return orderPayments.getOrDefault(orderId, List.of());
    }

    public List<SyntheticPayment> getPaymentsForMerchant(UUID merchantId) {
        return merchantPayments.getOrDefault(merchantId, List.of());
    }

    public List<SyntheticRepActivity> getActivitiesForMerchant(UUID merchantId) {
        return merchantActivities.getOrDefault(merchantId, List.of());
    }

    public List<SyntheticCreditEvaluation> getCreditHistoryForMerchant(UUID merchantId) {
        return merchantCreditHistory.getOrDefault(merchantId, List.of());
    }

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    public long              getGenerationSeed() { return generationSeed; }
    public SyntheticDataConfig getConfig()        { return config; }
    public LocalDateTime     getGeneratedAt()    { return generatedAt; }

    /** Summary counts for logging and audit. */
    public Map<String, Integer> getRecordCounts() {
        return Map.of(
                "merchants",          merchants.size(),
                "orders",             orders.size(),
                "orderItems",         orderItems.size(),
                "payments",           payments.size(),
                "inventoryMovements", inventoryMovements.size(),
                "repActivities",      repActivities.size(),
                "creditEvaluations",  creditEvaluations.size()
        );
    }
}
