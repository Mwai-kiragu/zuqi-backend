package com.zuqi.repository;

import com.zuqi.domain.branch.BranchUser;
import com.zuqi.domain.branch.BranchUserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchUserRepository extends JpaRepository<BranchUser, UUID> {

    List<BranchUser> findByBranchId(UUID branchId);

    List<BranchUser> findByUserId(UUID userId);

    List<BranchUser> findByBranchIdAndStatus(UUID branchId, BranchUserStatus status);

    Optional<BranchUser> findByBranchIdAndUserId(UUID branchId, UUID userId);

    boolean existsByBranchIdAndUserId(UUID branchId, UUID userId);

    List<BranchUser> findByUserIdAndStatus(UUID userId, BranchUserStatus status);
}
