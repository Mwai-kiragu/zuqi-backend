package com.zuqi.service.impl;

import com.zuqi.api.dto.supplier.SupplierBillRequest;
import com.zuqi.api.dto.supplier.SupplierBillResponse;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.procurement.PurchaseOrder;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.domain.supplier.SupplierBill;
import com.zuqi.domain.supplier.SupplierBillStatus;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.service.ActivityLogService;
import com.zuqi.service.GlAutoPostingService;
import com.zuqi.service.SupplierBillService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SupplierBillServiceImpl implements SupplierBillService {

    private final SupplierBillRepository supplierBillRepository;
    private final SupplierRepository supplierRepository;
    private final DistributorRepository distributorRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final UserRepository userRepository;
    private final GlAutoPostingService glAutoPostingService;
    private final SecurityUtils securityUtils;
    private final ActivityLogService activityLogService;

    private SupplierBill findById(UUID id) {
        return supplierBillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SupplierBill", "id", id));
    }

    private String generateBillNumber() {
        Optional<String> max = supplierBillRepository.findMaxBillNumber();
        if (max.isEmpty()) return "SBILL-00001";
        try {
            String last = max.get().replace("SBILL-", "");
            int next = Integer.parseInt(last) + 1;
            return String.format("SBILL-%05d", next);
        } catch (NumberFormatException e) {
            return "SBILL-" + System.currentTimeMillis();
        }
    }

    private List<Map<String, Object>> convertItems(List<com.zuqi.api.dto.supplier.SupplierBillLineItem> items) {
        if (items == null) return new ArrayList<>();
        return items.stream().map(item -> {
            Map<String, Object> map = new HashMap<>();
            map.put("description", item.getDescription());
            map.put("quantity", item.getQuantity());
            map.put("unitPrice", item.getUnitPrice());
            map.put("amount", item.getAmount());
            return map;
        }).collect(Collectors.toList());
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierBillResponse> getAllBills(SupplierBillStatus status, Pageable pageable) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            if (status != null)
                return supplierBillRepository.findByDistributorMerchantIdAndStatus(merchantId, status, pageable)
                        .map(SupplierBillResponse::from);
            return supplierBillRepository.findByDistributorMerchantId(merchantId, pageable)
                    .map(SupplierBillResponse::from);
        }

        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            if (status != null)
                return supplierBillRepository.findByDistributorIdAndStatusOrderByCreatedAtDesc(distributorId, status, pageable)
                        .map(SupplierBillResponse::from);
            return supplierBillRepository.findByDistributorIdOrderByCreatedAtDesc(distributorId, pageable)
                    .map(SupplierBillResponse::from);
        }

        return supplierBillRepository.findAll(pageable).map(SupplierBillResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierBillResponse getBillById(UUID id) {
        return SupplierBillResponse.from(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierBillResponse> getSupplierBills(UUID supplierId, Pageable pageable) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            return supplierBillRepository.findBySupplierIdAndMerchantId(supplierId, merchantId, pageable)
                    .map(SupplierBillResponse::from);
        }

        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            return supplierBillRepository.findBySupplierIdAndDistributorIdOrderByCreatedAtDesc(supplierId, distributorId, pageable)
                    .map(SupplierBillResponse::from);
        }
        return Page.empty(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierBillResponse> getOutstandingBillsForSupplier(UUID supplierId) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId == null) {
            UUID merchantId = securityUtils.getCurrentUserMerchantId();
            if (merchantId != null) {
                // Get first distributor for MERCHANT_ADMIN — use the merchant-scoped query
                return supplierBillRepository.findBySupplierIdAndMerchantId(supplierId, merchantId,
                        org.springframework.data.domain.PageRequest.of(0, 100))
                        .stream()
                        .filter(b -> b.getStatus() != SupplierBillStatus.PAID && b.getStatus() != SupplierBillStatus.CANCELLED)
                        .map(SupplierBillResponse::from)
                        .collect(Collectors.toList());
            }
            return List.of();
        }
        return supplierBillRepository.findOutstandingBySupplierAndDistributor(supplierId, distributorId)
                .stream().map(SupplierBillResponse::from).collect(Collectors.toList());
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Override
    public SupplierBillResponse createBill(SupplierBillRequest req) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();

        Distributor distributor;
        if (distributorId != null) {
            distributor = distributorRepository.findById(distributorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", distributorId));
        } else if (merchantId != null) {
            distributor = distributorRepository.findFirstByMerchantId(merchantId)
                    .orElseThrow(() -> new ValidationException("No distributor found for merchant"));
        } else {
            throw new ValidationException("Cannot determine distributor for current user");
        }

        Supplier supplier = supplierRepository.findById(req.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", req.getSupplierId()));

        PurchaseOrder po = null;
        if (req.getPurchaseOrderId() != null) {
            po = purchaseOrderRepository.findById(req.getPurchaseOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", req.getPurchaseOrderId()));
        }

        UUID currentUserId = securityUtils.getCurrentUserId();
        User createdBy = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;

        SupplierBill bill = SupplierBill.builder()
                .billNumber(generateBillNumber())
                .distributor(distributor)
                .supplier(supplier)
                .purchaseOrder(po)
                .referenceNumber(req.getReferenceNumber())
                .billDate(req.getBillDate())
                .dueDate(req.getDueDate())
                .billType(req.getBillType())
                .description(req.getDescription())
                .items(convertItems(req.getItems()))
                .totalAmount(req.getTotalAmount())
                .notes(req.getNotes())
                .createdBy(createdBy)
                .build();

        SupplierBill savedBill = supplierBillRepository.save(bill);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.CREATE, "SUPPLIER_BILL", savedBill.getId(),
                savedBill.getBillNumber(), "SUPPLIERS", "Created supplier bill: " + savedBill.getBillNumber()
            );
        }
        return SupplierBillResponse.from(savedBill);
    }

    @Override
    public SupplierBillResponse updateBill(UUID id, SupplierBillRequest req) {
        SupplierBill bill = findById(id);
        if (bill.getStatus() != SupplierBillStatus.DRAFT) {
            throw new ValidationException("Only DRAFT bills can be edited");
        }

        Supplier supplier = supplierRepository.findById(req.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", req.getSupplierId()));

        PurchaseOrder po = null;
        if (req.getPurchaseOrderId() != null) {
            po = purchaseOrderRepository.findById(req.getPurchaseOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", req.getPurchaseOrderId()));
        }

        bill.setSupplier(supplier);
        bill.setPurchaseOrder(po);
        bill.setReferenceNumber(req.getReferenceNumber());
        bill.setBillDate(req.getBillDate());
        bill.setDueDate(req.getDueDate());
        bill.setBillType(req.getBillType());
        bill.setDescription(req.getDescription());
        bill.setItems(convertItems(req.getItems()));
        bill.setTotalAmount(req.getTotalAmount());
        bill.setNotes(req.getNotes());

        SupplierBill updatedBill = supplierBillRepository.save(bill);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.UPDATE, "SUPPLIER_BILL", updatedBill.getId(),
                updatedBill.getBillNumber(), "SUPPLIERS", "Updated supplier bill: " + updatedBill.getBillNumber()
            );
        }
        return SupplierBillResponse.from(updatedBill);
    }

    @Override
    public SupplierBillResponse receiveBill(UUID id) {
        SupplierBill bill = findById(id);
        if (bill.getStatus() != SupplierBillStatus.DRAFT) {
            throw new ValidationException("Only DRAFT bills can be marked as received");
        }
        bill.setStatus(SupplierBillStatus.RECEIVED);
        SupplierBill saved = supplierBillRepository.save(bill);

        // GL auto-post
        try {
            glAutoPostingService.postSupplierBillReceived(saved);
            saved.setGlPosted(true);
            saved = supplierBillRepository.save(saved);
        } catch (Exception e) {
            log.warn("GL auto-post failed for supplier bill {}: {}", saved.getBillNumber(), e.getMessage());
        }

        return SupplierBillResponse.from(saved);
    }

    @Override
    public SupplierBillResponse cancelBill(UUID id) {
        SupplierBill bill = findById(id);
        if (bill.getStatus() == SupplierBillStatus.PAID) {
            throw new ValidationException("Paid bills cannot be cancelled");
        }
        bill.setStatus(SupplierBillStatus.CANCELLED);
        return SupplierBillResponse.from(supplierBillRepository.save(bill));
    }

    @Override
    public void applyPayment(UUID billId, BigDecimal amount) {
        SupplierBill bill = findById(billId);
        BigDecimal newPaid = bill.getPaidAmount().add(amount);
        bill.setPaidAmount(newPaid);

        if (newPaid.compareTo(bill.getTotalAmount()) >= 0) {
            bill.setStatus(SupplierBillStatus.PAID);
        } else if (newPaid.compareTo(BigDecimal.ZERO) > 0) {
            bill.setStatus(SupplierBillStatus.PARTIALLY_PAID);
        }

        supplierBillRepository.save(bill);
        log.info("Applied payment {} to supplier bill {} — new paid amount: {}, status: {}",
                amount, bill.getBillNumber(), newPaid, bill.getStatus());
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.UPDATE, "SUPPLIER_BILL", bill.getId(),
                bill.getBillNumber(), "SUPPLIERS", "Marked as paid: " + bill.getBillNumber() + " — amount " + amount
            );
        }
    }
}
