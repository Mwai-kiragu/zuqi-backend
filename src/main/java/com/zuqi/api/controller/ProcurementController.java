package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.procurement.PurchaseOrderRequest;
import com.zuqi.api.dto.procurement.PurchaseOrderResponse;
import com.zuqi.api.dto.procurement.PurchaseRequisitionRequest;
import com.zuqi.api.dto.procurement.PurchaseRequisitionResponse;
import com.zuqi.domain.procurement.PoStatus;
import com.zuqi.domain.procurement.PrStatus;
import com.zuqi.domain.user.User;
import com.zuqi.service.ProcurementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Procurement", description = "Purchase Requisition and Purchase Order management APIs")
public class ProcurementController {

    private final ProcurementService procurementService;

    // ===================== PURCHASE REQUISITIONS =====================

    @GetMapping("/v1/purchase-requisitions")
    @Operation(summary = "Get all purchase requisitions")
    public ResponseEntity<ApiResponse<Page<PurchaseRequisitionResponse>>> getPurchaseRequisitions(
            @Parameter(description = "Distributor ID filter") @RequestParam(required = false) UUID distributorId,
            @Parameter(description = "Status filter") @RequestParam(required = false) PrStatus status,
            @Parameter(description = "Requested-by user ID filter") @RequestParam(required = false) UUID requestedById,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.getPurchaseRequisitions(distributorId, status, requestedById, pageable)));
    }

    @GetMapping("/v1/purchase-requisitions/{id}")
    @Operation(summary = "Get purchase requisition by ID")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> getPurchaseRequisitionById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getPurchaseRequisitionById(id)));
    }

    @PostMapping("/v1/purchase-requisitions")
    @Operation(summary = "Create purchase requisition")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> createPurchaseRequisition(
            @Valid @RequestBody PurchaseRequisitionRequest request,
            @AuthenticationPrincipal User currentUser) {
        PurchaseRequisitionResponse pr = procurementService.createPurchaseRequisition(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Purchase requisition created successfully", pr));
    }

    @PostMapping("/v1/purchase-requisitions/{id}/submit")
    @Operation(summary = "Submit purchase requisition for approval")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> submitPurchaseRequisition(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Requisition submitted for approval",
                procurementService.submitPurchaseRequisition(id, currentUser)));
    }

    @PostMapping("/v1/purchase-requisitions/{id}/approve")
    @Operation(summary = "Approve purchase requisition")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> approvePurchaseRequisition(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Requisition approved",
                procurementService.approvePurchaseRequisition(id, currentUser)));
    }

    @PostMapping("/v1/purchase-requisitions/{id}/reject")
    @Operation(summary = "Reject purchase requisition")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> rejectPurchaseRequisition(
            @PathVariable UUID id,
            @RequestBody RejectBody body,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Requisition rejected",
                procurementService.rejectPurchaseRequisition(id, body.reason(), currentUser)));
    }

    @PutMapping("/v1/purchase-requisitions/{id}")
    @Operation(summary = "Update a DRAFT purchase requisition")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> updatePurchaseRequisition(
            @PathVariable UUID id,
            @Valid @RequestBody PurchaseRequisitionRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Requisition updated",
                procurementService.updatePurchaseRequisition(id, request, currentUser)));
    }

    @PostMapping("/v1/purchase-requisitions/{id}/resubmit")
    @Operation(summary = "Reset a rejected requisition to draft for revision and resubmission")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> resubmitPurchaseRequisition(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Requisition reset to draft for revision",
                procurementService.resubmitPurchaseRequisition(id, currentUser)));
    }

    @PostMapping("/v1/purchase-requisitions/{id}/cancel")
    @Operation(summary = "Cancel purchase requisition")
    public ResponseEntity<ApiResponse<PurchaseRequisitionResponse>> cancelPurchaseRequisition(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Requisition cancelled",
                procurementService.cancelPurchaseRequisition(id, currentUser)));
    }

    @PostMapping("/v1/purchase-requisitions/{id}/convert-to-po")
    @Operation(summary = "Convert approved requisition to purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> convertToPo(
            @PathVariable UUID id,
            @Valid @RequestBody PurchaseOrderRequest request,
            @AuthenticationPrincipal User currentUser) {
        PurchaseOrderResponse po = procurementService.convertPrToPo(id, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Purchase order created from requisition", po));
    }

    // ===================== PURCHASE ORDERS =====================

    @GetMapping("/v1/purchase-orders")
    @Operation(summary = "Get all purchase orders")
    public ResponseEntity<ApiResponse<Page<PurchaseOrderResponse>>> getPurchaseOrders(
            @Parameter(description = "Distributor ID filter") @RequestParam(required = false) UUID distributorId,
            @Parameter(description = "Status filter") @RequestParam(required = false) PoStatus status,
            @Parameter(description = "Supplier ID filter") @RequestParam(required = false) UUID supplierId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                procurementService.getPurchaseOrders(distributorId, status, supplierId, pageable)));
    }

    @GetMapping("/v1/purchase-orders/{id}")
    @Operation(summary = "Get purchase order by ID")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getPurchaseOrderById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(procurementService.getPurchaseOrderById(id)));
    }

    @PostMapping("/v1/purchase-orders")
    @Operation(summary = "Create purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createPurchaseOrder(
            @Valid @RequestBody PurchaseOrderRequest request,
            @AuthenticationPrincipal User currentUser) {
        PurchaseOrderResponse po = procurementService.createPurchaseOrder(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Purchase order created successfully", po));
    }

    @PostMapping("/v1/purchase-orders/{id}/send")
    @Operation(summary = "Send purchase order to supplier")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> sendPurchaseOrder(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Purchase order sent to supplier",
                procurementService.sendPurchaseOrder(id, currentUser)));
    }

    @PostMapping("/v1/purchase-orders/{id}/confirm")
    @Operation(summary = "Confirm purchase order (supplier acknowledged)")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> confirmPurchaseOrder(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Purchase order confirmed",
                procurementService.confirmPurchaseOrder(id, currentUser)));
    }

    @PostMapping("/v1/purchase-orders/{id}/cancel")
    @Operation(summary = "Cancel purchase order")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancelPurchaseOrder(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Purchase order cancelled",
                procurementService.cancelPurchaseOrder(id, currentUser)));
    }

    // Inner record for reject request body
    record RejectBody(String reason) {}
}
