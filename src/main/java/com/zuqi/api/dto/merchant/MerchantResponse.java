package com.zuqi.api.dto.merchant;

import com.zuqi.domain.merchant.Merchant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantResponse {

    private UUID id;
    private String businessName;
    private String ownerName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Long categoryId;
    private String categoryName;
    private UUID distributorId;
    private String distributorName;
    private UUID assignedSalesRepId;
    private String assignedSalesRepName;
    private BigDecimal creditLimit;
    private BigDecimal currentBalance;
    private Integer paymentTermsDays;
    private boolean active;
    private boolean verified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String deactivationReason;
    private LocalDateTime deactivatedAt;
    private String deactivatedByName;

    public static MerchantResponse fromEntity(Merchant merchant) {
        return MerchantResponse.builder()
                .id(merchant.getId())
                .businessName(merchant.getBusinessName())
                .ownerName(merchant.getOwnerName())
                .email(merchant.getEmail())
                .phone(merchant.getPhone())
                .address(merchant.getAddress())
                .city(merchant.getCity())
                .latitude(merchant.getLatitude())
                .longitude(merchant.getLongitude())
                .categoryId(merchant.getCategory() != null ? merchant.getCategory().getId() : null)
                .categoryName(merchant.getCategory() != null ? merchant.getCategory().getName() : null)
                .distributorId(merchant.getDistributor() != null ? merchant.getDistributor().getId() : null)
                .distributorName(merchant.getDistributor() != null ? merchant.getDistributor().getName() : null)
                .assignedSalesRepId(merchant.getAssignedSalesRep() != null ? merchant.getAssignedSalesRep().getId() : null)
                .assignedSalesRepName(merchant.getAssignedSalesRep() != null ? merchant.getAssignedSalesRep().getFullName() : null)
                .creditLimit(merchant.getCreditLimit())
                .currentBalance(merchant.getCurrentBalance())
                .paymentTermsDays(merchant.getPaymentTermsDays())
                .active(merchant.isActive())
                .verified(merchant.isVerified())
                .createdAt(merchant.getCreatedAt())
                .updatedAt(merchant.getUpdatedAt())
                .deactivationReason(merchant.getDeactivationReason())
                .deactivatedAt(merchant.getDeactivatedAt())
                .deactivatedByName(merchant.getDeactivatedBy() != null ? merchant.getDeactivatedBy().getFullName() : null)
                .build();
    }
}
