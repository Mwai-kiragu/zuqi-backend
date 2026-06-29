package com.zuqi.service;

import com.zuqi.api.dto.mpesa.*;
import com.zuqi.domain.ft.FundsTransfer;

import java.util.List;
import java.util.Map;
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

    /**
     * Initiates an M-Pesa B2C payment (business pays recipient phone).
     * The funds transfer's creditAccountNumber is used as the recipient phone.
     * Returns the gateway ConversationID / OriginatorConversationID to store on the transfer.
     */
    String initiateB2c(FundsTransfer ft);

    /** Receives the async B2C result callback from the Daraja gateway. */
    void handleB2cCallback(Map<String, Object> payload);
}
