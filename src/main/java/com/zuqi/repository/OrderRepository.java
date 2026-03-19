package com.zuqi.repository;

import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.order.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<Order> findByMerchantId(UUID merchantId, Pageable pageable);

    Page<Order> findBySalesRepId(UUID salesRepId, Pageable pageable);

    Page<Order> findByWarehouseId(UUID warehouseId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.distributor.id = :distributorId AND o.status = :status")
    Page<Order> findByDistributorIdAndStatus(
            @Param("distributorId") UUID distributorId,
            @Param("status") OrderStatus status,
            Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.distributor.id = :distributorId AND o.merchant.id = :merchantId")
    Page<Order> findByDistributorIdAndMerchantId(
            @Param("distributorId") UUID distributorId,
            @Param("merchantId") UUID merchantId,
            Pageable pageable);

    @Query(value = "SELECT * FROM orders o WHERE o.distributor_id = :distributorId " +
            "AND (:status IS NULL OR o.status = CAST(:status AS VARCHAR)) " +
            "AND (CAST(:merchantId AS UUID) IS NULL OR o.merchant_id = CAST(:merchantId AS UUID)) " +
            "AND (CAST(:salesRepId AS UUID) IS NULL OR o.sales_rep_id = CAST(:salesRepId AS UUID)) " +
            "AND (:paymentStatus IS NULL OR o.payment_status = CAST(:paymentStatus AS VARCHAR)) " +
            "AND (CAST(:startDate AS TIMESTAMP) IS NULL OR o.created_at >= CAST(:startDate AS TIMESTAMP)) " +
            "AND (CAST(:endDate AS TIMESTAMP) IS NULL OR o.created_at <= CAST(:endDate AS TIMESTAMP)) " +
            "ORDER BY o.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM orders o WHERE o.distributor_id = :distributorId " +
            "AND (:status IS NULL OR o.status = CAST(:status AS VARCHAR)) " +
            "AND (CAST(:merchantId AS UUID) IS NULL OR o.merchant_id = CAST(:merchantId AS UUID)) " +
            "AND (CAST(:salesRepId AS UUID) IS NULL OR o.sales_rep_id = CAST(:salesRepId AS UUID)) " +
            "AND (:paymentStatus IS NULL OR o.payment_status = CAST(:paymentStatus AS VARCHAR)) " +
            "AND (CAST(:startDate AS TIMESTAMP) IS NULL OR o.created_at >= CAST(:startDate AS TIMESTAMP)) " +
            "AND (CAST(:endDate AS TIMESTAMP) IS NULL OR o.created_at <= CAST(:endDate AS TIMESTAMP))",
            nativeQuery = true)
    Page<Order> findByFilters(
            @Param("distributorId") UUID distributorId,
            @Param("status") String status,
            @Param("merchantId") UUID merchantId,
            @Param("salesRepId") UUID salesRepId,
            @Param("paymentStatus") String paymentStatus,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND (o.orderNumber LIKE %:search% OR o.merchant.businessName LIKE %:search%)")
    Page<Order> searchOrders(
            @Param("distributorId") UUID distributorId,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.distributor.id = :distributorId AND o.status = :status")
    long countByDistributorIdAndStatus(@Param("distributorId") UUID distributorId, @Param("status") OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.paymentDueDate <= :dueDate AND o.paymentStatus != 'PAID'")
    List<Order> findOverdueOrders(@Param("dueDate") LocalDate dueDate);

    @Query("SELECT o FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.createdAt >= :startDate AND o.createdAt <= :endDate")
    List<Order> findByDistributorIdAndDateRange(
            @Param("distributorId") UUID distributorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.distributor.id = :distributorId")
    long countByDistributorId(@Param("distributorId") UUID distributorId);

    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(order_number, LENGTH(:prefix) + 1) AS INTEGER)), 0) " +
            "FROM orders WHERE order_number LIKE :prefix%", nativeQuery = true)
    Integer findMaxOrderNumberByPrefix(@Param("prefix") String prefix);

    // Dashboard queries
    @Query("SELECT COUNT(o) FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.createdAt >= :startDate")
    long countOrdersToday(@Param("distributorId") UUID distributorId, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.distributor.id = :distributorId")
    java.math.BigDecimal sumTotalRevenue(@Param("distributorId") UUID distributorId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.createdAt >= :startDate")
    java.math.BigDecimal sumRevenueFromDate(@Param("distributorId") UUID distributorId,
            @Param("startDate") LocalDateTime startDate);

    @Query("SELECT COALESCE(SUM(o.totalAmount - o.paidAmount), 0) FROM Order o " +
            "WHERE o.distributor.id = :distributorId AND o.paymentStatus != 'PAID'")
    java.math.BigDecimal sumOutstandingAmount(@Param("distributorId") UUID distributorId);

    @Query("SELECT COALESCE(SUM(o.totalAmount - o.paidAmount), 0) FROM Order o " +
            "WHERE o.distributor.id = :distributorId AND o.paymentStatus != 'PAID' " +
            "AND o.createdAt >= :startDate AND o.createdAt <= :endDate")
    java.math.BigDecimal sumOutstandingAmountInPeriod(@Param("distributorId") UUID distributorId,
                                                       @Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    @Query("SELECT o FROM Order o WHERE o.distributor.id = :distributorId ORDER BY o.createdAt DESC")
    Page<Order> findRecentOrders(@Param("distributorId") UUID distributorId, Pageable pageable);

    @Query("SELECT o.merchant.id, o.merchant.businessName, o.merchant.city, COUNT(o), SUM(o.totalAmount) " +
            "FROM Order o WHERE o.distributor.id = :distributorId AND o.status = 'DELIVERED' " +
            "GROUP BY o.merchant.id, o.merchant.businessName, o.merchant.city " +
            "ORDER BY SUM(o.totalAmount) DESC")
    List<Object[]> findTopMerchantsByRevenue(@Param("distributorId") UUID distributorId, Pageable pageable);

    @Query("SELECT CAST(o.createdAt AS LocalDate), COUNT(o), SUM(o.totalAmount) " +
            "FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.createdAt >= :startDate AND o.createdAt <= :endDate " +
            "GROUP BY CAST(o.createdAt AS LocalDate) ORDER BY CAST(o.createdAt AS LocalDate)")
    List<Object[]> findDailyRevenueData(@Param("distributorId") UUID distributorId,
            @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Branch-filtered dashboard queries
    @Query("SELECT COUNT(o) FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.warehouse.branch.id = :branchId")
    long countByDistributorIdAndBranch(@Param("distributorId") UUID distributorId,
                                        @Param("branchId") UUID branchId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.status = :status AND o.warehouse.branch.id = :branchId")
    long countByDistributorIdAndStatusAndBranch(@Param("distributorId") UUID distributorId,
                                                 @Param("status") OrderStatus status,
                                                 @Param("branchId") UUID branchId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.createdAt >= :startDate AND o.createdAt <= :endDate " +
            "AND o.warehouse.branch.id = :branchId")
    long countOrdersInPeriodByBranch(@Param("distributorId") UUID distributorId,
                                      @Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate,
                                      @Param("branchId") UUID branchId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.warehouse.branch.id = :branchId")
    java.math.BigDecimal sumTotalRevenueByBranch(@Param("distributorId") UUID distributorId,
                                                  @Param("branchId") UUID branchId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.createdAt >= :startDate AND o.createdAt <= :endDate " +
            "AND o.warehouse.branch.id = :branchId")
    java.math.BigDecimal sumRevenueInPeriodByBranch(@Param("distributorId") UUID distributorId,
                                                     @Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate,
                                                     @Param("branchId") UUID branchId);

    @Query("SELECT COALESCE(SUM(o.totalAmount - o.paidAmount), 0) FROM Order o " +
            "WHERE o.distributor.id = :distributorId AND o.paymentStatus != 'PAID' " +
            "AND o.warehouse.branch.id = :branchId")
    java.math.BigDecimal sumOutstandingAmountByBranch(@Param("distributorId") UUID distributorId,
                                                       @Param("branchId") UUID branchId);

    @Query("SELECT COALESCE(SUM(o.totalAmount - o.paidAmount), 0) FROM Order o " +
            "WHERE o.distributor.id = :distributorId AND o.paymentStatus != 'PAID' " +
            "AND o.warehouse.branch.id = :branchId " +
            "AND o.createdAt >= :startDate AND o.createdAt <= :endDate")
    java.math.BigDecimal sumOutstandingAmountInPeriodByBranch(@Param("distributorId") UUID distributorId,
                                                               @Param("branchId") UUID branchId,
                                                               @Param("startDate") LocalDateTime startDate,
                                                               @Param("endDate") LocalDateTime endDate);

    @Query("SELECT o FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.warehouse.branch.id = :branchId ORDER BY o.createdAt DESC")
    Page<Order> findRecentOrdersByBranch(@Param("distributorId") UUID distributorId,
                                          @Param("branchId") UUID branchId,
                                          Pageable pageable);

    // Period-scoped status count (for pulse: delivered/pending in period)
    @Query("SELECT COUNT(o) FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.status = :status AND o.createdAt >= :startDate AND o.createdAt <= :endDate")
    long countOrdersInPeriodWithStatus(@Param("distributorId") UUID distributorId,
                                        @Param("status") OrderStatus status,
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.status = :status AND o.createdAt >= :startDate AND o.createdAt <= :endDate " +
            "AND o.warehouse.branch.id = :branchId")
    long countOrdersInPeriodWithStatusByBranch(@Param("distributorId") UUID distributorId,
                                                @Param("status") OrderStatus status,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate,
                                                @Param("branchId") UUID branchId);

    // Date-range variants (no branch filter) for pulse period tabs
    @Query("SELECT COUNT(o) FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.createdAt >= :startDate AND o.createdAt <= :endDate")
    long countOrdersInPeriod(@Param("distributorId") UUID distributorId,
                              @Param("startDate") LocalDateTime startDate,
                              @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND o.createdAt >= :startDate AND o.createdAt <= :endDate")
    java.math.BigDecimal sumRevenueInPeriod(@Param("distributorId") UUID distributorId,
                                             @Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);

    // Global queries for SUPER_ADMIN/ADMIN (no distributor filter)
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    long countByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :startDate")
    long countOrdersTodayAll(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o")
    java.math.BigDecimal sumTotalRevenueAll();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.createdAt >= :startDate")
    java.math.BigDecimal sumRevenueFromDateAll(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT COALESCE(SUM(o.totalAmount - o.paidAmount), 0) FROM Order o WHERE o.paymentStatus != 'PAID'")
    java.math.BigDecimal sumOutstandingAmountAll();

    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
    Page<Order> findRecentOrdersAll(Pageable pageable);

    /** Scope to all orders for a merchant brand (MERCHANT_ADMIN). */
    Page<Order> findByDistributorMerchantId(UUID merchantId, Pageable pageable);

    /** Find the Order linked to a specific POS sale. */
    Optional<Order> findByPosSaleId(UUID posSaleId);

    @Query("SELECT o FROM Order o WHERE o.distributor.merchant.id = :merchantId " +
            "AND (o.orderNumber LIKE %:search% OR o.merchant.businessName LIKE %:search%)")
    Page<Order> searchOrdersByMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("search") String search,
            Pageable pageable);

    // AI Feature Engineering - Historical queries
    @Query("SELECT o FROM Order o WHERE o.merchant.id = :merchantId AND o.createdAt < :asOfDate")
    List<Order> findByMerchantIdAndCreatedAtBefore(
            @Param("merchantId") UUID merchantId,
            @Param("asOfDate") LocalDateTime asOfDate);

    /** Sum of unpaid/partial amounts for a specific customer (used for real-time credit utilization). */
    @Query("SELECT COALESCE(SUM(o.totalAmount - o.paidAmount), 0) FROM Order o " +
            "WHERE o.merchant.id = :customerId AND o.paymentStatus != 'PAID'")
    java.math.BigDecimal sumOutstandingByCustomerId(@Param("customerId") UUID customerId);

    /** Top products sold by revenue within a date range (for sales report). */
    @Query("SELECT oi.product.id, oi.product.name, oi.product.sku, SUM(oi.quantity), SUM(oi.totalAmount) " +
            "FROM OrderItem oi WHERE oi.order.distributor.id = :distributorId " +
            "AND oi.order.createdAt >= :startDate AND oi.order.createdAt <= :endDate " +
            "GROUP BY oi.product.id, oi.product.name, oi.product.sku " +
            "ORDER BY SUM(oi.totalAmount) DESC")
    List<Object[]> findTopProductsSold(@Param("distributorId") UUID distributorId,
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate,
                                        Pageable pageable);

    // AI feature queries (Phase 2 plan — Section 1.4)
    @Query("SELECT COUNT(o) FROM Order o WHERE o.merchant.id = :merchantId AND o.createdAt > :after")
    long countByMerchantIdAndCreatedAtAfter(@Param("merchantId") UUID merchantId,
                                            @Param("after") LocalDateTime after);

    @Query("SELECT o FROM Order o WHERE o.merchant.id = :customerId AND o.distributor.id = :distributorId")
    List<Order> findByCustomerIdAndDistributorId(@Param("customerId") UUID customerId,
                                                 @Param("distributorId") UUID distributorId);

    // AI Phase 3 — cash flow feature queries
    @Query("SELECT o FROM Order o WHERE o.distributor.id = :distributorId " +
           "AND o.status IN ('PENDING', 'CONFIRMED', 'PROCESSING')")
    List<Order> findPendingOrdersByDistributorId(@Param("distributorId") UUID distributorId);
}
