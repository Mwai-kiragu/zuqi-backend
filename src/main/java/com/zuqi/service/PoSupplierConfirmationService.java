package com.zuqi.service;

import com.zuqi.api.dto.procurement.PoConfirmationDetailsResponse;
import com.zuqi.domain.procurement.PurchaseOrder;

import java.util.Map;

public interface PoSupplierConfirmationService {

    /** Generate CONFIRM / DECLINE / PARTIAL tokens for a PO. Returns map action → token string. */
    Map<String, String> generateTokensForPo(PurchaseOrder po);

    /** Return PO details visible to the supplier, including token status. */
    PoConfirmationDetailsResponse getTokenDetails(String token);

    /** Record the supplier's response. notes is required only for PARTIAL action. */
    void processResponse(String token, String notes);
}
