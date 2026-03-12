package com.zuqi.repository;

import com.zuqi.domain.mpesa.MpesaConfig;
import com.zuqi.domain.mpesa.MpesaConfigStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MpesaConfigRepository extends JpaRepository<MpesaConfig, UUID> {

    List<MpesaConfig> findByMerchantId(UUID merchantId);

    Optional<MpesaConfig> findByExternalId(String externalId);

    List<MpesaConfig> findByMerchantIdAndStatus(UUID merchantId, MpesaConfigStatus status);

    List<MpesaConfig> findByMerchantIdAndTransactionTypeAndStatus(UUID merchantId, com.zuqi.domain.mpesa.MpesaTransactionType transactionType, MpesaConfigStatus status);

    boolean existsByMerchantIdAndBusinessShortCodeAndStatus(UUID merchantId, String businessShortCode, MpesaConfigStatus status);
}
