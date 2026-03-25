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

    GrnResponse getGrnById(UUID id);

    GrnResponse createGrn(GrnRequest request, User currentUser);

    GrnResponse confirmGrn(UUID id, User currentUser);

    GrnResponse rejectGrn(UUID id, String reason, User currentUser);
}
