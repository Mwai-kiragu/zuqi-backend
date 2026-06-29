package com.zuqi.service.impl;

import com.zuqi.api.dto.ncba.*;
import com.zuqi.api.dto.payment.PaymentRequest;
import com.zuqi.domain.ncba.*;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.InvoiceService;
import com.zuqi.service.NcbaService;
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
public class NcbaServiceImpl implements NcbaService {

    private final NcbaConfigRepository ncbaConfigRepository;
    private final NcbaStkRequestRepository ncbaStkRequestRepository;
    private final MerchantRepository merchantRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final OrderRepository orderRepository;
    private final SecurityUtils securityUtils;
    private final RestTemplate restTemplate;

    @Autowired @Lazy private InvoiceService invoiceService;
    @Autowired @Lazy private PaymentService paymentService;
    @Autowired @Lazy private com.zuqi.service.PosService posService;

    @Value("${ncba.config-url:https://zuqipayment.dev.zed.business/api/v1/configurations}")
    private String ncbaConfigUrl;

    @Value("${ncba.stk-push-url:https://zuqipayment.dev.zed.business/api/v1/stk-push/initiate}")
    private String ncbaStkPushUrl;

    @Value("${ncba.stk-status-url:https://zuqipayment.dev.zed.business/api/v1/stk-push}")
    private String ncbaStkStatusUrl;

    @Value("${ncba.callback-base-url:https://zuqi.pestoe.com/api/v1/ncba/callback}")
    private String callbackBaseUrl;

    @Override
    @Transactional
    public NcbaConfigResponse activateConfig(UUID merchantId, NcbaActivateRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", merchantId));

        User currentUser = securityUtils.getCurrentUser();
        String configuredByName = currentUser != null
                ? (currentUser.getFirstName() + " " + currentUser.getLastName()).trim()
                : "System";

        List<NcbaConfig> existing = ncbaConfigRepository.findByMerchantIdAndStatus(merchantId, NcbaConfigStatus.ACTIVE);

        NcbaConfig config;
        if (!existing.isEmpty()) {
            config = existing.get(0);
            if (existing.size() > 1) {
                existing.subList(1, existing.size()).forEach(c -> c.setStatus(NcbaConfigStatus.INACTIVE));
                ncbaConfigRepository.saveAll(existing.subList(1, existing.size()));
            }
            log.info("Updating existing NCBA config {} for merchant {}", config.getId(), merchantId);
        } else {
            config = NcbaConfig.builder()
                    .merchant(merchant)
                    .status(NcbaConfigStatus.ACTIVE)
                    .configuredBy(currentUser)
                    .configuredByName(configuredByName)
                    .build();
            log.info("Creating new NCBA config for merchant {}", merchantId);
        }

        config.setBusinessName(request.businessName());
        config.setPaybillNo(request.paybillNo());
        config.setNetwork(request.network() != null ? request.network() : "Safaricom");
        config.setWebhookUrl(callbackBaseUrl);
        config.setConfiguredBy(currentUser);
        config.setConfiguredByName(configuredByName);

        // Register configuration with NCBA payment service
        String lookupId = registerWithNcba(request.businessName(), request.paybillNo(),
                request.network() != null ? request.network() : "Safaricom");
        if (lookupId != null) {
            config.setLookupId(lookupId);
        }

        NcbaConfig saved = ncbaConfigRepository.save(config);
        return NcbaConfigResponse.fromEntity(saved);
    }

    @Override
    public List<NcbaConfigResponse> getConfigs(UUID merchantId) {
        return ncbaConfigRepository.findByMerchantId(merchantId)
                .stream()
                .map(NcbaConfigResponse::fromEntity)
                .toList();
    }

    @Override
    public List<NcbaConfigResponse> getAllConfigs() {
        return ncbaConfigRepository.findAll(
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(NcbaConfigResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public NcbaConfigResponse deactivateConfig(UUID configId) {
        NcbaConfig config = ncbaConfigRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("NcbaConfig", "id", configId));
        config.setStatus(NcbaConfigStatus.INACTIVE);
        return NcbaConfigResponse.fromEntity(ncbaConfigRepository.save(config));
    }

    @Override
    @Transactional
    public NcbaStkPushResponse initiateStk(NcbaStkPushRequest request) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId == null) {
            throw new ValidationException("Merchant context required for NCBA STK push");
        }
        return doInitiateStk(merchantId, request);
    }

    @Override
    public NcbaStkPushResponse initiatePublicStk(UUID merchantId, String phone, java.math.BigDecimal amount, String referenceId) {
        if (merchantId == null) throw new ValidationException("Merchant context required for NCBA STK push");
        return doInitiateStk(merchantId, new com.zuqi.api.dto.ncba.NcbaStkPushRequest(phone, amount, referenceId, "INVOICE", referenceId));
    }

