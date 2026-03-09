package com.zuqi.repository;

import com.zuqi.domain.product.ProductBranchPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductBranchPriceRepository extends JpaRepository<ProductBranchPrice, UUID> {

    List<ProductBranchPrice> findByProductId(UUID productId);

    Optional<ProductBranchPrice> findByProductIdAndBranchId(UUID productId, UUID branchId);

    @Modifying
    @Query("DELETE FROM ProductBranchPrice pbp WHERE pbp.product.id = :productId")
    void deleteByProductId(@Param("productId") UUID productId);

    @Query("SELECT pbp FROM ProductBranchPrice pbp WHERE pbp.product.id IN :productIds")
    List<ProductBranchPrice> findByProductIdIn(@Param("productIds") List<UUID> productIds);
}
