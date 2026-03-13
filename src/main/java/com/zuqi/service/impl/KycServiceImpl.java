package com.zuqi.service.impl;

import com.zuqi.api.dto.kyc.DistributorKycRequest;
import com.zuqi.api.dto.kyc.KycApplicationResponse;
import com.zuqi.api.dto.kyc.MerchantKycRequest;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.customer.KycStatus;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.UserRepository;
import com.zuqi.service.KycService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycServiceImpl implements KycService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final DistributorRepository distributorRepository;

    @Override
    @Transactional
    public void submitMerchantKyc(UUID userId, MerchantKycRequest request) {
        log.info("Submitting merchant KYC for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        Customer merchant;
        if (user.getCustomerId() != null) {
            merchant = customerRepository.findById(user.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", user.getCustomerId().toString()));
        } else {
            // Create new customer record for the user
            merchant = Customer.builder()
                    .businessName(request.getBusinessName())
                    .ownerName(user.getFirstName() + " " + user.getLastName())
                    .email(user.getEmail())
                    .phone(user.getPhoneNumber() != null ? user.getPhoneNumber() : "")
                    .active(true)
                    .build();
        }

        merchant.setBusinessName(request.getBusinessName());
        merchant.setAddress(request.getPhysicalAddress());
        merchant.setCity(request.getCity());
        merchant.setCounty(request.getCounty());
        merchant.setKraPin(request.getKraPin());

        // Store additional KYC data
        Map<String, Object> kycDocs = merchant.getKycDocuments() != null ? merchant.getKycDocuments() : new HashMap<>();
        kycDocs.put("businessType", request.getBusinessType());
        kycDocs.put("nationalIdNumber", request.getNationalIdNumber());
        merchant.setKycDocuments(kycDocs);

        merchant.setKycStatus(KycStatus.SUBMITTED);
        Customer savedMerchant = customerRepository.save(merchant);

        // Link customer to user if not already linked
        if (user.getCustomerId() == null) {
            user.setCustomerId(savedMerchant.getId());
            userRepository.save(user);
        }

        log.info("Customer KYC submitted for user: {}, customer: {}", userId, savedMerchant.getId());
    }

    @Override
    @Transactional
    public void submitDistributorKyc(UUID userId, DistributorKycRequest request) {
        log.info("Submitting distributor KYC for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        if (user.getDistributorId() == null) {
            throw new ValidationException("User is not associated with a distributor");
        }

        Distributor distributor = distributorRepository.findById(user.getDistributorId())
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", user.getDistributorId().toString()));

        // Store KYC data
        Map<String, Object> kycDocs = distributor.getKycDocuments() != null ? distributor.getKycDocuments() : new HashMap<>();
        kycDocs.put("kraPin", request.getKraPin());
        kycDocs.put("county", request.getCounty());
        distributor.setKycDocuments(kycDocs);

        distributor.setKycStatus(KycStatus.SUBMITTED);
        distributorRepository.save(distributor);

        log.info("Distributor KYC submitted for user: {}, distributor: {}", userId, distributor.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public String getKycStatus(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        if (user.getCustomerId() != null) {
            return customerRepository.findById(user.getCustomerId())
                    .map(m -> m.getKycStatus().name())
                    .orElse("PENDING");
        }

        if (user.getDistributorId() != null) {
            return distributorRepository.findById(user.getDistributorId())
                    .map(d -> d.getKycStatus() != null ? d.getKycStatus().name() : "PENDING")
                    .orElse("PENDING");
        }

        return "PENDING";
    }

    @Override
    @Transactional(readOnly = true)
    public Page<KycApplicationResponse> getApplications(String status, Pageable pageable) {
        List<KycApplicationResponse> results = new ArrayList<>();

        if (status != null && !status.isEmpty()) {
            KycStatus kycStatus = KycStatus.valueOf(status);
            Page<Customer> merchants = customerRepository.findByKycStatus(kycStatus, pageable);
            merchants.getContent().forEach(m -> results.add(mapMerchant(m)));

            Page<Distributor> distributors = distributorRepository.findByKycStatus(kycStatus, pageable);
            distributors.getContent().forEach(d -> results.add(mapDistributor(d)));
        } else {
            Page<Customer> merchants = customerRepository.findByKycStatusNot(KycStatus.PENDING, pageable);
            merchants.getContent().forEach(m -> results.add(mapMerchant(m)));

            Page<Distributor> distributors = distributorRepository.findByKycStatusNot(KycStatus.PENDING, pageable);
            distributors.getContent().forEach(d -> results.add(mapDistributor(d)));
        }

        results.sort(Comparator.comparing(KycApplicationResponse::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        return new PageImpl<>(results, pageable, results.size());
    }

    @Override
    @Transactional
    public void approveKyc(UUID entityId, String type) {
        if ("MERCHANT".equalsIgnoreCase(type)) {
            Customer merchant = customerRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", entityId.toString()));
            merchant.setKycStatus(KycStatus.APPROVED);
            customerRepository.save(merchant);
            log.info("Customer KYC approved: {}", entityId);
        } else if ("DISTRIBUTOR".equalsIgnoreCase(type)) {
            Distributor distributor = distributorRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", entityId.toString()));
            distributor.setKycStatus(KycStatus.APPROVED);
            distributorRepository.save(distributor);
            log.info("Distributor KYC approved: {}", entityId);
        } else {
            throw new ValidationException("Invalid type: " + type + ". Must be MERCHANT or DISTRIBUTOR.");
        }
    }

    @Override
    @Transactional
    public void rejectKyc(UUID entityId, String type, String reason) {
        if ("MERCHANT".equalsIgnoreCase(type)) {
            Customer merchant = customerRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", entityId.toString()));
            merchant.setKycStatus(KycStatus.REJECTED);
            Map<String, Object> docs = merchant.getKycDocuments() != null ? merchant.getKycDocuments() : new HashMap<>();
            docs.put("rejectionReason", reason);
            merchant.setKycDocuments(docs);
            customerRepository.save(merchant);
            log.info("Customer KYC rejected: {}, reason: {}", entityId, reason);
        } else if ("DISTRIBUTOR".equalsIgnoreCase(type)) {
            Distributor distributor = distributorRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", entityId.toString()));
            distributor.setKycStatus(KycStatus.REJECTED);
            Map<String, Object> docs = distributor.getKycDocuments() != null ? distributor.getKycDocuments() : new HashMap<>();
            docs.put("rejectionReason", reason);
            distributor.setKycDocuments(docs);
            distributorRepository.save(distributor);
            log.info("Distributor KYC rejected: {}, reason: {}", entityId, reason);
        } else {
            throw new ValidationException("Invalid type: " + type + ". Must be MERCHANT or DISTRIBUTOR.");
        }
    }

    private KycApplicationResponse mapMerchant(Customer m) {
        Map<String, Object> docs = m.getKycDocuments() != null ? m.getKycDocuments() : new HashMap<>();
        return KycApplicationResponse.builder()
                .id(m.getId())
                .type("MERCHANT")
                .entityName(m.getBusinessName())
                .ownerName(m.getOwnerName())
                .email(m.getEmail())
                .phone(m.getPhone())
                .kycStatus(m.getKycStatus() != null ? m.getKycStatus().name() : "PENDING")
                .kycDocuments(docs)
                .county(m.getCounty())
                .kraPin(m.getKraPin())
                .city(m.getCity())
                .address(m.getAddress())
                .submittedAt(m.getUpdatedAt() != null ? m.getUpdatedAt() : m.getCreatedAt())
                .businessType(docs.get("businessType") != null ? docs.get("businessType").toString() : null)
                .nationalIdNumber(docs.get("nationalIdNumber") != null ? docs.get("nationalIdNumber").toString() : null)
                .build();
    }

    private KycApplicationResponse mapDistributor(Distributor d) {
        Map<String, Object> docs = d.getKycDocuments() != null ? d.getKycDocuments() : new HashMap<>();
        return KycApplicationResponse.builder()
                .id(d.getId())
                .type("DISTRIBUTOR")
                .entityName(d.getName())
                .ownerName(null)
                .email(d.getEmail())
                .phone(d.getPhone())
                .kycStatus(d.getKycStatus() != null ? d.getKycStatus().name() : "PENDING")
                .kycDocuments(docs)
                .county(docs.get("county") != null ? docs.get("county").toString() : null)
                .kraPin(docs.get("kraPin") != null ? docs.get("kraPin").toString() : null)
                .city(d.getCity())
                .address(d.getAddress())
                .submittedAt(d.getUpdatedAt() != null ? d.getUpdatedAt() : d.getCreatedAt())
                .businessType(null)
                .nationalIdNumber(null)
                .build();
    }
}
