package com.zuqi.api.dto.procurement;

import com.zuqi.domain.procurement.PoStatus;
import com.zuqi.domain.procurement.PurchaseOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderResponse {

    private UUID id;
    private String poNumber;
    private UUID supplierId;
    private String supplierName;
    private String supplierCode;
    private UUID distributorId;
    private String distributorName;
    private UUID purchaseRequisitionId;
    private String prNumber;
    private PoStatus status;
    private List<Map<String, Object>> items;
    private BigDecimal totalAmount;
    private BigDecimal receivedAmount;
    private String deliveryAddress;
    private Integer paymentTermsDays;
    private LocalDate expectedDeliveryDate;
    private String notes;
    private LocalDateTime sentAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime receivedAt;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PurchaseOrderResponse fromEntity(PurchaseOrder po) {
        return fromEntity(po, null);
    }

    public static PurchaseOrderResponse fromEntity(PurchaseOrder po, String distributorName) {
        return PurchaseOrderResponse.builder()
                .id(po.getId())
                .poNumber(po.getPoNumber())
                .supplierId(po.getSupplier() != null ? po.getSupplier().getId() : null)
                .supplierName(po.getSupplier() != null ? po.getSupplier().getName() : null)
                .supplierCode(po.getSupplier() != null ? po.getSupplier().getSupplierCode() : null)
                .distributorId(po.getDistributorId())
                .distributorName(distributorName)
                .purchaseRequisitionId(po.getPurchaseRequisition() != null ? po.getPurchaseRequisition().getId() : null)
                .prNumber(po.getPurchaseRequisition() != null ? po.getPurchaseRequisition().getPrNumber() : null)
                .status(po.getStatus())
                .items(po.getItems())
                .totalAmount(po.getTotalAmount())
                .receivedAmount(po.getReceivedAmount())
                .deliveryAddress(po.getDeliveryAddress())
                .paymentTermsDays(po.getPaymentTermsDays())
                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                .notes(po.getNotes())
                .sentAt(po.getSentAt())
                .confirmedAt(po.getConfirmedAt())
                .receivedAt(po.getReceivedAt())
                .createdByName(po.getCreatedBy() != null ? po.getCreatedBy().getFullName() : null)
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
