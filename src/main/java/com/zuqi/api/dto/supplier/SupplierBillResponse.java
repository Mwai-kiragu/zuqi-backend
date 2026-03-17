package com.zuqi.api.dto.supplier;

import com.zuqi.domain.supplier.SupplierBill;
import com.zuqi.domain.supplier.SupplierBillStatus;
import com.zuqi.domain.supplier.SupplierBillType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class SupplierBillResponse {

    private UUID id;
    private String billNumber;
    private UUID distributorId;
    private String distributorName;
    private UUID supplierId;
    private String supplierName;
    private String supplierCode;
    private UUID purchaseOrderId;
    private String poNumber;
    private String referenceNumber;
    private LocalDate billDate;
    private LocalDate dueDate;
    private SupplierBillType billType;
    private String description;
    private List<Map<String, Object>> items;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
    private SupplierBillStatus status;
    private boolean glPosted;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SupplierBillResponse from(SupplierBill bill) {
        BigDecimal paid = bill.getPaidAmount() != null ? bill.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal outstanding = bill.getTotalAmount().subtract(paid);

        return SupplierBillResponse.builder()
                .id(bill.getId())
                .billNumber(bill.getBillNumber())
                .distributorId(bill.getDistributor() != null ? bill.getDistributor().getId() : null)
                .distributorName(bill.getDistributor() != null ? bill.getDistributor().getName() : null)
                .supplierId(bill.getSupplier() != null ? bill.getSupplier().getId() : null)
                .supplierName(bill.getSupplier() != null ? bill.getSupplier().getName() : null)
                .supplierCode(bill.getSupplier() != null ? bill.getSupplier().getSupplierCode() : null)
                .purchaseOrderId(bill.getPurchaseOrder() != null ? bill.getPurchaseOrder().getId() : null)
                .poNumber(bill.getPurchaseOrder() != null ? bill.getPurchaseOrder().getPoNumber() : null)
                .referenceNumber(bill.getReferenceNumber())
                .billDate(bill.getBillDate())
                .dueDate(bill.getDueDate())
                .billType(bill.getBillType())
                .description(bill.getDescription())
                .items(bill.getItems())
                .totalAmount(bill.getTotalAmount())
                .paidAmount(paid)
                .outstandingAmount(outstanding.max(BigDecimal.ZERO))
                .status(bill.getStatus())
                .glPosted(bill.isGlPosted())
                .notes(bill.getNotes())
                .createdAt(bill.getCreatedAt())
                .updatedAt(bill.getUpdatedAt())
                .build();
    }
}
