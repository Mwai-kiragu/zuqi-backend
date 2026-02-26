package com.zuqi.service;

import com.zuqi.api.dto.kyc.DistributorKycRequest;
import com.zuqi.api.dto.kyc.KycApplicationResponse;
import com.zuqi.api.dto.kyc.MerchantKycRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface KycService {

    void submitMerchantKyc(UUID userId, MerchantKycRequest request);

    void submitDistributorKyc(UUID userId, DistributorKycRequest request);

    String getKycStatus(UUID userId);

    Page<KycApplicationResponse> getApplications(String status, Pageable pageable);

    void approveKyc(UUID entityId, String type);

    void rejectKyc(UUID entityId, String type, String reason);
}
