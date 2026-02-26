package com.zuqi.api.dto.kyc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycApplicationResponse {
    private UUID id;
    private String type;
    private String entityName;
    private String ownerName;
    private String email;
    private String phone;
    private String kycStatus;
    private Map<String, Object> kycDocuments;
    private String county;
    private String kraPin;
    private String city;
    private String address;
    private LocalDateTime submittedAt;
    private String businessType;
    private String nationalIdNumber;
}
