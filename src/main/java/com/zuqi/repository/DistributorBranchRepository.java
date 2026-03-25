package com.zuqi.repository;

import com.zuqi.domain.branch.BranchStatus;
import com.zuqi.domain.branch.DistributorBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DistributorBranchRepository extends JpaRepository<DistributorBranch, UUID> {

    List<DistributorBranch> findByDistributorId(UUID distributorId);

    List<DistributorBranch> findByDistributorIdAndStatus(UUID distributorId, BranchStatus status);

    Optional<DistributorBranch> findByIdAndDistributorId(UUID id, UUID distributorId);

    boolean existsByCodeAndDistributorId(String code, UUID distributorId);

    Optional<DistributorBranch> findFirstByDistributorIdAndHeadquartersTrue(UUID distributorId);
}
