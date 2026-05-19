package com.zuqi.repository;

import com.zuqi.domain.ncba.NcbaStkRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NcbaStkRequestRepository extends JpaRepository<NcbaStkRequest, UUID> {

    Optional<NcbaStkRequest> findByTransactionId(String transactionId);

    Optional<NcbaStkRequest> findTopByReferenceIdOrderByCreatedAtDesc(String referenceId);
}
