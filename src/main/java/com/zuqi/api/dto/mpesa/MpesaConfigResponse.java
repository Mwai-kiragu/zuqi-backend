package com.zuqi.api.dto.mpesa;

import com.zuqi.domain.mpesa.MpesaConfig;
import com.zuqi.domain.mpesa.MpesaConfigStatus;
import com.zuqi.domain.mpesa.MpesaTransactionType;

import java.time.LocalDateTime;
import java.util.UUID;

public record MpesaConfigResponse(
        UUID id,
        UUID merchantId,
        String merchantName,
        String businessName,
        MpesaTransactionType transactionType,
        String businessShortCode,
        String tillNumber,
        String storeNumber,
        String hoNumber,
        String businessNo,
        String accountReference,
        String thirdPartyCallback,
        String externalId,
        MpesaConfigStatus status,
        boolean termsAccepted,
        UUID configuredById,
        String configuredByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MpesaConfigResponse fromEntity(MpesaConfig c) {
        return new MpesaConfigResponse(
                c.getId(),
                c.getMerchant().getId(),
                c.getMerchant().getName(),
                c.getBusinessName(),
                c.getTransactionType(),
                c.getBusinessShortCode(),
                c.getTillNumber(),
                c.getStoreNumber(),
                c.getHoNumber(),
                c.getBusinessNo(),
                c.getAccountReference(),
                c.getThirdPartyCallback(),
                c.getExternalId(),
                c.getStatus(),
                c.isTermsAccepted(),
                c.getConfiguredBy() != null ? c.getConfiguredBy().getId() : null,
                c.getConfiguredByName(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