    private NcbaStkPushResponse doInitiateStk(UUID merchantId, NcbaStkPushRequest request) {
        List<NcbaConfig> activeConfigs = ncbaConfigRepository.findByMerchantIdAndStatus(merchantId, NcbaConfigStatus.ACTIVE);
        if (activeConfigs.isEmpty()) {
            throw new ValidationException("No active NCBA configuration found for this merchant");
        }
        NcbaConfig ncbaConfig = activeConfigs.get(0);

        if (ncbaConfig.getLookupId() == null || ncbaConfig.getLookupId().isBlank()) {
            throw new ValidationException(
                    "NCBA configuration is not fully registered — please re-save the NCBA configuration.");
        }

        String phone = normalizePhone(request.phone());
        String accountNo = request.accountNo() != null ? request.accountNo() : request.referenceId();

        NcbaStkRequest stkRequest = NcbaStkRequest.builder()
                .referenceId(request.referenceId())
                .referenceType(request.referenceType())
                .merchantId(merchantId)
                .phoneNumber(phone)
                .amount(request.amount())
                .accountNo(accountNo)
                .lookupId(ncbaConfig.getLookupId())
                .status(NcbaStkStatus.PENDING)
                .build();
        stkRequest = ncbaStkRequestRepository.save(stkRequest);

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("lookup_id", ncbaConfig.getLookupId());
            body.put("telephone_no", phone);
            body.put("amount", request.amount().toPlainString());
            body.put("account_no", accountNo);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            log.info("Initiating NCBA STK push for ref={} phone={} amount={} lookupId={}",
                    request.referenceId(), phone, request.amount(), ncbaConfig.getLookupId());

            ResponseEntity<Map> response = restTemplate.exchange(
                    ncbaStkPushUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> resBody = response.getBody();
            if (resBody != null) {
                String transactionId = getString(resBody, "transaction_id");
                if (transactionId == null) transactionId = getString(resBody, "transactionId");
                if (transactionId == null) transactionId = getString(resBody, "id");

                if (transactionId != null) {
                    stkRequest.setTransactionId(transactionId);
                    stkRequest = ncbaStkRequestRepository.save(stkRequest);
                    log.info("NCBA STK push initiated transactionId={}", transactionId);
                }

                // Check for error in response
                String status = getString(resBody, "status");
                if (status != null && (status.equalsIgnoreCase("error") || status.equalsIgnoreCase("failed"))) {
                    String message = getString(resBody, "message");
                    throw new RuntimeException(message != null ? message : "NCBA STK push rejected");
                }
            }
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            stkRequest.setStatus(NcbaStkStatus.FAILED);
            stkRequest.setResultDesc(e.getMessage());
            ncbaStkRequestRepository.save(stkRequest);
            log.error("NCBA STK push failed for ref={}: {}", request.referenceId(), e.getMessage());
            throw new ValidationException("NCBA STK push failed: " + e.getMessage());
        }

        return toResponse(stkRequest, "NCBA payment prompt sent. Please enter your PIN to complete.");
    }

    @Override
    @Transactional
    public NcbaStkPushResponse getStkStatus(UUID stkRequestId) {
        NcbaStkRequest req = ncbaStkRequestRepository.findById(stkRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("NcbaStkRequest", "id", stkRequestId));

        // If still pending and we have a transactionId, poll NCBA for current status
        if (req.getStatus() == NcbaStkStatus.PENDING && req.getTransactionId() != null) {
            pollAndUpdateStatus(req);
        }

        return toResponse(req, req.getResultDesc() != null ? req.getResultDesc() : req.getStatus().name());
    }

