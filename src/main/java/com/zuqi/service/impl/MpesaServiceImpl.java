package com.zuqi.service.impl;

import com.zuqi.api.dto.mpesa.*;
import com.zuqi.api.dto.payment.PaymentRequest;
import com.zuqi.domain.mpesa.*;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.InvoiceService;
import com.zuqi.service.MpesaService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MpesaServiceImpl implements MpesaService {

    private final MpesaConfigRepository mpesaConfigRepository;
    private final MpesaStkRequestRepository mpesaStkRequestRepository;
    private final MerchantRepository merchantRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final OrderRepository orderRepository;
    private final SecurityUtils securityUtils;
    private final RestTemplate restTemplate;

    // @Lazy to avoid circular dependency (InvoiceService → PaymentService → ... → MpesaService)
    @Autowired @Lazy private InvoiceService invoiceService;
    @Autowired @Lazy private PaymentService paymentService;

    @Value("${daraja.stk-push-url:https://stk.swerri.io/api/v1/stkPush}")
    private String darajaStkPushUrl;

    @Value("${daraja.callback-base-url:https://zuqi.pestoe.com/api/v1/mpesa/callback}")
    private String callbackBaseUrl;

    @Value("${daraja.business-config-url:https://stk.swerri.io/api/v1/business_config/all}")
    private String darajaBusinessConfigUrl;

    @Value("${daraja.edit-config-url:https://stk.swerri.io/api/v1/business_config/edit}")
    private String darajaEditConfigUrl;

    @Override
    @Transactional
    public MpesaConfigResponse activateConfig(UUID merchantId, MpesaActivateRequest request) {
        if (!request.termsAccepted()) {
            throw new ValidationException("Terms must be accepted to activate M-Pesa");
        }

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));

        // For TILL, businessShortCode defaults to tillNumber when not supplied
        String effectiveShortCode = (request.businessShortCode() != null && !request.businessShortCode().isBlank())
                ? request.businessShortCode()
                : request.tillNumber();

        User currentUser = securityUtils.getCurrentUser();
        String configuredByName = currentUser != null
                ? (currentUser.getFirstName() + " " + currentUser.getLastName()).trim()
                : "System";

        // Upsert: update existing ACTIVE config if present, otherwise create new
        List<MpesaConfig> existing = mpesaConfigRepository
                .findByMerchantIdAndTransactionTypeAndStatus(merchantId, request.transactionType(), MpesaConfigStatus.ACTIVE);

        MpesaConfig config;
        if (!existing.isEmpty()) {
            config = existing.get(0);
            // Deactivate any duplicates beyond the first
            if (existing.size() > 1) {
                existing.subList(1, existing.size()).forEach(c -> c.setStatus(MpesaConfigStatus.INACTIVE));
                mpesaConfigRepository.saveAll(existing.subList(1, existing.size()));
            }
            log.info("Updating existing {} config {} for merchant {}", request.transactionType(), config.getId(), merchantId);
        } else {
            config = MpesaConfig.builder()
                    .merchant(merchant)
                    .transactionType(request.transactionType())
                    .status(MpesaConfigStatus.ACTIVE)
                    .termsAccepted(request.termsAccepted())
                    .configuredBy(currentUser)
                    .configuredByName(configuredByName)
                    .build();
            log.info("Creating new {} config for merchant {}", request.transactionType(), merchantId);
        }

        // Apply all fields (create or update)
        config.setBusinessName(request.businessName());
        config.setBusinessShortCode(effectiveShortCode);
        config.setTillNumber(request.tillNumber());
        config.setStoreNumber(request.storeNumber());
        config.setHoNumber(request.hoNumber());
        config.setBusinessNo(request.businessNo());
        config.setAccountReference(request.accountReference());
        config.setConsumerKey(request.consumerKey());
        config.setConsumerSecret(request.consumerSecret());
        config.setPassKey(request.passKey());
        config.setThirdPartyCallback(callbackBaseUrl);
        config.setTermsAccepted(request.termsAccepted());
        config.setConfiguredBy(currentUser);
        config.setConfiguredByName(configuredByName);
        if (request.externalId() != null && !request.externalId().isBlank()) {
            config.setExternalId(request.externalId());
        }

        MpesaConfig saved = mpesaConfigRepository.save(config);
        log.info("M-Pesa config activated for merchant {} — type={} shortCode={}",
                merchantId, request.transactionType(), request.businessShortCode());

        // Auto-resolve Zed businessId if not already set
        if (saved.getExternalId() == null || saved.getExternalId().isBlank()) {
            String zedId = lookupZedBusinessId(effectiveShortCode, request.transactionType());
            if (zedId == null && request.storeNumber() != null && !request.storeNumber().isBlank()
                    && !request.storeNumber().equals(effectiveShortCode)) {
                zedId = lookupZedBusinessId(request.storeNumber(), request.transactionType());
            }
            if (zedId == null && request.hoNumber() != null && !request.hoNumber().isBlank()
                    && !request.hoNumber().equals(effectiveShortCode)) {
                zedId = lookupZedBusinessId(request.hoNumber(), request.transactionType());
            }
            if (zedId != null) {
                saved.setExternalId(zedId);
                saved = mpesaConfigRepository.save(saved);
                log.info("Resolved Zed businessId={} for shortCode={}", zedId, effectiveShortCode);
            } else {
                log.warn("Zed businessId not found for shortCode={} — config saved without it", effectiveShortCode);
            }
        }

        // Push updated credentials + callback to Zed for existing linked configs
        if (saved.getExternalId() != null && !saved.getExternalId().isBlank()) {
            String newExternalId = updateZedCallback(saved);
            if (newExternalId != null && !newExternalId.equals(saved.getExternalId())) {
                log.info("Zed returned new externalId={} after edit (was {})", newExternalId, saved.getExternalId());
                saved.setExternalId(newExternalId);
                saved = mpesaConfigRepository.save(saved);
            }
        }

        return MpesaConfigResponse.fromEntity(saved);
    }

    @Override
    public List<MpesaConfigResponse> getConfigs(UUID merchantId) {
        return mpesaConfigRepository.findByMerchantId(merchantId)
                .stream()
                .map(MpesaConfigResponse::fromEntity)
                .toList();
    }

    @Override
    public List<MpesaConfigResponse> getAllConfigs() {
        return mpesaConfigRepository.findAll(
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(MpesaConfigResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public MpesaConfigResponse deactivateConfig(UUID configId) {
        MpesaConfig config = mpesaConfigRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("MpesaConfig", "id", configId));
        config.setStatus(MpesaConfigStatus.INACTIVE);
        return MpesaConfigResponse.fromEntity(mpesaConfigRepository.save(config));
    }

    @Override
    @Transactional
    public StkPushResponse initiateStk(StkPushRequest request) {
        // Look up config by externalId (may have duplicates — take the first active one)
        List<MpesaConfig> configs = mpesaConfigRepository.findByExternalId(request.businessId());
        MpesaConfig config = configs.stream()
                .filter(c -> c.getStatus() == MpesaConfigStatus.ACTIVE)
                .findFirst()
                .orElse(configs.isEmpty() ? null : configs.get(0));
        if (config == null) {
            throw new ResourceNotFoundException("MpesaConfig", "businessId", request.businessId());
        }

        if (config.getStatus() != MpesaConfigStatus.ACTIVE) {
            throw new ValidationException("M-Pesa configuration is not active");
        }

        String phone = normalizePhone(request.phone());

        // Persist before the API call so we have a record even on failure
        MpesaStkRequest stkRequest = MpesaStkRequest.builder()
                .referenceId(request.referenceId())
                .referenceType(request.referenceType())
                .merchantId(config.getMerchant().getId())
                .phoneNumber(phone)
                .amount(request.amount())
                .status(MpesaStkStatus.PENDING)
                .build();
        stkRequest = mpesaStkRequestRepository.save(stkRequest);

        try {
            // businessId comes directly from the request (Zed darajaConfigId)
            String businessId = request.businessId();

            String accountRef = (config.getAccountReference() != null && !config.getAccountReference().isBlank())
                    ? config.getAccountReference()
                    : "";

            Map<String, Object> body = new HashMap<>();
            body.put("amount", request.amount().intValue());
            body.put("phone", phone);
            body.put("Order_ID", request.referenceId());
            body.put("businessId", businessId);
            body.put("business_account_reference", accountRef);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.exchange(
                    darajaStkPushUrl, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> resBody = response.getBody();
            // Response mirrors Safaricom: { "ResponseCode": "0", "CheckoutRequestID": "...", "MerchantRequestID": "..." }
            String responseCode = resBody != null ? String.valueOf(resBody.get("ResponseCode")) : null;
            String checkoutId   = resBody != null ? String.valueOf(resBody.get("CheckoutRequestID")) : null;
            String merchantReqId = resBody != null ? String.valueOf(resBody.get("MerchantRequestID")) : null;

            stkRequest.setCheckoutRequestId(checkoutId);
            stkRequest.setMerchantRequestId(merchantReqId);

            if ("0".equals(responseCode)) {
                mpesaStkRequestRepository.save(stkRequest);
                return new StkPushResponse(stkRequest.getId(), checkoutId, merchantReqId,
                        request.referenceId(), request.referenceType(), "PENDING",
                        "STK push sent successfully. Please check your phone.");
            } else {
                String errMsg = resBody != null ? String.valueOf(resBody.get("errorMessage")) : "STK push failed";
                stkRequest.setStatus(MpesaStkStatus.FAILED);
                stkRequest.setResultDesc(errMsg);
                mpesaStkRequestRepository.save(stkRequest);
                throw new ValidationException(errMsg != null && !errMsg.equals("null") ? errMsg : "STK push failed");
            }

        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            stkRequest.setStatus(MpesaStkStatus.FAILED);
            stkRequest.setResultDesc(e.getMessage());
            mpesaStkRequestRepository.save(stkRequest);
            log.error("STK push failed for reference {}: {}", request.referenceId(), e.getMessage());
            throw new ValidationException("M-Pesa STK push failed: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void handleStkCallback(StkCallbackPayload payload) {
        log.info("M-Pesa STK callback received: checkoutId={} resultCode={}",
                payload.checkoutRequestId(), payload.resultCode());

        Optional<MpesaStkRequest> optRequest = mpesaStkRequestRepository
                .findByCheckoutRequestId(payload.checkoutRequestId());

        if (optRequest.isEmpty()) {
            optRequest = mpesaStkRequestRepository
                    .findTopByReferenceIdOrderByCreatedAtDesc(payload.orderId());
        }

        if (optRequest.isEmpty()) {
            log.warn("No STK request found for checkoutId={} orderId={}",
                    payload.checkoutRequestId(), payload.orderId());
            return;
        }

        MpesaStkRequest stkRequest = optRequest.get();
        stkRequest.setResultCode(payload.resultCode());
        stkRequest.setResultDesc(payload.resultDesc());
        stkRequest.setCallbackReceivedAt(LocalDateTime.now());

        if ("0".equals(payload.resultCode())) {
            stkRequest.setStatus(MpesaStkStatus.SUCCESS);
            stkRequest.setMpesaReceiptNumber(payload.mpesaReceiptNumber());
            mpesaStkRequestRepository.save(stkRequest);
            log.info("M-Pesa payment SUCCESS: receipt={} referenceId={}",
                    payload.mpesaReceiptNumber(), stkRequest.getReferenceId());
            // Auto-reconcile: mark the invoice/order as paid
            reconcilePayment(stkRequest);
        } else {
            stkRequest.setStatus(MpesaStkStatus.FAILED);
            mpesaStkRequestRepository.save(stkRequest);
            log.info("M-Pesa payment FAILED: desc={} referenceId={}",
                    payload.resultDesc(), stkRequest.getReferenceId());
        }
    }

    @Override
    public StkPushResponse getStkStatus(UUID stkRequestId) {
        MpesaStkRequest req = mpesaStkRequestRepository.findById(stkRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("MpesaStkRequest", "id", stkRequestId));
        return new StkPushResponse(
                req.getId(),
                req.getCheckoutRequestId(),
                req.getMerchantRequestId(),
                req.getReferenceId(),
                req.getReferenceType(),
                req.getStatus().name(),
                req.getResultDesc() != null ? req.getResultDesc() : req.getStatus().name()
        );
    }

    @Override
    public boolean getCashEnabled(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));
        return merchant.isCashEnabled();
    }

    @Override
    @Transactional
    public boolean setCashEnabled(UUID merchantId, boolean enabled) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));
        merchant.setCashEnabled(enabled);
        merchantRepository.save(merchant);
        log.info("Cash payments {} for merchant {}", enabled ? "enabled" : "disabled", merchantId);
        return enabled;
    }

    // ---- helpers ----

    /**
     * Runs in its own transaction so a reconciliation failure never rolls back the callback update.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcilePayment(MpesaStkRequest stkRequest) {
        try {
            Long mpesaMethodId = paymentMethodRepository.findByCode("MPESA")
                    .map(m -> m.getId())
                    .orElse(null);

            String referenceId  = stkRequest.getReferenceId();
            String referenceType = stkRequest.getReferenceType();
            BigDecimal amount    = stkRequest.getAmount();
            String receipt       = stkRequest.getMpesaReceiptNumber();

            if ("INVOICE".equalsIgnoreCase(referenceType)) {
                invoiceService.recordPayment(referenceId, amount, mpesaMethodId, receipt);
                log.info("Auto-reconciled invoice {} via M-Pesa receipt {}", referenceId, receipt);

            } else if ("ORDER".equalsIgnoreCase(referenceType)) {
                Order order = orderRepository.findById(UUID.fromString(referenceId)).orElse(null);
                if (order != null) {
                    PaymentRequest pr = PaymentRequest.builder()
                            .orderId(order.getId())
                            .merchantId(order.getMerchant().getId())
                            .distributorId(order.getDistributor().getId())
                            .paymentMethodId(mpesaMethodId)
                            .amount(amount)
                            .currency("KES")
                            .externalReference(receipt)
                            .build();
                    paymentService.createPayment(pr);
                    log.info("Auto-reconciled order {} via M-Pesa receipt {}", referenceId, receipt);
                }
            } else {
                log.info("No auto-reconciliation for referenceType={} referenceId={}", referenceType, referenceId);
            }
        } catch (Exception e) {
            log.error("Failed to auto-reconcile M-Pesa payment for ref={} type={}: {}",
                    stkRequest.getReferenceId(), stkRequest.getReferenceType(), e.getMessage());
        }
    }

    /**
     * Calls the Zed edit endpoint to update the thirdPartyCallback to our callback URL.
     * Returns the new externalId (_id) from the response if Zed returns a new one.
     */
    @SuppressWarnings("unchecked")
    private String updateZedCallback(MpesaConfig config) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("businessId", config.getExternalId());
            body.put("businessName", config.getBusinessName());
            body.put("businessShortCode", config.getBusinessShortCode());
            body.put("consumerKey", config.getConsumerKey());
            body.put("consumerSecret", config.getConsumerSecret());
            body.put("accountReference", config.getAccountReference() != null ? config.getAccountReference() : "");
            body.put("passKey", config.getPassKey());
            body.put("thirdPartyCallback", callbackBaseUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            log.info("Updating Zed thirdPartyCallback for businessId={} to {}", config.getExternalId(), callbackBaseUrl);
            ResponseEntity<Map> response = restTemplate.exchange(
                    darajaEditConfigUrl, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);

            log.info("Zed edit-config response: status={} body={}", response.getStatusCode(), response.getBody());

            if (response.getBody() != null) {
                // Try to extract the new _id from newBusinessConfigs._id
                Object newConfigs = response.getBody().get("newBusinessConfigs");
                if (newConfigs instanceof Map<?, ?> nc) {
                    Object newId = nc.get("_id");
                    if (newId != null) return String.valueOf(newId);
                }
                // Some versions return _id directly
                Object directId = response.getBody().get("_id");
                if (directId != null) return String.valueOf(directId);
            }
        } catch (Exception e) {
            log.warn("Could not update Zed callback for config {}: {}", config.getId(), e.getMessage());
        }
        return null;
    }

    /**
     * Calls the Zed payment service to find the latest darajaConfigId (_id) matching the given
     * short code and transaction type. Falls back to short-code-only match if no type match found.
     * The API returns records newest-first, so the first match is always the most recent.
     */
    @SuppressWarnings("unchecked")
    private String lookupZedBusinessId(String shortCode, MpesaTransactionType transactionType) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(darajaBusinessConfigUrl, Map.class);
            log.info("Zed business_config/all status={} body={}", response.getStatusCode(), response.getBody());
            if (response.getBody() == null) return null;

            Object rawData = response.getBody().get("data");
            List<?> items = rawData instanceof List ? (List<?>) rawData : null;
            if (items == null) {
                log.warn("Zed business_config/all returned no 'data' list; keys={}", response.getBody().keySet());
                return null;
            }

            // Map our type to Zed's TransactionType string
            String zedType = (transactionType == MpesaTransactionType.TILL)
                    ? "CustomerBuyGoodsOnline" : "CustomerPayBillOnline";

            String fallbackId = null; // best short-code-only match

            for (Object item : items) {
                if (item instanceof Map<?, ?> m) {
                    // Try multiple field names Zed may use for the short code
                    Object scObj = m.get("businessShortCode");
                    if (scObj == null) scObj = m.get("businessNumber");
                    if (scObj == null) scObj = m.get("tillNumber");
                    if (scObj == null) scObj = m.get("shortCode");
                    if (scObj == null) scObj = m.get("till_number");
                    if (scObj == null) scObj = m.get("short_code");
                    String sc = scObj != null ? String.valueOf(scObj) : "";
                    log.debug("Zed config entry: shortCode={} id={} keys={}", sc, m.get("_id"), m.keySet());

                    if (shortCode != null && shortCode.equals(sc)) {
                        Object id = m.get("_id");
                        if (id == null) continue;
                        String idStr = String.valueOf(id);

                        // Prefer exact type match (list is newest-first → first hit wins)
                        Object typeObj = m.get("TransactionType");
                        if (typeObj == null) typeObj = m.get("transactionType");
                        String type = typeObj != null ? String.valueOf(typeObj) : "";
                        if (zedType.equalsIgnoreCase(type)) {
                            log.info("Matched Zed businessId={} shortCode={} type={}", idStr, shortCode, type);
                            return idStr;
                        }
                        if (fallbackId == null) fallbackId = idStr; // keep first shortCode match as fallback
                    }
                }
            }

            if (fallbackId != null) {
                log.warn("No exact type match for shortCode={} type={}; using fallback id={}", shortCode, zedType, fallbackId);
                return fallbackId;
            }
            log.warn("No Zed business config found for shortCode={}", shortCode);
        } catch (Exception e) {
            log.warn("Could not auto-resolve Zed businessId for shortCode={}: {}", shortCode, e.getMessage());
        }
        return null;
    }

    private String normalizePhone(String phone) {
        if (phone == null) return phone;
        phone = phone.replaceAll("\\s+", "");
        if (phone.startsWith("+")) phone = phone.substring(1);
        if (phone.startsWith("07") || phone.startsWith("01")) {
            phone = "254" + phone.substring(1);
        } else if (phone.startsWith("7") || phone.startsWith("1")) {
            phone = "254" + phone;
        }
        return phone;
    }
}
