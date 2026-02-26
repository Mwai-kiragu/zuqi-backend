package com.zuqi.repository;

import com.zuqi.domain.billing.DistributorSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DistributorSubscriptionRepository extends JpaRepository<DistributorSubscription, UUID> {

    Optional<DistributorSubscription> findByDistributorId(UUID distributorId);

    List<DistributorSubscription> findAllByActiveTrueOrderByCreatedAtDesc();
}
