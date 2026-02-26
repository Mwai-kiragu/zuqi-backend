package com.zuqi.repository;

import com.zuqi.domain.billing.BillingModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BillingModuleRepository extends JpaRepository<BillingModule, UUID> {

    List<BillingModule> findAllByActiveTrueOrderBySortOrderAsc();
}
