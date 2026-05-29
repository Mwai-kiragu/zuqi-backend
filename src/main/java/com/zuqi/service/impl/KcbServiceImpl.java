package com.zuqi.service.impl;

import com.zuqi.api.dto.kcb.*;
import com.zuqi.api.dto.payment.PaymentRequest;
import com.zuqi.domain.kcb.*;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.InvoiceService;
import com.zuqi.service.KcbService;
import com.zuqi.service.PaymentService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
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
    private final PaymentMethodRepository paymentMethodRepository;
    private final OrderRepository orderRepository;
    private final SecurityUtils securityUtils;
    private final RestTemplate restTemplate;

    @Autowired @Lazy private InvoiceService invoiceService;
    @Autowired @Lazy private PaymentService paymentService;

    @Value("${kcb.add-config-url:https://stk.swerri.io/api/v1/add_business_configs}")
    private String kcbAddConfigUrl;

//    @Value("${kcb.stk-push-url:https://stk.swerri.io/api/v1/kcb_acc_stkpush}")
    @Value("${kcb.stk-push-url:https://stk.swerri.io/api/v1/kcbStkPush}")
    private String kcbStkPushUrl;

    @Value("${kcb.callback-base-url:https://zuqi.pestoe.com/api/v1/kcb/callback}")
    private String callbackBaseUrl;

    @Value("${daraja.business-config-url:https://stk.swerri.io/api/v1/business_config/all}")
    private String darajaBusinessConfigUrl;

    @Override
    @Transactional
    public KcbConfigResponse activateConfig(UUID merchantId, KcbActivateRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));

        User currentUser = securityUtils.getCurrentUser();
        String configuredByName = currentUser != null
                ? (currentUser.getFirstName() + " " + currentUser.getLastName()).trim()
                : "System";

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

        // Always call add_business_configs to register or update the config in swerri.io.
        // This ensures the thirdPartyCallback URL is always current (e.g., when switching between
        // local ngrok and production URLs or when the ngrok session restarts).
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

            log.info("Registering/updating KCB config with swerri.io for merchant {} accountNumber={} callbackUrl={}",
                    merchantId, request.accountNumber(), callbackBaseUrl);

            ResponseEntity<Map> response = restTemplate.exchange(
                    kcbAddConfigUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> resBody = response.getBody();
            if (resBody != null) {
                Object newConfigs = resBody.get("newBusinessConfigs");
                if (newConfigs instanceof Map<?, ?> nc) {
                    String id = getString(nc, "_id");
                    if (id != null) {
                        config.setExternalId(id);
                        log.info("KCB config registered/updated with swerri.io for merchant {} kcbDarajaId={}",
                                merchantId, id);
                    }
                }
                // Some swerri.io responses return the updated config at the top level
                if (config.getExternalId() == null) {
                    String id = getString(resBody, "_id");
                    if (id != null) {
                        config.setExternalId(id);
                        log.info("KCB config id from swerri.io top-level for merchant {} kcbDarajaId={}",
                                merchantId, id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("swerri.io KCB config registration/update failed for merchant {}: {}", merchantId, e.getMessage());
            // Fall back to existing id from lookup if available
            String existingId = lookupSwerriKcbId(request.accountNumber(), request.businessNo());
            if (existingId != null) {
                config.setExternalId(existingId);
                log.info("Using existing KCB config from swerri.io for merchant {} kcbDarajaId={}", merchantId, existingId);
            }
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

        // Callback fields: merchantRequestId, checkoutRequestId, orderId, resultCode, resultDesc, transactionReference
        String merchantRequestId = getString(payload, "merchantRequestId");
        String checkoutRequestId = getString(payload, "checkoutRequestId");
        String orderId           = getString(payload, "orderId");
        String resultDesc        = getString(payload, "resultDesc");
        String receipt           = getString(payload, "transactionReference");

        // resultCode: 0 = success, anything else = failure
        Object resultCodeObj = payload.get("resultCode");
        boolean success = resultCodeObj != null &&
                (Integer.valueOf(0).equals(resultCodeObj) || "0".equals(String.valueOf(resultCodeObj)));

        // Match by merchantRequestId (stored as zedStkId), then checkoutRequestId (stored as requestReferenceId), then orderId
        Optional<KcbStkRequest> optReq = merchantRequestId != null
                ? kcbStkRequestRepository.findByZedStkId(merchantRequestId)
                : Optional.empty();

        if (optReq.isEmpty() && checkoutRequestId != null) {
            optReq = kcbStkRequestRepository.findTopByRequestReferenceIdOrderByCreatedAtDesc(checkoutRequestId);
        }

        if (optReq.isEmpty() && orderId != null) {
            optReq = kcbStkRequestRepository.findTopByReferenceIdOrderByCreatedAtDesc(orderId);
        }

        if (optReq.isEmpty()) {
            log.warn("KCB callback: no matching STK request found for merchantRequestId={} orderId={}", merchantRequestId, orderId);
            return;
        }

        KcbStkRequest req = optReq.get();
        req.setCallbackReceivedAt(LocalDateTime.now());
        req.setResultDesc(resultDesc);
        if (receipt != null) req.setZedStkId(receipt); // store KCB receipt as zedStkId for reconciliation

        if (success) {
            req.setStatus(KcbStkStatus.SUCCESS);
            log.info("KCB payment SUCCESS for referenceId={} receipt={}", req.getReferenceId(), receipt);
        } else {
            req.setStatus(KcbStkStatus.FAILED);
            log.info("KCB payment FAILED for referenceId={} resultCode={} desc={}", req.getReferenceId(), resultCodeObj, resultDesc);
        }

        kcbStkRequestRepository.save(req);

        if (success) {
            reconcilePayment(req);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcilePayment(KcbStkRequest stkRequest) {
        try {
            Long kcbMethodId = paymentMethodRepository.findByCode("KCB")
                    .map(m -> m.getId())
                    .orElse(null);

            String referenceId   = stkRequest.getReferenceId();
            String referenceType = stkRequest.getReferenceType();
            BigDecimal amount    = stkRequest.getAmount();
            String receipt       = stkRequest.getZedStkId();

            if ("INVOICE".equalsIgnoreCase(referenceType)) {
                invoiceService.recordPayment(UUID.fromString(referenceId), amount, kcbMethodId, receipt);
                log.info("Auto-reconciled invoice {} via KCB receipt {}", referenceId, receipt);

            } else if ("ORDER".equalsIgnoreCase(referenceType)) {
                Order order = orderRepository.findById(UUID.fromString(referenceId)).orElse(null);
                if (order != null) {
                    PaymentRequest pr = PaymentRequest.builder()
                            .orderId(order.getId())
                            .merchantId(order.getMerchant().getId())
                            .distributorId(order.getDistributor().getId())
                            .paymentMethodId(kcbMethodId)
                            .amount(amount)
                            .currency("KES")
                            .externalReference(receipt)
                            .build();
                    paymentService.createPayment(pr);
                    log.info("Auto-reconciled order {} via KCB receipt {}", referenceId, receipt);
                }
            } else {
                log.info("No auto-reconciliation for referenceType={} referenceId={}", referenceType, referenceId);
            }
        } catch (Exception e) {
            log.error("Failed to auto-reconcile KCB payment for ref={} type={}: {}",
                    stkRequest.getReferenceId(), stkRequest.getReferenceType(), e.getMessage());
        }
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

    /**
     * Looks up an existing KCB config in swerri.io by accountNumber or businessNo,
     * mirroring M-Pesa's lookupZedBusinessId approach.
     */
    @SuppressWarnings("unchecked")
    private String lookupSwerriKcbId(String accountNumber, String businessNo) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(darajaBusinessConfigUrl, Map.class);
            log.info("swerri.io business_config/all status={} (KCB lookup)", response.getStatusCode());
            if (response.getBody() == null) return null;

            Object rawData = response.getBody().get("data");
            List<?> items = rawData instanceof List ? (List<?>) rawData : null;
            if (items == null) return null;

            for (Object item : items) {
                if (item instanceof Map<?, ?> m) {
                    // swerri.io stores the short code in businessShortCode or businessNumber
                    Object scObj = m.get("businessShortCode");
                    if (scObj == null) scObj = m.get("businessNumber");
                    if (scObj == null) scObj = m.get("accountNumber");
                    String sc = scObj != null ? String.valueOf(scObj) : "";

                    boolean matchAccount = accountNumber != null && accountNumber.equals(sc);
                    boolean matchBizNo   = businessNo   != null && businessNo.equals(sc);

                    if (matchAccount || matchBizNo) {
                        Object id = m.get("_id");
                        if (id != null) {
                            log.info("Found existing KCB config in swerri.io: id={} sc={}", id, sc);
                            return String.valueOf(id);
                        }
                    }
                }
            }
            log.info("No existing KCB config in swerri.io matched accountNumber={} businessNo={}", accountNumber, businessNo);
        } catch (Exception e) {
            log.warn("Could not look up KCB config from swerri.io: {}", e.getMessage());
        }
        return null;
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
