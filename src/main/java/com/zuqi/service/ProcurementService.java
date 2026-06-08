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

    PurchaseRequisitionResponse getPurchaseRequisitionById(String id);

    PurchaseRequisitionResponse createPurchaseRequisition(PurchaseRequisitionRequest request, User currentUser);

    PurchaseRequisitionResponse submitPurchaseRequisition(String id, User currentUser);

    PurchaseRequisitionResponse approvePurchaseRequisition(String id, User currentUser);

    PurchaseRequisitionResponse rejectPurchaseRequisition(String id, String reason, User currentUser);

    PurchaseRequisitionResponse updatePurchaseRequisition(String id, PurchaseRequisitionRequest request, User currentUser);

    PurchaseRequisitionResponse resubmitPurchaseRequisition(String id, User currentUser);

    PurchaseRequisitionResponse cancelPurchaseRequisition(String id, User currentUser);

    // Purchase Orders
    Page<PurchaseOrderResponse> getPurchaseOrders(UUID distributorId, PoStatus status, UUID supplierId, Pageable pageable);

    PurchaseOrderResponse getPurchaseOrderById(String id);

    PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request, User currentUser);

    PurchaseOrderResponse sendPurchaseOrder(String id, User currentUser);

    PurchaseOrderResponse confirmPurchaseOrder(String id, User currentUser);

    PurchaseOrderResponse cancelPurchaseOrder(String id, User currentUser);

    PurchaseOrderResponse convertPrToPo(String prId, PurchaseOrderRequest request, User currentUser);
}
