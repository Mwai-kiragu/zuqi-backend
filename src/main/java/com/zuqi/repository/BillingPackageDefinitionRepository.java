package com.zuqi.repository;

import com.zuqi.domain.billing.BillingPackageDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingPackageDefinitionRepository extends JpaRepository<BillingPackageDefinition, UUID> {

    List<BillingPackageDefinition> findAllByActiveTrueOrderBySortOrderAsc();

    Optional<BillingPackageDefinition> findByName(String name);
}
