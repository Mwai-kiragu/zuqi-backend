package com.zuqi.repository;

import com.zuqi.domain.ai.ReorderSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReorderSuggestionRepository extends JpaRepository<ReorderSuggestion, UUID> {

    @Query("SELECT rs FROM ReorderSuggestion rs " +
           "JOIN FETCH rs.distributor " +
           "JOIN FETCH rs.warehouse " +
           "JOIN FETCH rs.product " +
           "WHERE rs.distributor.id = :distributorId " +
           "AND rs.warehouse.id = :warehouseId " +
           "AND rs.status = :status")
    List<ReorderSuggestion> findByDistributorIdAndWarehouseIdAndStatus(
            @Param("distributorId") UUID distributorId,
            @Param("warehouseId") UUID warehouseId,
            @Param("status") String status);

    @Query("SELECT rs FROM ReorderSuggestion rs " +
           "JOIN FETCH rs.distributor " +
           "JOIN FETCH rs.warehouse " +
           "JOIN FETCH rs.product " +
           "WHERE rs.distributor.id = :distributorId AND rs.status = :status " +
           "ORDER BY rs.computedAt DESC")
    List<ReorderSuggestion> findByDistributorIdAndStatus(
            @Param("distributorId") UUID distributorId,
            @Param("status") String status);

    @Query("SELECT rs FROM ReorderSuggestion rs " +
           "JOIN FETCH rs.distributor " +
           "JOIN FETCH rs.warehouse " +
           "JOIN FETCH rs.product " +
           "WHERE rs.distributor.id = :distributorId " +
           "ORDER BY rs.computedAt DESC")
    List<ReorderSuggestion> findAllByDistributorIdFetched(@Param("distributorId") UUID distributorId);

    @Query("SELECT rs FROM ReorderSuggestion rs WHERE rs.distributor.id = :distributorId " +
           "AND rs.product.id = :productId ORDER BY rs.computedAt DESC")
    List<ReorderSuggestion> findLatestByDistributorAndProduct(
            @Param("distributorId") UUID distributorId,
            @Param("productId") UUID productId);

    @Query("SELECT rs FROM ReorderSuggestion rs WHERE rs.distributor.id = :distributorId " +
           "AND rs.status = 'PENDING' AND rs.dataPhase = :dataPhase")
    List<ReorderSuggestion> findPendingByDistributorAndPhase(
            @Param("distributorId") UUID distributorId,
            @Param("dataPhase") String dataPhase);
}
