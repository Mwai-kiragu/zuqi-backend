package com.zuqi.api.dto.procurement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zuqi.domain.procurement.PrStatus;
import com.zuqi.domain.procurement.PurchaseRequisition;
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
public class PurchaseRequisitionResponse {

    private UUID id;
    private String prNumber;
    private UUID distributorId;
    private String distributorName;
    private UUID requestedById;
    private String requestedByName;
    private String requestedByEmail;
    private PrStatus status;
    private String description;
    private String justification;
    private LocalDate expectedDeliveryDate;
    private List<Map<String, Object>> items;
    private BigDecimal estimatedTotalAmount;
    private UUID preferredSupplierId;
    private String preferredSupplierName;
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private String approvedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PurchaseRequisitionResponse fromEntity(PurchaseRequisition pr) {
        return fromEntity(pr, null);
    }

    public static PurchaseRequisitionResponse fromEntity(PurchaseRequisition pr, String distributorName) {
        return PurchaseRequisitionResponse.builder()
                .id(pr.getId())
                .prNumber(pr.getPrNumber())
                .distributorId(pr.getDistributorId())
                .distributorName(distributorName)
                .requestedById(pr.getRequestedBy() != null ? pr.getRequestedBy().getId() : null)
                .requestedByName(pr.getRequestedBy() != null ? pr.getRequestedBy().getFullName() : null)
                .requestedByEmail(pr.getRequestedBy() != null ? pr.getRequestedBy().getEmail() : null)
                .status(pr.getStatus())
                .description(pr.getDescription())
                .justification(pr.getJustification())
                .expectedDeliveryDate(pr.getExpectedDeliveryDate())
                .items(pr.getItems())
                .estimatedTotalAmount(pr.getEstimatedTotalAmount())
                .preferredSupplierId(pr.getPreferredSupplierId())
                .preferredSupplierName(pr.getPreferredSupplierName())
                .rejectionReason(pr.getRejectionReason())
                .submittedAt(pr.getSubmittedAt())
                .approvedAt(pr.getApprovedAt())
                .approvedByName(pr.getApprovedBy() != null ? pr.getApprovedBy().getFullName() : null)
                .createdAt(pr.getCreatedAt())
                .updatedAt(pr.getUpdatedAt())
                .build();
    }
}
