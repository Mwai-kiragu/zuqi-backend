package com.zuqi.service;

import com.zuqi.api.dto.kcb.KcbActivateRequest;
import com.zuqi.api.dto.kcb.KcbConfigResponse;
import com.zuqi.api.dto.kcb.KcbStkPushRequest;
import com.zuqi.api.dto.kcb.KcbStkPushResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface KcbService {

    KcbConfigResponse activateConfig(UUID merchantId, KcbActivateRequest request);

    List<KcbConfigResponse> getConfigs(UUID merchantId);

    List<KcbConfigResponse> getAllConfigs();

    KcbConfigResponse deactivateConfig(UUID configId);

    KcbStkPushResponse initiateStk(KcbStkPushRequest request);

    KcbStkPushResponse initiatePublicStk(UUID merchantId, String phone, java.math.BigDecimal amount, String referenceId);

    KcbStkPushResponse getStkStatus(UUID stkRequestId);

    void handleCallback(Map<String, Object> payload);
}
