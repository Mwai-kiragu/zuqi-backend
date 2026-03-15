package com.zuqi.repository;

import com.zuqi.domain.gl.GlAccount;
import com.zuqi.domain.gl.SystemAccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GlAccountRepository extends JpaRepository<GlAccount, UUID> {

    List<GlAccount> findByDistributorIdOrderByAccountCodeAsc(UUID distributorId);

    List<GlAccount> findByDistributorIdAndParentIdIsNullOrderByAccountCodeAsc(UUID distributorId);

    List<GlAccount> findByDistributorIdAndParentIdOrderByAccountCodeAsc(UUID distributorId, UUID parentId);

    boolean existsByDistributorIdAndAccountCode(UUID distributorId, String accountCode);

    boolean existsByDistributorId(UUID distributorId);

    Optional<GlAccount> findByDistributorIdAndAccountCode(UUID distributorId, String accountCode);

    List<GlAccount> findByDistributorIdAndActiveOrderByAccountCodeAsc(UUID distributorId, boolean active);

    Optional<GlAccount> findByDistributorIdAndSystemAccountType(UUID distributorId, SystemAccountType systemAccountType);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM GlAccount a JOIN Distributor d ON a.distributorId = d.id WHERE d.merchant.id = :merchantId ORDER BY a.accountCode ASC")
    List<GlAccount> findByDistributorMerchantIdOrderByAccountCodeAsc(@org.springframework.data.repository.query.Param("merchantId") UUID merchantId);
}
