package com.zuqi.repository;

import com.zuqi.domain.pricing.PriceList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceListRepository extends JpaRepository<PriceList, UUID> {

    Page<PriceList> findByDistributorId(UUID distributorId, Pageable pageable);

    @Query("SELECT pl FROM PriceList pl WHERE pl.distributor.merchant.id = :merchantId")
    Page<PriceList> findByDistributorMerchantId(@Param("merchantId") UUID merchantId, Pageable pageable);

    Optional<PriceList> findByDistributorIdAndIsDefaultTrue(UUID distributorId);

    @Modifying
    @Query("UPDATE PriceList pl SET pl.approvalStatus = :status WHERE pl.id = :id")
    void updateApprovalStatus(@Param("id") UUID id, @Param("status") String status);
}
