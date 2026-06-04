package com.zuqi.repository;

import com.zuqi.domain.product.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findByDistributorIdAndActiveTrue(UUID distributorId);

    /** Scope to a merchant brand (MERCHANT_ADMIN) — all statuses. */
    List<ProductCategory> findByDistributorMerchantId(UUID merchantId);

    /** Scope to a merchant brand (MERCHANT_ADMIN). */
    List<ProductCategory> findByDistributorMerchantIdAndActiveTrue(UUID merchantId);

    List<ProductCategory> findByDistributorMerchantIdAndActiveFalse(UUID merchantId);

    List<ProductCategory> findByDistributorIdAndActiveFalse(UUID distributorId);

    List<ProductCategory> findByDistributorId(UUID distributorId);

    List<ProductCategory> findByDistributorIdAndParentIsNull(UUID distributorId);

    List<ProductCategory> findByDistributorIdAndParentIsNullAndActiveTrue(UUID distributorId);

    Optional<ProductCategory> findByNameAndDistributorId(String name, UUID distributorId);

    boolean existsByNameAndDistributorId(String name, UUID distributorId);

    List<ProductCategory> findByParentId(Long parentId);

    List<ProductCategory> findByParentIdAndActiveTrue(Long parentId);

    @Query("SELECT c FROM ProductCategory c LEFT JOIN FETCH c.parent WHERE c.distributor.id = :distributorId ORDER BY c.name")
    List<ProductCategory> findByDistributorIdFetchedForExport(@Param("distributorId") UUID distributorId);

    @Query("SELECT c FROM ProductCategory c LEFT JOIN FETCH c.parent WHERE c.distributor.merchant.id = :merchantId ORDER BY c.name")
    List<ProductCategory> findByMerchantIdFetchedForExport(@Param("merchantId") UUID merchantId);

    @Query(value = "SELECT c.* FROM product_categories c " +
           "LEFT JOIN distributors d ON c.distributor_id = d.id " +
           "WHERE c.distributor_id = :distributorId " +
           "AND (CAST(:active AS boolean) IS NULL OR c.active = CAST(:active AS boolean)) " +
           "AND (CAST(:search AS text) IS NULL OR LOWER(c.name) LIKE '%' || LOWER(CAST(:search AS text)) || '%' OR LOWER(COALESCE(c.description,'')) LIKE '%' || LOWER(CAST(:search AS text)) || '%') " +
           "AND (CAST(:startDate AS timestamp) IS NULL OR c.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) IS NULL OR c.created_at < CAST(:endDate AS timestamp)) " +
           "ORDER BY c.name",
           nativeQuery = true)
    List<ProductCategory> findFiltered(@Param("distributorId") UUID distributorId,
                                       @Param("active") Boolean active,
                                       @Param("search") String search,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT c.* FROM product_categories c " +
           "LEFT JOIN distributors d ON c.distributor_id = d.id " +
           "LEFT JOIN merchants m ON d.merchant_id = m.id " +
           "WHERE m.id = :merchantId " +
           "AND (CAST(:active AS boolean) IS NULL OR c.active = CAST(:active AS boolean)) " +
           "AND (CAST(:search AS text) IS NULL OR LOWER(c.name) LIKE '%' || LOWER(CAST(:search AS text)) || '%' OR LOWER(COALESCE(c.description,'')) LIKE '%' || LOWER(CAST(:search AS text)) || '%') " +
           "AND (CAST(:startDate AS timestamp) IS NULL OR c.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) IS NULL OR c.created_at < CAST(:endDate AS timestamp)) " +
           "ORDER BY c.name",
           nativeQuery = true)
    List<ProductCategory> findFilteredByMerchant(@Param("merchantId") UUID merchantId,
                                                  @Param("active") Boolean active,
                                                  @Param("search") String search,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT c.* FROM product_categories c " +
           "WHERE (CAST(:active AS boolean) IS NULL OR c.active = CAST(:active AS boolean)) " +
           "AND (CAST(:search AS text) IS NULL OR LOWER(c.name) LIKE '%' || LOWER(CAST(:search AS text)) || '%' OR LOWER(COALESCE(c.description,'')) LIKE '%' || LOWER(CAST(:search AS text)) || '%') " +
           "AND (CAST(:startDate AS timestamp) IS NULL OR c.created_at >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) IS NULL OR c.created_at < CAST(:endDate AS timestamp)) " +
           "ORDER BY c.name",
           nativeQuery = true)
    List<ProductCategory> findFilteredAll(@Param("active") Boolean active,
                                          @Param("search") String search,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);
}
