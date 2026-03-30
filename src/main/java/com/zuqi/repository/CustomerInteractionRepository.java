package com.zuqi.repository;

import com.zuqi.domain.crm.CustomerInteraction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CustomerInteractionRepository extends JpaRepository<CustomerInteraction, UUID> {

    Page<CustomerInteraction> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<CustomerInteraction> findByDistributorIdOrderByCreatedAtDesc(UUID distributorId, Pageable pageable);

    @Query("SELECT ci FROM CustomerInteraction ci WHERE ci.customer.id IN " +
           "(SELECT c.id FROM Customer c WHERE c.distributor.id IN " +
           "(SELECT d.id FROM Distributor d WHERE d.merchant.id = :merchantId)) " +
           "ORDER BY ci.createdAt DESC")
    Page<CustomerInteraction> findByMerchantIdOrderByCreatedAtDesc(@Param("merchantId") UUID merchantId, Pageable pageable);

    long countByCustomerId(UUID customerId);

    @Query("SELECT ci FROM CustomerInteraction ci WHERE ci.followUpDate IS NOT NULL " +
           "AND ci.followUpDone = false AND ci.distributorId = :distributorId " +
           "ORDER BY ci.followUpDate ASC")
    Page<CustomerInteraction> findPendingFollowUpsByDistributorId(@Param("distributorId") UUID distributorId, Pageable pageable);
}
