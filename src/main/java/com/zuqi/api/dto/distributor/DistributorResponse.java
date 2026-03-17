package com.zuqi.api.dto.distributor;

import com.zuqi.domain.distributor.Distributor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistributorResponse {
    private UUID id;
    private String name;
    private String registrationNumber;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String country;
    private boolean active;
    private LocalDateTime createdAt;
    private String deactivationReason;
    private LocalDateTime deactivatedAt;
    private String deactivatedByName;
    private UUID merchantId;
    private String merchantName;

    public static DistributorResponse fromEntity(Distributor distributor) {
        return DistributorResponse.builder()
                .id(distributor.getId())
                .name(distributor.getName())
                .registrationNumber(distributor.getRegistrationNumber())
                .email(distributor.getEmail())
                .phone(distributor.getPhone())
                .address(distributor.getAddress())
                .city(distributor.getCity())
                .country(distributor.getCountry())
                .active(distributor.isActive())
                .createdAt(distributor.getCreatedAt())
                .deactivationReason(distributor.getDeactivationReason())
                .deactivatedAt(distributor.getDeactivatedAt())
                .deactivatedByName(distributor.getDeactivatedBy() != null ? distributor.getDeactivatedBy().getFullName() : null)
                .merchantId(distributor.getMerchant() != null ? distributor.getMerchant().getId() : null)
                .merchantName(distributor.getMerchant() != null ? distributor.getMerchant().getName() : null)
                .build();
    }
}
