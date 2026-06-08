package com.zuqi.repository;

import com.zuqi.domain.returns.CreditNote;
import com.zuqi.domain.returns.CreditNoteStatus;
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
public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    Optional<CreditNote> findByCreditNoteNumber(String creditNoteNumber);

    Optional<CreditNote> findBySalesReturnId(UUID salesReturnId);

    Page<CreditNote> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<CreditNote> findByDistributorMerchantId(UUID merchantId, Pageable pageable);

    Page<CreditNote> findByCustomerId(UUID customerId, Pageable pageable);

    List<CreditNote> findByCustomerIdAndStatusIn(UUID customerId, List<CreditNoteStatus> statuses);

    @Query("SELECT COALESCE(SUM(cn.remainingAmount), 0) FROM CreditNote cn " +
           "WHERE cn.customer.id = :customerId AND cn.status IN ('OPEN', 'PARTIALLY_APPLIED')")
    BigDecimal sumAvailableBalanceByCustomerId(@Param("customerId") UUID customerId);

    long countByDistributorId(UUID distributorId);

    long countByDistributorMerchantId(UUID merchantId);
}
