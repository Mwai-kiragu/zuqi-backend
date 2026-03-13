package com.zuqi.api.dto.merchant;

import com.zuqi.domain.customer.KycStatus;
import com.zuqi.domain.merchant.Merchant;
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
public class MerchantResponse {

    private UUID id;
    private UUID distributorId;
    private String name;
    private String registrationNumber;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String country;
    private String logoUrl;
    private boolean active;
    private boolean cashEnabled;
    private KycStatus kycStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MerchantResponse fromEntity(Merchant merchant) {
        return MerchantResponse.builder()
                .id(merchant.getId())
                .name(merchant.getName())
                .registrationNumber(merchant.getRegistrationNumber())
                .email(merchant.getEmail())
                .phone(merchant.getPhone())
                .address(merchant.getAddress())
                .city(merchant.getCity())
                .country(merchant.getCountry())
                .logoUrl(merchant.getLogoUrl())
                .active(merchant.isActive())
                .cashEnabled(merchant.isCashEnabled())
                .kycStatus(merchant.getKycStatus())
                .createdAt(merchant.getCreatedAt())
                .updatedAt(merchant.getUpdatedAt())
                .build();
    }
}
