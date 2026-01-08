package com.zuqi.repository;

import com.zuqi.domain.inventory.Stock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Stock entity operations.
 */
@Repository
public interface StockRepository extends JpaRepository<Stock, UUID> {

    /**
     * Find stock by warehouse and product.
     */
    Optional<Stock> findByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

    /**
     * Find all stock for a warehouse.
     */
    Page<Stock> findByWarehouseId(UUID warehouseId, Pageable pageable);

    /**
     * Find all stock for a product.
     */
    List<Stock> findByProductId(UUID productId);

    /**
     * Find stock for a product across all warehouses.
     */
    @Query("SELECT s FROM Stock s WHERE s.product.id = :productId AND s.quantity > 0")
    List<Stock> findAvailableStockByProductId(@Param("productId") UUID productId);

    /**
     * Find low stock items for a warehouse.
     */
    @Query("SELECT s FROM Stock s WHERE s.warehouse.id = :warehouseId " +
            "AND s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel")
    List<Stock> findLowStockByWarehouseId(@Param("warehouseId") UUID warehouseId);

    /**
     * Find low stock items for a distributor.
     */
    @Query("SELECT s FROM Stock s WHERE s.warehouse.distributor.id = :distributorId " +
            "AND s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel")
    Page<Stock> findLowStockByDistributorId(@Param("distributorId") UUID distributorId, Pageable pageable);

    /**
     * Find stock by warehouse and product IDs.
     */
    @Query("SELECT s FROM Stock s WHERE s.warehouse.id = :warehouseId AND s.product.id IN :productIds")
    List<Stock> findByWarehouseIdAndProductIdIn(
            @Param("warehouseId") UUID warehouseId,
            @Param("productIds") List<UUID> productIds);

    /**
     * Check if stock exists for warehouse and product.
     */
    boolean existsByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

    /**
     * Get total stock quantity for a product across all warehouses.
     */
    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM Stock s WHERE s.product.id = :productId")
    java.math.BigDecimal getTotalQuantityByProductId(@Param("productId") UUID productId);

    /**
     * Count low stock items for a distributor.
     */
    @Query("SELECT COUNT(s) FROM Stock s WHERE s.warehouse.distributor.id = :distributorId " +
            "AND s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel AND s.quantity > 0")
    long countLowStockByDistributorId(@Param("distributorId") UUID distributorId);

    /**
     * Count out of stock items for a distributor.
     */
    @Query("SELECT COUNT(s) FROM Stock s WHERE s.warehouse.distributor.id = :distributorId " +
            "AND s.quantity <= 0")
    long countOutOfStockByDistributorId(@Param("distributorId") UUID distributorId);
}
