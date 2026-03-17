package com.zuqi.service;

import com.zuqi.api.dto.supplier.SupplierBillRequest;
import com.zuqi.api.dto.supplier.SupplierBillResponse;
import com.zuqi.domain.supplier.SupplierBillStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SupplierBillService {

    Page<SupplierBillResponse> getAllBills(SupplierBillStatus status, Pageable pageable);

    SupplierBillResponse getBillById(UUID id);

    SupplierBillResponse createBill(SupplierBillRequest request);

    SupplierBillResponse updateBill(UUID id, SupplierBillRequest request);

    SupplierBillResponse receiveBill(UUID id);

    SupplierBillResponse cancelBill(UUID id);

    Page<SupplierBillResponse> getSupplierBills(UUID supplierId, Pageable pageable);

    List<SupplierBillResponse> getOutstandingBillsForSupplier(UUID supplierId);

    /** Called by FundsTransferServiceImpl on disburse to apply payment to the linked bill */
    void applyPayment(UUID billId, BigDecimal amount);
}
