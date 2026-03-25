package com.zuqi.api.dto.customer;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.customer.KycStatus;
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
public class CustomerResponse {

    private UUID id;
    private String customerCode;
    private String businessName;
    private String ownerName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String county;
    private String subCounty;
    private String kraPin;
    private String nationalId;
    private List<Map<String, Object>> contactPersons;
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

    public static CustomerResponse fromEntity(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .customerCode(customer.getCustomerCode())
                .businessName(customer.getBusinessName())
                .ownerName(customer.getOwnerName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .city(customer.getCity())
                .county(customer.getCounty())
                .subCounty(customer.getSubCounty())
                .kraPin(customer.getKraPin())
                .nationalId(customer.getNationalId())
                .contactPersons(customer.getContactPersons())
                .latitude(customer.getLatitude())
                .longitude(customer.getLongitude())
                .categoryId(customer.getCategory() != null ? customer.getCategory().getId() : null)
                .categoryName(customer.getCategory() != null ? customer.getCategory().getName() : null)
                .distributorId(customer.getDistributor() != null ? customer.getDistributor().getId() : null)
                .distributorName(customer.getDistributor() != null ? customer.getDistributor().getName() : null)
                .assignedSalesRepId(customer.getAssignedSalesRep() != null ? customer.getAssignedSalesRep().getId() : null)
                .assignedSalesRepName(customer.getAssignedSalesRep() != null ? customer.getAssignedSalesRep().getFullName() : null)
                .creditLimit(customer.getCreditLimit())
                .currentBalance(customer.getCurrentBalance())
                .paymentTermsDays(customer.getPaymentTermsDays())
                .active(customer.isActive())
                .verified(customer.isVerified())
                .blacklisted(customer.isBlacklisted())
                .blacklistedReason(customer.getBlacklistedReason())
                .blacklistedAt(customer.getBlacklistedAt())
                .blacklistedByName(customer.getBlacklistedBy() != null ? customer.getBlacklistedBy().getFullName() : null)
                .kycStatus(customer.getKycStatus())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .deactivationReason(customer.getDeactivationReason())
                .deactivatedAt(customer.getDeactivatedAt())
                .deactivatedByName(customer.getDeactivatedBy() != null ? customer.getDeactivatedBy().getFullName() : null)
                .approvalStatus(customer.getApprovalStatus())
                .createdById(customer.getCreatedById())
                .build();
    }
}
