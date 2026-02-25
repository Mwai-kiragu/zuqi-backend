package com.zuqi.service;

import com.zuqi.api.dto.procurement.PurchaseOrderRequest;
import com.zuqi.api.dto.procurement.PurchaseOrderResponse;
import com.zuqi.api.dto.procurement.PurchaseRequisitionRequest;
import com.zuqi.api.dto.procurement.PurchaseRequisitionResponse;
import com.zuqi.domain.procurement.PoStatus;
import com.zuqi.domain.procurement.PrStatus;
import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProcurementService {

    // Purchase Requisitions
    Page<PurchaseRequisitionResponse> getPurchaseRequisitions(UUID distributorId, PrStatus status, UUID requestedById, Pageable pageable);

    PurchaseRequisitionResponse getPurchaseRequisitionById(UUID id);

    PurchaseRequisitionResponse createPurchaseRequisition(PurchaseRequisitionRequest request, User currentUser);

    PurchaseRequisitionResponse submitPurchaseRequisition(UUID id, User currentUser);

    PurchaseRequisitionResponse approvePurchaseRequisition(UUID id, User currentUser);

    PurchaseRequisitionResponse rejectPurchaseRequisition(UUID id, String reason, User currentUser);

    PurchaseRequisitionResponse cancelPurchaseRequisition(UUID id, User currentUser);

    // Purchase Orders
    Page<PurchaseOrderResponse> getPurchaseOrders(UUID distributorId, PoStatus status, UUID supplierId, Pageable pageable);

    PurchaseOrderResponse getPurchaseOrderById(UUID id);

    PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request, User currentUser);

    PurchaseOrderResponse sendPurchaseOrder(UUID id, User currentUser);

    PurchaseOrderResponse confirmPurchaseOrder(UUID id, User currentUser);

    PurchaseOrderResponse cancelPurchaseOrder(UUID id, User currentUser);

    PurchaseOrderResponse convertPrToPo(UUID prId, PurchaseOrderRequest request, User currentUser);
}