    @Override
    @Transactional
    public void handleCallback(Map<String, Object> payload) {
        log.info("NCBA STK callback received: {}", payload);

        String transactionId = getString(payload, "transaction_id");
        if (transactionId == null) transactionId = getString(payload, "transactionId");
        String referenceId    = getString(payload, "account_no");
        if (referenceId == null) referenceId = getString(payload, "reference_id");
        String resultDesc     = getString(payload, "message");
        if (resultDesc == null) resultDesc = getString(payload, "description");

        // Determine success: status == "success" or resultCode == 0
        Object resultCodeObj = payload.get("resultCode");
        Object statusObj = payload.get("status");
        boolean success = (resultCodeObj != null &&
                (Integer.valueOf(0).equals(resultCodeObj) || "0".equals(String.valueOf(resultCodeObj))))
                || (statusObj != null && "success".equalsIgnoreCase(String.valueOf(statusObj)));

        Optional<NcbaStkRequest> optReq = transactionId != null
                ? ncbaStkRequestRepository.findByTransactionId(transactionId)
                : Optional.empty();

        if (optReq.isEmpty() && referenceId != null) {
            optReq = ncbaStkRequestRepository.findTopByReferenceIdOrderByCreatedAtDesc(referenceId);
        }

        if (optReq.isEmpty()) {
            log.warn("NCBA callback: no matching STK request found for transactionId={}", transactionId);
            return;
        }

        NcbaStkRequest req = optReq.get();
        req.setCallbackReceivedAt(LocalDateTime.now());
        if (resultDesc != null) req.setResultDesc(resultDesc);

        if (success) {
            req.setStatus(NcbaStkStatus.SUCCESS);
            log.info("NCBA payment SUCCESS for referenceId={} transactionId={}", req.getReferenceId(), transactionId);
        } else {
            req.setStatus(NcbaStkStatus.FAILED);
            log.info("NCBA payment FAILED for referenceId={} desc={}", req.getReferenceId(), resultDesc);
        }

        ncbaStkRequestRepository.save(req);

        if (success) {
            reconcilePayment(req);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcilePayment(NcbaStkRequest stkRequest) {
        try {
            Long ncbaMethodId = paymentMethodRepository.findByCode("NCBA")
                    .map(m -> m.getId())
                    .orElse(null);

            String referenceId   = stkRequest.getReferenceId();
            String referenceType = stkRequest.getReferenceType();
            BigDecimal amount    = stkRequest.getAmount();
            String receipt       = stkRequest.getTransactionId();

            if ("INVOICE".equalsIgnoreCase(referenceType)) {
                invoiceService.recordPayment(referenceId, amount, ncbaMethodId, receipt);
                log.info("Auto-reconciled invoice {} via NCBA transactionId={}", referenceId, receipt);

            } else if ("ORDER".equalsIgnoreCase(referenceType)) {
                Order order = orderRepository.findById(UUID.fromString(referenceId)).orElse(null);
                if (order != null) {
                    PaymentRequest pr = PaymentRequest.builder()
                            .orderId(order.getId())
                            .merchantId(order.getMerchant().getId())
                            .distributorId(order.getDistributor().getId())
                            .paymentMethodId(ncbaMethodId)
                            .amount(amount)
                            .currency("KES")
                            .externalReference(receipt)
                            .build();
                    paymentService.createPayment(pr);
                    log.info("Auto-reconciled order {} via NCBA transactionId={}", referenceId, receipt);
                }
            } else if ("POS_SALE".equalsIgnoreCase(referenceType)) {
                com.zuqi.api.dto.pos.ProcessPaymentRequest pr = new com.zuqi.api.dto.pos.ProcessPaymentRequest();
                pr.setPaymentMethod(com.zuqi.domain.pos.PosPaymentMethod.NCBA);
                pr.setAmount(amount);
                pr.setReferenceNumber(receipt);
                posService.recordGatewayPayment(UUID.fromString(referenceId), pr);
                log.info("Auto-reconciled POS sale {} via NCBA transactionId={}", referenceId, receipt);

            } else {
                log.info("No auto-reconciliation for referenceType={} referenceId={}", referenceType, referenceId);
            }
        } catch (Exception e) {
            log.error("Failed to auto-reconcile NCBA payment for ref={} type={}: {}",
                    stkRequest.getReferenceId(), stkRequest.getReferenceType(), e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private String registerWithNcba(String businessName, String paybillNo, String network) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("business_name", businessName);
            body.put("paybill_no", paybillNo);
            body.put("webhook_url", callbackBaseUrl);
            body.put("network", network);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            log.info("Registering NCBA config for paybill={} network={}", paybillNo, network);

            ResponseEntity<Map> response = restTemplate.exchange(
                    ncbaConfigUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> resBody = response.getBody();
            if (resBody != null) {
                String lookupId = getString(resBody, "lookup_id");
                if (lookupId == null) lookupId = getString(resBody, "lookupId");
                if (lookupId == null) lookupId = getString(resBody, "id");
                if (lookupId != null) {
                    log.info("NCBA config registered, lookupId={}", lookupId);
                    return lookupId;
                }
            }
        } catch (Exception e) {
            log.warn("NCBA config registration failed: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void pollAndUpdateStatus(NcbaStkRequest req) {
        try {
            String url = ncbaStkStatusUrl + "/" + req.getTransactionId();
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null) return;

            String status = getString(body, "status");
            if (status == null) return;

            if ("success".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) {
                req.setStatus(NcbaStkStatus.SUCCESS);
                String desc = getString(body, "message");
                if (desc != null) req.setResultDesc(desc);
                ncbaStkRequestRepository.save(req);
                reconcilePayment(req);

            } else if ("failed".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
                req.setStatus(NcbaStkStatus.FAILED);
                String desc = getString(body, "message");
                if (desc != null) req.setResultDesc(desc);
                ncbaStkRequestRepository.save(req);
            }
        } catch (Exception e) {
            log.debug("NCBA status poll failed for transactionId={}: {}", req.getTransactionId(), e.getMessage());
        }
    }

    private NcbaStkPushResponse toResponse(NcbaStkRequest req, String message) {
        return new NcbaStkPushResponse(
                req.getId(),
                req.getTransactionId(),
                req.getLookupId(),
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
