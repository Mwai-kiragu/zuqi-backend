package com.zuqi.api.dto.procurement;

import com.zuqi.domain.procurement.GoodsReceiptNote;
import com.zuqi.domain.procurement.GrnStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrnResponse {

    private UUID id;
    private String grnNumber;
    private UUID purchaseOrderId;
    private String poNumber;
    private UUID supplierId;
    private String supplierName;
    private UUID distributorId;
    private UUID warehouseId;
    private String warehouseName;
    private GrnStatus status;
    private String deliveryNoteNumber;
    private String notes;
    private List<Map<String, Object>> items;
    private BigDecimal totalAmount;
    private String confirmedByName;
    private LocalDateTime confirmedAt;
    private String rejectedByName;
    private LocalDateTime rejectedAt;
    private String rejectionReason;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GrnResponse fromEntity(GoodsReceiptNote grn) {
        return fromEntity(grn, null);
    }

    public static GrnResponse fromEntity(GoodsReceiptNote grn, String warehouseName) {
        return GrnResponse.builder()
                .id(grn.getId())
                .grnNumber(grn.getGrnNumber())
                .purchaseOrderId(grn.getPurchaseOrder() != null ? grn.getPurchaseOrder().getId() : null)
                .poNumber(grn.getPurchaseOrder() != null ? grn.getPurchaseOrder().getPoNumber() : null)
                .supplierId(grn.getSupplier() != null ? grn.getSupplier().getId() : null)
                .supplierName(grn.getSupplier() != null ? grn.getSupplier().getName() : null)
                .distributorId(grn.getDistributorId())
                .warehouseId(grn.getWarehouseId())
                .warehouseName(warehouseName)
                .status(grn.getStatus())
                .deliveryNoteNumber(grn.getDeliveryNoteNumber())
                .notes(grn.getNotes())
                .items(grn.getItems())
                .totalAmount(grn.getTotalAmount())
                .confirmedByName(grn.getConfirmedBy() != null ? grn.getConfirmedBy().getFullName() : null)
                .confirmedAt(grn.getConfirmedAt())
                .rejectedByName(grn.getRejectedBy() != null ? grn.getRejectedBy().getFullName() : null)
                .rejectedAt(grn.getRejectedAt())
                .rejectionReason(grn.getRejectionReason())
                .createdByName(grn.getCreatedBy() != null ? grn.getCreatedBy().getFullName() : null)
                .createdAt(grn.getCreatedAt())
                .updatedAt(grn.getUpdatedAt())
                .build();
    }
}
