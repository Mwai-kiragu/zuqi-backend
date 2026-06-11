package com.zuqi.service;

import com.zuqi.api.dto.pos.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PosService {

    PosTerminalResponse createTerminal(PosTerminalRequest request, UUID createdByUserId);

    List<PosTerminalResponse> getTerminalsByBranch(UUID branchId);

    PosShiftResponse openShift(OpenShiftRequest request, UUID cashierId);

    PosShiftResponse closeShift(UUID shiftId, CloseShiftRequest request, UUID cashierId);

    PosShiftResponse reconcileShift(UUID shiftId, UUID supervisorId);

    PosShiftResponse getCurrentShift(UUID branchId, UUID cashierId);

    ShiftReconciliationResponse getShiftReconciliation(UUID shiftId);

    PosSaleResponse createSale(CreateSaleRequest request, UUID cashierId);

    PosSaleResponse updateSaleItems(UUID saleId, UpdateSaleItemsRequest request);

    PosSaleResponse addPayment(UUID saleId, ProcessPaymentRequest request);

    /** Add a top-up payment to a COMPLETED but partially-paid sale. */
    PosSaleResponse settleBalance(UUID saleId, ProcessPaymentRequest request);

    PosSaleResponse completeSale(UUID saleId, UUID warehouseId);

    PosSaleResponse cancelSale(UUID saleId, String reason);

    PosSaleResponse refundSale(UUID saleId, UUID cashierId);

    PosSaleResponse partialRefundSale(UUID saleId, com.zuqi.api.dto.pos.PartialRefundRequest request, UUID cashierId);

    /** Called by ApprovalServiceImpl when a POS_REFUND approval is approved. */
    PosSaleResponse executeApprovedRefund(UUID saleId, java.util.Map<String, Object> requestedValues, UUID cashierId);

    Page<PosSaleResponse> getSales(UUID branchId, String status, LocalDate startDate, LocalDate endDate, Pageable pageable);

    PosSaleResponse getSaleById(UUID saleId);

    PosSummaryResponse getDailySummary(UUID branchId, LocalDate startDate, LocalDate endDate);
}
