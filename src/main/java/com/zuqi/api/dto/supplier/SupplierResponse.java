package com.zuqi.api.dto.supplier;

import com.zuqi.domain.merchant.KycStatus;
import com.zuqi.domain.supplier.Supplier;
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
public class SupplierResponse {

    private UUID id;
    private String supplierCode;
    private String name;
    private String kraPin;
    private String registrationNumber;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String county;
    private String subCounty;
    private String bankName;
    private String bankBranch;
    private String bankAccountNumber;
    private String bankAccountName;
    private String swiftCode;
    private Integer paymentTermsDays;
    private BigDecimal creditLimit;
    private List<Map<String, Object>> contactPersons;
    private Long categoryId;
    private String categoryName;
    private UUID distributorId;
    private String distributorName;
    private boolean active;
    private boolean verified;
    private boolean blacklisted;
    private String blacklistedReason;
    private LocalDateTime blacklistedAt;
    private String blacklistedByName;
    private KycStatus kycStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String deactivationReason;
    private LocalDateTime deactivatedAt;
    private String deactivatedByName;
    private String approvalStatus;
    private java.util.UUID createdById;

    public static SupplierResponse fromEntity(Supplier s) {
        return SupplierResponse.builder()
                .id(s.getId())
                .supplierCode(s.getSupplierCode())
                .name(s.getName())
                .kraPin(s.getKraPin())
                .registrationNumber(s.getRegistrationNumber())
                .email(s.getEmail())
                .phone(s.getPhone())
                .address(s.getAddress())
                .city(s.getCity())
                .county(s.getCounty())
                .subCounty(s.getSubCounty())
                .bankName(s.getBankName())
                .bankBranch(s.getBankBranch())
                .bankAccountNumber(s.getBankAccountNumber())
                .bankAccountName(s.getBankAccountName())
                .swiftCode(s.getSwiftCode())
                .paymentTermsDays(s.getPaymentTermsDays())
                .creditLimit(s.getCreditLimit())
                .contactPersons(s.getContactPersons())
                .categoryId(s.getCategory() != null ? s.getCategory().getId() : null)
                .categoryName(s.getCategory() != null ? s.getCategory().getName() : null)
                .distributorId(s.getDistributor() != null ? s.getDistributor().getId() : null)
                .distributorName(s.getDistributor() != null ? s.getDistributor().getName() : null)
                .active(s.isActive())
                .verified(s.isVerified())
                .blacklisted(s.isBlacklisted())
                .blacklistedReason(s.getBlacklistedReason())
                .blacklistedAt(s.getBlacklistedAt())
                .blacklistedByName(s.getBlacklistedBy() != null ? s.getBlacklistedBy().getFullName() : null)
                .kycStatus(s.getKycStatus())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .deactivationReason(s.getDeactivationReason())
                .deactivatedAt(s.getDeactivatedAt())
                .deactivatedByName(s.getDeactivatedBy() != null ? s.getDeactivatedBy().getFullName() : null)
                .approvalStatus(s.getApprovalStatus())
                .createdById(s.getCreatedById())
                .build();
    }
}
