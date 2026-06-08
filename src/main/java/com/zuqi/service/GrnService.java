package com.zuqi.service;

import com.zuqi.api.dto.procurement.GrnRequest;
import com.zuqi.api.dto.procurement.GrnResponse;
import com.zuqi.domain.procurement.GrnStatus;
import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GrnService {

    Page<GrnResponse> getGrns(UUID distributorId, GrnStatus status, UUID supplierId, UUID purchaseOrderId, Pageable pageable);

    GrnResponse getGrnById(String id);

    GrnResponse createGrn(GrnRequest request, User currentUser);

    GrnResponse confirmGrn(String id, User currentUser);

    GrnResponse rejectGrn(String id, String reason, User currentUser);

    GrnResponse updateDeliveryNote(String id, String deliveryNoteNumber, User currentUser);
}
