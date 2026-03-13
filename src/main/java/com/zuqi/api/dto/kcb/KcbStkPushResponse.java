package com.zuqi.api.dto.kcb;

import java.util.UUID;

public record KcbStkPushResponse(
        UUID stkRequestId,
        String zedStkId,
        String stkOrderId,
        String requestReferenceId,
        String referenceId,
        String referenceType,
        String status,
        String message
) {}
