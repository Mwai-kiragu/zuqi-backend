package com.zuqi.repository;

import com.zuqi.domain.inventory.Stock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockRepository extends JpaRepository<Stock, UUID> {

    Optional<Stock> findByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

    Page<Stock> findByWarehouseId(UUID warehouseId, Pageable pageable);

    List<Stock> findByProductId(UUID productId);

    @Query("SELECT s FROM Stock s WHERE s.product.id = :productId AND s.quantity > 0")
    List<Stock> findAvailableStockByProductId(@Param("productId") UUID productId);

    @Query("SELECT s FROM Stock s WHERE s.warehouse.id = :warehouseId " +
            "AND s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel")
    List<Stock> findLowStockByWarehouseId(@Param("warehouseId") UUID warehouseId);

    @Query("SELECT s FROM Stock s JOIN FETCH s.product JOIN FETCH s.warehouse " +
            "WHERE s.warehouse.distributor.id = :distributorId " +
            "AND s.reorderLevel IS NOT NULL AND s.quantity > 0 AND s.quantity <= s.reorderLevel")
    Page<Stock> findLowStockByDistributorId(@Param("distributorId") UUID distributorId, Pageable pageable);

    @Query("SELECT s FROM Stock s JOIN FETCH s.product JOIN FETCH s.warehouse " +
            "WHERE s.warehouse.distributor.merchant.id = :merchantId " +
            "AND s.reorderLevel IS NOT NULL AND s.quantity > 0 AND s.quantity <= s.reorderLevel")
    Page<Stock> findLowStockByMerchantIdFetched(@Param("merchantId") UUID merchantId, Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel")
    Page<Stock> findAllLowStock(Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.warehouse.id = :warehouseId AND s.product.id IN :productIds")
    List<Stock> findByWarehouseIdAndProductIdIn(
            @Param("warehouseId") UUID warehouseId,
            @Param("productIds") List<UUID> productIds);

    boolean existsByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

    @Query("SELECT s.product.id, COALESCE(SUM(s.quantity), 0) FROM Stock s WHERE s.product.id IN :productIds GROUP BY s.product.id")
    List<Object[]> findTotalStockByProductIds(@Param("productIds") List<UUID> productIds);

    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM Stock s WHERE s.product.id = :productId")
    java.math.BigDecimal getTotalQuantityByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.warehouse.distributor.id = :distributorId " +
            "AND s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel AND s.quantity > 0")
    long countLowStockByDistributorId(@Param("distributorId") UUID distributorId);

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.warehouse.distributor.id = :distributorId " +
            "AND s.quantity <= 0")
    long countOutOfStockByDistributorId(@Param("distributorId") UUID distributorId);

    @Query("SELECT s FROM Stock s JOIN FETCH s.product JOIN FETCH s.warehouse " +
            "WHERE s.warehouse.distributor.id = :distributorId AND s.quantity <= 0")
    List<Stock> findOutOfStockByDistributorId(@Param("distributorId") UUID distributorId);

    /** Sum of (quantity × costPrice) for all in-stock items of a distributor (stock valuation). */
    @Query("SELECT COALESCE(SUM(s.quantity * COALESCE(s.product.costPrice, s.product.unitPrice)), 0) " +
            "FROM Stock s WHERE s.warehouse.distributor.id = :distributorId AND s.quantity > 0")
    BigDecimal sumStockValueByDistributorId(@Param("distributorId") UUID distributorId);

    /** Per-warehouse product count and low-stock count for a distributor. */
    @Query("SELECT s.warehouse.id, s.warehouse.name, COUNT(s), " +
            "SUM(CASE WHEN s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel AND s.quantity > 0 THEN 1 ELSE 0 END) " +
            "FROM Stock s WHERE s.warehouse.distributor.id = :distributorId GROUP BY s.warehouse.id, s.warehouse.name")
    List<Object[]> warehouseSummaryByDistributorId(@Param("distributorId") UUID distributorId);

    @Query("SELECT COALESCE(SUM(s.quantity * COALESCE(s.product.costPrice, s.product.unitPrice)), 0) " +
            "FROM Stock s WHERE s.warehouse.distributor.merchant.id = :merchantId AND s.quantity > 0")
    BigDecimal sumStockValueByMerchantId(@Param("merchantId") UUID merchantId);

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.warehouse.distributor.merchant.id = :merchantId " +
            "AND s.reorderLevel IS NOT NULL AND s.quantity > 0 AND s.quantity <= s.reorderLevel")
    long countLowStockByMerchantId(@Param("merchantId") UUID merchantId);

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.warehouse.distributor.merchant.id = :merchantId " +
            "AND s.quantity <= 0")
    long countOutOfStockByMerchantId(@Param("merchantId") UUID merchantId);

    @Query("SELECT s FROM Stock s JOIN FETCH s.product JOIN FETCH s.warehouse " +
            "WHERE s.warehouse.distributor.merchant.id = :merchantId AND s.quantity <= 0")
    List<Stock> findOutOfStockByMerchantId(@Param("merchantId") UUID merchantId);

    @Query("SELECT s.warehouse.id, s.warehouse.name, COUNT(s), " +
            "SUM(CASE WHEN s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel AND s.quantity > 0 THEN 1 ELSE 0 END) " +
            "FROM Stock s WHERE s.warehouse.distributor.merchant.id = :merchantId GROUP BY s.warehouse.id, s.warehouse.name")
    List<Object[]> warehouseSummaryByMerchantId(@Param("merchantId") UUID merchantId);

    // Global queries for SUPER_ADMIN/ADMIN (no distributor filter)
    @Query("SELECT COUNT(s) FROM Stock s WHERE s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel AND s.quantity > 0")
    long countAllLowStock();

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.quantity <= 0")
    long countAllOutOfStock();

    @Query("SELECT s FROM Stock s WHERE " +
            "(:distributorId IS NULL OR s.warehouse.distributor.id = :distributorId) AND " +
            "(:warehouseId IS NULL OR s.warehouse.id = :warehouseId) AND " +
            "(:branchId IS NULL OR s.warehouse.branch.id = :branchId) AND " +
            "(:search IS NULL OR LOWER(s.product.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "  OR LOWER(s.product.sku) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Stock> findByFilters(
            @Param("distributorId") UUID distributorId,
            @Param("warehouseId") UUID warehouseId,
            @Param("branchId") UUID branchId,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT s FROM Stock s JOIN FETCH s.warehouse JOIN FETCH s.product WHERE s.warehouse.distributor.id = :distributorId")
    List<Stock> findAllByDistributorIdFetched(@Param("distributorId") UUID distributorId);

    @Query("SELECT s FROM Stock s JOIN FETCH s.warehouse JOIN FETCH s.product WHERE s.warehouse.distributor.merchant.id = :merchantId")
    List<Stock> findAllByMerchantIdFetched(@Param("merchantId") UUID merchantId);

    @Query("SELECT s FROM Stock s JOIN FETCH s.warehouse w JOIN FETCH w.distributor JOIN FETCH s.product " +
            "WHERE s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel " +
            "AND (s.lastLowStockAlertSentAt IS NULL OR s.lastLowStockAlertSentAt < :threshold)")
    List<Stock> findLowStockNotRecentlyAlerted(@Param("threshold") java.time.LocalDateTime threshold);

    @Query("SELECT s FROM Stock s JOIN FETCH s.warehouse JOIN FETCH s.product")
    List<Stock> findAllFetched();
}
