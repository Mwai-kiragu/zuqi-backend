package com.zuqi.repository;

import com.zuqi.domain.supplier.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Page<Supplier> findByActiveTrue(Pageable pageable);

    Page<Supplier> findByActiveFalse(Pageable pageable);

    Page<Supplier> findByDistributorIdAndActiveTrue(UUID distributorId, Pageable pageable);

    Page<Supplier> findByBlacklistedTrue(Pageable pageable);

    Page<Supplier> findByDistributorIdAndBlacklistedTrue(UUID distributorId, Pageable pageable);

    Optional<Supplier> findBySupplierCode(String supplierCode);

    Optional<Supplier> findByKraPin(String kraPin);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByKraPin(String kraPin);

    @Query("SELECT COUNT(s) FROM Supplier s")
    long countAll();

    @Query("SELECT s FROM Supplier s WHERE s.active = true AND " +
            "(LOWER(s.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "s.phone LIKE CONCAT('%', :searchTerm, '%') OR " +
            "LOWER(s.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Supplier> searchActive(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT s FROM Supplier s WHERE s.distributor.id = :distributorId AND s.active = true AND " +
            "(LOWER(s.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "s.phone LIKE CONCAT('%', :searchTerm, '%'))")
    Page<Supplier> searchByDistributor(@Param("distributorId") UUID distributorId,
                                       @Param("searchTerm") String searchTerm,
                                       Pageable pageable);

    /** Scope to a merchant brand (MERCHANT_ADMIN). */
    Page<Supplier> findByDistributorMerchantIdAndActiveTrue(UUID merchantId, Pageable pageable);

    Page<Supplier> findByDistributorMerchantIdAndBlacklistedTrue(UUID merchantId, Pageable pageable);

    @Query("SELECT s FROM Supplier s WHERE s.distributor.merchant.id = :merchantId AND s.active = true AND " +
            "(LOWER(s.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "s.phone LIKE CONCAT('%', :searchTerm, '%'))")
    Page<Supplier> searchByMerchant(@Param("merchantId") UUID merchantId,
                                    @Param("searchTerm") String searchTerm,
                                    Pageable pageable);

    @Modifying
    @Query("UPDATE Supplier s SET s.approvalStatus = :status WHERE s.id = :id")
    void updateApprovalStatus(@Param("id") UUID id, @Param("status") String status);

    // Non-paginated exports
    List<Supplier> findByDistributorId(UUID distributorId);
    List<Supplier> findByDistributorMerchantId(UUID merchantId);
}
