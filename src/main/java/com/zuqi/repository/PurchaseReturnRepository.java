package com.zuqi.repository;

import com.zuqi.domain.returns.PurchaseReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, UUID> {

    Page<PurchaseReturn> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<PurchaseReturn> findByDistributorMerchantId(UUID merchantId, Pageable pageable);

    boolean existsByReturnNumber(String returnNumber);
}
