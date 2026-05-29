package com.zuqi.repository;

import com.zuqi.domain.kcb.KcbStkRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KcbStkRequestRepository extends JpaRepository<KcbStkRequest, UUID> {

    Optional<KcbStkRequest> findByZedStkId(String zedStkId);

    Optional<KcbStkRequest> findTopByRequestReferenceIdOrderByCreatedAtDesc(String requestReferenceId);

    Optional<KcbStkRequest> findTopByReferenceIdOrderByCreatedAtDesc(String referenceId);
}
