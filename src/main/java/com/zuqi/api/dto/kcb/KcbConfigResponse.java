package com.zuqi.api.dto.kcb;

import com.zuqi.domain.kcb.KcbConfig;
import com.zuqi.domain.kcb.KcbConfigStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record KcbConfigResponse(
        UUID id,
        UUID merchantId,
        String merchantName,
        String businessName,
        String accountNumber,
        String kcbAccountType,
        String businessNo,
        String accountType,
        boolean subscriptionAccount,
        String externalId,
        KcbConfigStatus status,
        String configuredByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static KcbConfigResponse fromEntity(KcbConfig c) {
        return new KcbConfigResponse(
                c.getId(),
                c.getMerchant().getId(),
                c.getMerchant().getName(),
                c.getBusinessName(),
                c.getAccountNumber(),
                c.getKcbAccountType(),
                c.getBusinessNo(),
                c.getAccountType(),
                c.isSubscriptionAccount(),
                c.getExternalId(),
                c.getStatus(),
                c.getConfiguredByName(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
