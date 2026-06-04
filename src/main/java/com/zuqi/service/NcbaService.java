package com.zuqi.service;

import com.zuqi.api.dto.ncba.NcbaActivateRequest;
import com.zuqi.api.dto.ncba.NcbaConfigResponse;
import com.zuqi.api.dto.ncba.NcbaStkPushRequest;
import com.zuqi.api.dto.ncba.NcbaStkPushResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NcbaService {

    NcbaConfigResponse activateConfig(UUID merchantId, NcbaActivateRequest request);

    List<NcbaConfigResponse> getConfigs(UUID merchantId);

    List<NcbaConfigResponse> getAllConfigs();

    NcbaConfigResponse deactivateConfig(UUID configId);

    NcbaStkPushResponse initiateStk(NcbaStkPushRequest request);

    NcbaStkPushResponse initiatePublicStk(UUID merchantId, String phone, java.math.BigDecimal amount, String referenceId);

    NcbaStkPushResponse getStkStatus(UUID stkRequestId);

    void handleCallback(Map<String, Object> payload);
}
