package com.zuqi.repository;

import com.zuqi.domain.mpesa.MpesaStkRequest;
import com.zuqi.domain.mpesa.MpesaStkStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MpesaStkRequestRepository extends JpaRepository<MpesaStkRequest, UUID> {

    Optional<MpesaStkRequest> findByCheckoutRequestId(String checkoutRequestId);

    Optional<MpesaStkRequest> findTopByReferenceIdOrderByCreatedAtDesc(String referenceId);

    Optional<MpesaStkRequest> findByReferenceIdAndStatus(String referenceId, MpesaStkStatus status);
}
