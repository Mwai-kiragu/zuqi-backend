package com.zuqi.service.impl;

import com.zuqi.api.dto.kcb.*;
import com.zuqi.domain.kcb.*;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.KcbConfigRepository;
import com.zuqi.repository.KcbStkRequestRepository;
import com.zuqi.repository.MerchantRepository;
import com.zuqi.service.KcbService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class KcbServiceImpl implements KcbService {

    private final KcbConfigRepository kcbConfigRepository;
    private final KcbStkRequestRepository kcbStkRequestRepository;
    private final MerchantRepository merchantRepository;
    private final SecurityUtils securityUtils;
    private final RestTemplate restTemplate;

    /** swerri.io endpoint that registers a KCB collection account (same service as M-Pesa) */
    @Value("${kcb.add-config-url:https://stk.swerri.io/api/v1/add_business_configs}")
    private String kcbAddConfigUrl;

    /** swerri.io endpoint for KCB KCBACCOUNT STK push — takes businessId directly, no JWT needed */
    @Value("${kcb.stk-push-url:https://stk.swerri.io/api/v1/kcb_acc_stkpush}")
    private String kcbStkPushUrl;

    @Value("${kcb.callback-base-url:https://zuqi.pestoe.com/api/v1/kcb/callback}")
    private String callbackBaseUrl;

    @Override
    @Transactional
    public KcbConfigResponse activateConfig(UUID merchantId, KcbActivateRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));

        User currentUser = securityUtils.getCurrentUser();
        String configuredByName = currentUser != null
                ? (currentUser.getFirstName() + " " + currentUser.getLastName()).trim()
                : "System";

        // Upsert: update existing ACTIVE config if present, otherwise create new
        List<KcbConfig> existing = kcbConfigRepository.findByMerchantIdAndStatus(merchantId, KcbConfigStatus.ACTIVE);

        KcbConfig config;
        if (!existing.isEmpty()) {
            config = existing.get(0);
            if (existing.size() > 1) {
                existing.subList(1, existing.size()).forEach(c -> c.setStatus(KcbConfigStatus.INACTIVE));
                kcbConfigRepository.saveAll(existing.subList(1, existing.size()));
            }
            log.info("Updating existing KCB config {} for merchant {}", config.getId(), merchantId);
        } else {
            config = KcbConfig.builder()
                    .merchant(merchant)
                    .status(KcbConfigStatus.ACTIVE)
                    .configuredBy(currentUser)
                    .configuredByName(configuredByName)
                    .build();
            log.info("Creating new KCB config for merchant {}", merchantId);
        }

        config.setBusinessName(request.businessName());
        config.setAccountNumber(request.accountNumber());
        config.setKcbAccountType(request.kcbAccountType() != null ? request.kcbAccountType() : "KCBACCOUNT");
        config.setBusinessNo(request.businessNo());
        config.setAccountType(request.accountType() != null ? request.accountType() : "general");
        config.setSubscriptionAccount(request.subscriptionAccount());
        config.setThirdPartyCallback(callbackBaseUrl);
        config.setConfiguredBy(currentUser);
        config.setConfiguredByName(configuredByName);

        // Register with swerri.io (same service that handles M-Pesa configs)
        // No JWT needed — just account details. Returns a kcbDarajaId (_id) we use for STK push.
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("businessName", request.businessName());
            body.put("accountReference", request.accountNumber());
            body.put("businessShortCode", request.accountNumber());
            body.put("consumerKey", "");
            body.put("consumerSecret", "");
            body.put("passKey", "");
            body.put("thirdPartyCallback", callbackBaseUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            log.info("Registering KCB config with swerri.io for merchant {} accountNumber={}",
                    merchantId, request.accountNumber());

            ResponseEntity<Map> response = restTemplate.exchange(
                    kcbAddConfigUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> resBody = response.getBody();
            if (resBody != null) {
                Object newConfigs = resBody.get("newBusinessConfigs");
                if (newConfigs instanceof Map<?, ?> nc) {
                    String id = getString(nc, "_id");
                    if (id != null) {
                        config.setExternalId(id);
                        log.info("KCB config registered with swerri.io for merchant {} kcbDarajaId={}",
                                merchantId, id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("swerri.io KCB config registration failed for merchant {}: {}", merchantId, e.getMessage());
            // Store config locally anyway — externalId can be set manually or retried
        }

        KcbConfig saved = kcbConfigRepository.save(config);
        return KcbConfigResponse.fromEntity(saved);
    }

    @Override
    public List<KcbConfigResponse> getConfigs(UUID merchantId) {
        return kcbConfigRepository.findByMerchantId(merchantId)
                .stream()
                .map(KcbConfigResponse::fromEntity)
                .toList();
    }

    @Override
    public List<KcbConfigResponse> getAllConfigs() {
        return kcbConfigRepository.findAll(
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(KcbConfigResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public KcbConfigResponse deactivateConfig(UUID configId) {
        KcbConfig config = kcbConfigRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("KcbConfig", "id", configId));
        config.setStatus(KcbConfigStatus.INACTIVE);
        return KcbConfigResponse.fromEntity(kcbConfigRepository.save(config));
    }

    @Override
    @Transactional
    public KcbStkPushResponse initiateStk(KcbStkPushRequest request) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId == null) {
            throw new ValidationException("Merchant context required for KCB STK push");
        }

        List<KcbConfig> activeConfigs = kcbConfigRepository.findByMerchantIdAndStatus(merchantId, KcbConfigStatus.ACTIVE);
        if (activeConfigs.isEmpty()) {
            throw new ValidationException("No active KCB configuration found for this merchant");
        }
        KcbConfig kcbConfig = activeConfigs.get(0);

        if (kcbConfig.getExternalId() == null || kcbConfig.getExternalId().isBlank()) {
            throw new ValidationException(
                    "KCB configuration is not fully set up — please re-save the KCB configuration to register with the payment provider.");
        }

        String phone = normalizePhone(request.phone());

        KcbStkRequest stkRequest = KcbStkRequest.builder()
                .referenceId(request.referenceId())
                .referenceType(request.referenceType())
                .merchantId(merchantId)
                .phoneNumber(phone)
                .amount(request.amount())
                .status(KcbStkStatus.PENDING)
                .build();
        stkRequest = kcbStkRequestRepository.save(stkRequest);

        try {
            // Call swerri.io KCB STK push directly using the stored kcbDarajaId (externalId)
            // Same pattern as M-Pesa: no business number or JWT needed
            Map<String, Object> body = new HashMap<>();
            body.put("amount", request.amount().intValue());
            body.put("phone", phone);
            body.put("Order_ID", request.referenceId());
            body.put("businessId", kcbConfig.getExternalId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            log.info("Initiating KCB STK push via swerri.io for ref={} phone={} amount={} businessId={}",
                    request.referenceId(), phone, request.amount(), kcbConfig.getExternalId());

            ResponseEntity<Map> response = restTemplate.exchange(
                    kcbStkPushUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> resBody = response.getBody();
            if (resBody != null) {
                // swerri.io wraps KCB response: {response: {ResponseCode, MerchantRequestID}, header: {statusCode}}
                Object headerObj = resBody.get("header");
                if (headerObj instanceof Map<?, ?> header) {
                    String statusCode = getString(header, "statusCode");
                    if (statusCode != null && !"0".equals(statusCode)) {
                        String statusDesc = getString(header, "statusDescription");
                        throw new RuntimeException("KCB error: " + statusDesc);
                    }
                }

                Object responseObj = resBody.get("response");
                if (responseObj instanceof Map<?, ?> resp) {
                    String responseCode = getString(resp, "ResponseCode");
                    String merchantRequestId = getString(resp, "MerchantRequestID");
                    String checkoutRequestId = getString(resp, "CheckoutRequestID");

                    stkRequest.setZedStkId(merchantRequestId);
                    stkRequest.setRequestReferenceId(checkoutRequestId);
                    stkRequest.setStkOrderId(request.referenceId());
                    stkRequest = kcbStkRequestRepository.save(stkRequest);

                    if (!"0".equals(responseCode)) {
                        String errDesc = getString(resp, "ResponseDescription");
                        throw new RuntimeException(errDesc != null ? errDesc : "KCB STK push rejected (code=" + responseCode + ")");
                    }
                    log.info("KCB STK push initiated merchantRequestId={} checkoutRequestId={}",
                            merchantRequestId, checkoutRequestId);
                }
            }
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            stkRequest.setStatus(KcbStkStatus.FAILED);
            stkRequest.setResultDesc(e.getMessage());
            kcbStkRequestRepository.save(stkRequest);
            log.error("KCB STK push failed for ref={}: {}", request.referenceId(), e.getMessage());
            throw new ValidationException("KCB STK push failed: " + e.getMessage());
        }

        return toResponse(stkRequest, "KCB payment prompt sent. Please enter your KCB PIN to complete.");
    }

    @Override
    public KcbStkPushResponse getStkStatus(UUID stkRequestId) {
        KcbStkRequest req = kcbStkRequestRepository.findById(stkRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("KcbStkRequest", "id", stkRequestId));
        return toResponse(req, req.getResultDesc() != null ? req.getResultDesc() : req.getStatus().name());
    }

    @Override
    @Transactional
    public void handleCallback(Map<String, Object> payload) {
        log.info("KCB STK callback received: {}", payload);

        String zedStkId = payload.containsKey("id") ? String.valueOf(payload.get("id")) : null;
        String referenceId = payload.containsKey("orderId") ? String.valueOf(payload.get("orderId")) : null;
        String statusStr = payload.containsKey("status") ? String.valueOf(payload.get("status")) : null;
        String resultDesc = payload.containsKey("message") ? String.valueOf(payload.get("message")) : null;

        Optional<KcbStkRequest> optReq = zedStkId != null
                ? kcbStkRequestRepository.findByZedStkId(zedStkId)
                : Optional.empty();

        if (optReq.isEmpty() && referenceId != null) {
            optReq = kcbStkRequestRepository.findTopByReferenceIdOrderByCreatedAtDesc(referenceId);
        }

        if (optReq.isEmpty()) {
            log.warn("KCB callback: no matching STK request found for zedStkId={} refId={}", zedStkId, referenceId);
            return;
        }

        KcbStkRequest req = optReq.get();
        req.setCallbackReceivedAt(LocalDateTime.now());
        req.setResultDesc(resultDesc);

        boolean success = "SUCCESS".equalsIgnoreCase(statusStr) || "200".equals(statusStr)
                || (payload.containsKey("status") && Integer.valueOf(200).equals(payload.get("status")));

        if (success) {
            req.setStatus(KcbStkStatus.SUCCESS);
            log.info("KCB payment SUCCESS for referenceId={}", req.getReferenceId());
        } else {
            req.setStatus(KcbStkStatus.FAILED);
            log.info("KCB payment FAILED for referenceId={} desc={}", req.getReferenceId(), resultDesc);
        }

        kcbStkRequestRepository.save(req);
    }

    private KcbStkPushResponse toResponse(KcbStkRequest req, String message) {
        return new KcbStkPushResponse(
                req.getId(),
                req.getZedStkId(),
                req.getStkOrderId(),
                req.getRequestReferenceId(),
                req.getReferenceId(),
                req.getReferenceType(),
                req.getStatus().name(),
                message
        );
    }

    private String normalizePhone(String phone) {
        if (phone == null) return phone;
        phone = phone.replaceAll("\\s+", "");
        if (phone.startsWith("+")) phone = phone.substring(1);
        if (phone.startsWith("07") || phone.startsWith("01")) phone = "254" + phone.substring(1);
        return phone;
    }

    private String getString(Map<?, ?> map, String key) {
        Object val = map.get(key);
        return val != null && !"null".equals(String.valueOf(val)) ? String.valueOf(val) : null;
    }
}
