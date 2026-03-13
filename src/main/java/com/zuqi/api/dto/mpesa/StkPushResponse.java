package com.zuqi.api.dto.mpesa;

import java.util.UUID;

public record StkPushResponse(
        UUID stkRequestId,
        String checkoutRequestId,
        String merchantRequestId,
        String referenceId,
        String referenceType,
        String status,
        String message
) {}
