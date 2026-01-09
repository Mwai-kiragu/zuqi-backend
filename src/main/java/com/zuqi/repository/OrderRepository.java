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

    @Query("SELECT o FROM Order o WHERE o.distributor.id = :distributorId " +
            "AND (:status IS NULL OR o.status = :status) " +
            "AND (:merchantId IS NULL OR o.merchant.id = :merchantId) " +
            "AND (:salesRepId IS NULL OR o.salesRep.id = :salesRepId) " +
            "AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus) " +
            "AND (:startDate IS NULL OR o.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR o.createdAt <= :endDate)")
    Page<Order> findByFilters(
            @Param("distributorId") UUID distributorId,
            @Param("status") OrderStatus status,
            @Param("merchantId") UUID merchantId,
            @Param("salesRepId") UUID salesRepId,
            @Param("paymentStatus") PaymentStatus paymentStatus,
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
}
