package com.zuqi.repository;

import com.zuqi.domain.gl.CostCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CostCenterRepository extends JpaRepository<CostCenter, UUID> {

    List<CostCenter> findByDistributorIdOrderByCodeAsc(UUID distributorId);

    List<CostCenter> findByDistributorIdAndActiveOrderByCodeAsc(UUID distributorId, boolean active);

    boolean existsByDistributorIdAndCode(UUID distributorId, String code);

    Optional<CostCenter> findByDistributorIdAndCode(UUID distributorId, String code);
}
