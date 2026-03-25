package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.pos.*;
import com.zuqi.service.PosService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/pos")
@RequiredArgsConstructor
@Tag(name = "Point of Sale", description = "POS management endpoints")
public class PosController {

    private final PosService posService;
    private final SecurityUtils securityUtils;

    // ---- Terminals ----

    @PostMapping("/terminals")
    @Operation(summary = "Create a POS terminal")
    public ResponseEntity<ApiResponse<PosTerminalResponse>> createTerminal(
            @Valid @RequestBody PosTerminalRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        PosTerminalResponse response = posService.createTerminal(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Terminal created", response));
    }

    @GetMapping("/terminals")
    @Operation(summary = "List terminals by branch")
    public ResponseEntity<ApiResponse<List<PosTerminalResponse>>> getTerminals(
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(ApiResponse.success(posService.getTerminalsByBranch(branchId)));
    }

    // ---- Shifts ----

    @PostMapping("/shifts/open")
    @Operation(summary = "Open a cashier shift")
    public ResponseEntity<ApiResponse<PosShiftResponse>> openShift(
            @Valid @RequestBody OpenShiftRequest request) {
        UUID cashierId = securityUtils.getCurrentUserId();
        PosShiftResponse response = posService.openShift(request, cashierId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Shift opened", response));
    }

    @PostMapping("/shifts/{shiftId}/close")
    @Operation(summary = "Close a cashier shift")
    public ResponseEntity<ApiResponse<PosShiftResponse>> closeShift(
            @PathVariable UUID shiftId, @RequestBody CloseShiftRequest request) {
        UUID cashierId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Shift closed", posService.closeShift(shiftId, request, cashierId)));
    }

    @PostMapping("/shifts/{shiftId}/reconcile")
    @Operation(summary = "Reconcile a closed shift (supervisor only)", description = "Approves reconciliation for a closed shift. Caller must not be the shift's cashier.")
    public ResponseEntity<ApiResponse<PosShiftResponse>> reconcileShift(@PathVariable UUID shiftId) {
        UUID supervisorId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Shift reconciled", posService.reconcileShift(shiftId, supervisorId)));
    }

    @GetMapping("/shifts/{shiftId}/reconciliation")
    @Operation(summary = "Get shift reconciliation preview (payment breakdown before closing)")
    public ResponseEntity<ApiResponse<ShiftReconciliationResponse>> getShiftReconciliation(
            @PathVariable UUID shiftId) {
        return ResponseEntity.ok(ApiResponse.success(posService.getShiftReconciliation(shiftId)));
    }

    @GetMapping("/shifts/current")
    @Operation(summary = "Get current open shift for the cashier")
    public ResponseEntity<ApiResponse<PosShiftResponse>> getCurrentShift(@RequestParam UUID branchId) {
        UUID cashierId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(posService.getCurrentShift(branchId, cashierId)));
    }

    // ---- Sales ----

    @PostMapping("/sales")
    @Operation(summary = "Create a new sale (DRAFT)")
    public ResponseEntity<ApiResponse<PosSaleResponse>> createSale(
            @Valid @RequestBody CreateSaleRequest request) {
        UUID cashierId = securityUtils.getCurrentUserId();
        PosSaleResponse response = posService.createSale(request, cashierId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Sale created", response));
    }

    @PutMapping("/sales/{saleId}/items")
    @Operation(summary = "Update items in a sale")
    public ResponseEntity<ApiResponse<PosSaleResponse>> updateSaleItems(
            @PathVariable UUID saleId, @Valid @RequestBody UpdateSaleItemsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Items updated", posService.updateSaleItems(saleId, request)));
    }

    @PostMapping("/sales/{saleId}/payment")
    @Operation(summary = "Add a payment to a sale")
    public ResponseEntity<ApiResponse<PosSaleResponse>> addPayment(
            @PathVariable UUID saleId, @Valid @RequestBody ProcessPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment added", posService.addPayment(saleId, request)));
    }

    @PostMapping("/sales/{saleId}/complete")
    @Operation(summary = "Complete a sale")
    public ResponseEntity<ApiResponse<PosSaleResponse>> completeSale(
            @PathVariable UUID saleId,
            @RequestParam(required = false) UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success("Sale completed", posService.completeSale(saleId, warehouseId)));
    }

    @PostMapping("/sales/{saleId}/cancel")
    @Operation(summary = "Cancel a sale")
    public ResponseEntity<ApiResponse<PosSaleResponse>> cancelSale(
            @PathVariable UUID saleId,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success("Sale cancelled", posService.cancelSale(saleId, reason)));
    }

    @PostMapping("/sales/{saleId}/refund")
    @Operation(summary = "Refund a completed sale")
    public ResponseEntity<ApiResponse<PosSaleResponse>> refundSale(@PathVariable UUID saleId) {
        UUID cashierId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Sale refunded", posService.refundSale(saleId, cashierId)));
    }

    @GetMapping("/sales")
    @Operation(summary = "List sales (filter by branchId, status, date range)")
    public ResponseEntity<ApiResponse<Page<PosSaleResponse>>> getSales(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(posService.getSales(branchId, status, startDate, endDate, pageable)));
    }

    @GetMapping("/sales/{saleId}")
    @Operation(summary = "Get sale details")
    public ResponseEntity<ApiResponse<PosSaleResponse>> getSale(@PathVariable UUID saleId) {
        return ResponseEntity.ok(ApiResponse.success(posService.getSaleById(saleId)));
    }

    // ---- Reports ----

    @GetMapping("/reports/summary")
    @Operation(summary = "Sales summary by branch and date range")
    public ResponseEntity<ApiResponse<PosSummaryResponse>> getDailySummary(
            @RequestParam UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate from = startDate != null ? startDate : LocalDate.now();
        LocalDate to   = endDate   != null ? endDate   : from;
        return ResponseEntity.ok(ApiResponse.success(posService.getDailySummary(branchId, from, to)));
    }
}
