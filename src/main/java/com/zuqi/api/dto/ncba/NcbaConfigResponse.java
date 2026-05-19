package com.zuqi.api.dto.ncba;

import com.zuqi.domain.ncba.NcbaConfig;
import com.zuqi.domain.ncba.NcbaConfigStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record NcbaConfigResponse(
        UUID id,
        UUID merchantId,
        String merchantName,
        String businessName,
        String paybillNo,
        String network,
        String lookupId,
        NcbaConfigStatus status,
        String configuredByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NcbaConfigResponse fromEntity(NcbaConfig c) {
        return new NcbaConfigResponse(
                c.getId(),
                c.getMerchant().getId(),
                c.getMerchant().getName(),
                c.getBusinessName(),
                c.getPaybillNo(),
                c.getNetwork(),
                c.getLookupId(),
                c.getStatus(),
                c.getConfiguredByName(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
