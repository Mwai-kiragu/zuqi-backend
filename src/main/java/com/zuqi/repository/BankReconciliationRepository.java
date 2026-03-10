package com.zuqi.repository;

import com.zuqi.domain.accounting.BankReconciliation;
import com.zuqi.domain.accounting.BankReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BankReconciliationRepository extends JpaRepository<BankReconciliation, UUID> {
    Page<BankReconciliation> findByDistributorIdOrderByStatementDateDesc(UUID distributorId, Pageable pageable);
    Page<BankReconciliation> findByDistributorIdAndStatusOrderByStatementDateDesc(
            UUID distributorId, BankReconciliationStatus status, Pageable pageable);
}
