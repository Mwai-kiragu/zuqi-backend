package com.zuqi.repository;

import com.zuqi.domain.gl.GlAccount;
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

    Optional<GlAccount> findByDistributorIdAndAccountCode(UUID distributorId, String accountCode);

    List<GlAccount> findByDistributorIdAndActiveOrderByAccountCodeAsc(UUID distributorId, boolean active);
}
