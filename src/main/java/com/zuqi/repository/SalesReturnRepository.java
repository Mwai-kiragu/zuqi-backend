package com.zuqi.repository;

import com.zuqi.domain.returns.SalesReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SalesReturnRepository extends JpaRepository<SalesReturn, UUID> {

    Page<SalesReturn> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<SalesReturn> findByDistributorMerchantId(UUID merchantId, Pageable pageable);

    boolean existsByReturnNumber(String returnNumber);
}
