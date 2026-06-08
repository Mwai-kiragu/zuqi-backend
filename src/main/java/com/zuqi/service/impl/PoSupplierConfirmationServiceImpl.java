package com.zuqi.service.impl;

import com.zuqi.api.dto.procurement.PoConfirmationDetailsResponse;
import com.zuqi.domain.procurement.PoConfirmationToken;
import com.zuqi.domain.procurement.PoStatus;
import com.zuqi.domain.procurement.PurchaseOrder;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.PoConfirmationTokenRepository;
import com.zuqi.repository.PurchaseOrderRepository;
import com.zuqi.service.NotificationService;
import com.zuqi.service.PoSupplierConfirmationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PoSupplierConfirmationServiceImpl implements PoSupplierConfirmationService {

    private static final int TOKEN_EXPIRY_DAYS = 7;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PoConfirmationTokenRepository tokenRepository;
    private final PurchaseOrderRepository poRepository;
    private final DistributorRepository distributorRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public Map<String, String> generateTokensForPo(PurchaseOrder po) {
        tokenRepository.deleteUnusedByPoId(po.getId());

        Map<String, String> result = new LinkedHashMap<>();
        for (String action : List.of("CONFIRM", "DECLINE", "PARTIAL")) {
            String rawToken = generateSecureToken();
            tokenRepository.save(PoConfirmationToken.builder()
                    .po(po)
                    .token(rawToken)
                    .action(action)
                    .expiresAt(LocalDateTime.now().plusDays(TOKEN_EXPIRY_DAYS))
                    .build());
            result.put(action, rawToken);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PoConfirmationDetailsResponse getTokenDetails(String token) {
        PoConfirmationToken ct = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("ConfirmationToken", "token", token));

        PurchaseOrder po = ct.getPo();
        String tokenStatus = resolveTokenStatus(ct, po);
        String distributorName = distributorRepository.findById(po.getDistributorId())
                .map(d -> d.getName())
                .orElse(null);

        return PoConfirmationDetailsResponse.builder()
                .action(ct.getAction())
                .tokenStatus(tokenStatus)
                .poNumber(po.getPoNumber())
                .supplierName(po.getSupplier() != null ? po.getSupplier().getName() : null)
                .distributorName(distributorName)
                .items(po.getItems())
                .totalAmount(po.getTotalAmount())
                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                .notes(po.getNotes())
                .supplierResponse(po.getSupplierResponse())
                .supplierNotes(po.getSupplierNotes())
                .build();
    }

    @Override
    @Transactional
    public void processResponse(String token, String notes) {
        PoConfirmationToken ct = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("ConfirmationToken", "token", token));

        if (ct.getUsedAt() != null) {
            throw new ValidationException("This confirmation link has already been used.");
        }
        if (ct.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ValidationException("This confirmation link has expired. Please contact " +
                    "the procurement team for a new one.");
        }

        PurchaseOrder po = ct.getPo();
        if (po.getSupplierRespondedAt() != null) {
            throw new ValidationException("A response has already been recorded for this purchase order.");
        }

        String action = ct.getAction();
        po.setSupplierResponse(action);
        po.setSupplierNotes(notes);
        po.setSupplierRespondedAt(LocalDateTime.now());

        switch (action) {
            case "CONFIRM"  -> { po.setStatus(PoStatus.CONFIRMED); po.setConfirmedAt(LocalDateTime.now()); }
            case "DECLINE"  -> po.setStatus(PoStatus.DECLINED);
            // PARTIAL: keep SENT status, record notes for procurement team to follow up
            default -> { /* PARTIAL — no status change */ }
        }

        ct.setUsedAt(LocalDateTime.now());
        tokenRepository.save(ct);
        poRepository.save(po);

        try {
            notificationService.notifyPoSupplierResponse(po);
        } catch (Exception e) {
            log.warn("Failed to send supplier response notification for PO {}: {}", po.getPoNumber(), e.getMessage());
        }
    }

    private String resolveTokenStatus(PoConfirmationToken ct, PurchaseOrder po) {
        if (ct.getUsedAt() != null || po.getSupplierRespondedAt() != null) return "USED";
        if (ct.getExpiresAt().isBefore(LocalDateTime.now())) return "EXPIRED";
        return "ACTIVE";
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
