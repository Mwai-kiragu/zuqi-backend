package com.zuqi.service;

import com.zuqi.api.dto.mpesa.*;

import java.util.List;
import java.util.UUID;

public interface MpesaService {

    MpesaConfigResponse activateConfig(UUID merchantId, MpesaActivateRequest request);

    List<MpesaConfigResponse> getConfigs(UUID merchantId);

    List<MpesaConfigResponse> getAllConfigs();

    MpesaConfigResponse deactivateConfig(UUID configId);

    StkPushResponse initiateStk(StkPushRequest request);

    void handleStkCallback(StkCallbackPayload payload);

    StkPushResponse getStkStatus(UUID stkRequestId);

    boolean getCashEnabled(UUID merchantId);

    boolean setCashEnabled(UUID merchantId, boolean enabled);
}
