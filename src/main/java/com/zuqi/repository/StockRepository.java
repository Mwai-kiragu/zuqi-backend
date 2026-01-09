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

    @Query("SELECT s FROM Stock s WHERE s.warehouse.distributor.id = :distributorId " +
            "AND s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel")
    Page<Stock> findLowStockByDistributorId(@Param("distributorId") UUID distributorId, Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel")
    Page<Stock> findAllLowStock(Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.warehouse.id = :warehouseId AND s.product.id IN :productIds")
    List<Stock> findByWarehouseIdAndProductIdIn(
            @Param("warehouseId") UUID warehouseId,
            @Param("productIds") List<UUID> productIds);

    boolean existsByWarehouseIdAndProductId(UUID warehouseId, UUID productId);

    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM Stock s WHERE s.product.id = :productId")
    java.math.BigDecimal getTotalQuantityByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.warehouse.distributor.id = :distributorId " +
            "AND s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel AND s.quantity > 0")
    long countLowStockByDistributorId(@Param("distributorId") UUID distributorId);

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.warehouse.distributor.id = :distributorId " +
            "AND s.quantity <= 0")
    long countOutOfStockByDistributorId(@Param("distributorId") UUID distributorId);

    // Global queries for SUPER_ADMIN/ADMIN (no distributor filter)
    @Query("SELECT COUNT(s) FROM Stock s WHERE s.reorderLevel IS NOT NULL AND s.quantity <= s.reorderLevel AND s.quantity > 0")
    long countAllLowStock();

    @Query("SELECT COUNT(s) FROM Stock s WHERE s.quantity <= 0")
    long countAllOutOfStock();
}
