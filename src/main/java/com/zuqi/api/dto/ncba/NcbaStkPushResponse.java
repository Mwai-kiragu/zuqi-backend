package com.zuqi.api.dto.ncba;

import java.util.UUID;

public record NcbaStkPushResponse(
        UUID stkRequestId,
        String transactionId,
        String lookupId,
        String referenceId,
        String referenceType,
        String status,
        String message
) {}
