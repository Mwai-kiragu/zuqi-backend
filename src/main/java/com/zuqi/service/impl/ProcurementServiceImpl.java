package com.zuqi.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.api.dto.procurement.ProcurementItemDto;
import com.zuqi.api.dto.procurement.PurchaseOrderRequest;
import com.zuqi.api.dto.procurement.PurchaseOrderResponse;
import com.zuqi.api.dto.procurement.PurchaseRequisitionRequest;
import com.zuqi.api.dto.procurement.PurchaseRequisitionResponse;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.procurement.PoStatus;
import com.zuqi.domain.procurement.PrStatus;
import com.zuqi.domain.procurement.PurchaseOrder;
import com.zuqi.domain.procurement.PurchaseRequisition;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.PurchaseOrderRepository;
import com.zuqi.repository.PurchaseRequisitionRepository;
import com.zuqi.repository.SupplierRepository;
import com.zuqi.service.ApprovalService;
import com.zuqi.service.ApprovalThresholdService;
import com.zuqi.service.ProcurementService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcurementServiceImpl implements ProcurementService {

    private final PurchaseRequisitionRepository prRepository;
    private final PurchaseOrderRepository poRepository;
    private final SupplierRepository supplierRepository;
    private final DistributorRepository distributorRepository;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;
    private final ApprovalThresholdService approvalThresholdService;
    private final ApprovalService approvalService;

    private String generatePrNumber() {
        long count = prRepository.countAll();
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        return String.format("PR-%s%04d", datePart, count + 1);
    }

    private String generatePoNumber() {
        long count = poRepository.countAll();
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        return String.format("PO-%s%04d", datePart, count + 1);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itemsToMaps(List<ProcurementItemDto> items) {
        return items.stream()
                .map(item -> (Map<String, Object>) objectMapper.convertValue(item, Map.class))
                .toList();
    }

    private BigDecimal calculateTotal(List<ProcurementItemDto> items) {
        return items.stream()
                .map(item -> {
                    BigDecimal cost = item.getEstimatedUnitCost() != null ? item.getEstimatedUnitCost()
                            : item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;
                    return cost.multiply(BigDecimal.valueOf(item.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseRequisitionResponse> getPurchaseRequisitions(UUID distributorId, PrStatus status, UUID requestedById, Pageable pageable) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return prRepository.findWithFilters(effectiveDistributorId, status, requestedById, pageable)
                .map(pr -> PurchaseRequisitionResponse.fromEntity(pr, resolveDistributorName(pr.getDistributorId())));
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseRequisitionResponse getPurchaseRequisitionById(UUID id) {
        PurchaseRequisition pr = findPrById(id);
        return PurchaseRequisitionResponse.fromEntity(pr, resolveDistributorName(pr.getDistributorId()));
    }

    @Override
    @Transactional
    public PurchaseRequisitionResponse createPurchaseRequisition(PurchaseRequisitionRequest request, User currentUser) {
        log.info("Creating purchase requisition for user: {}", currentUser.getId());

        UUID distributorId = request.getDistributorId() != null
                ? request.getDistributorId()
                : securityUtils.getDistributorIdForFiltering();

        PurchaseRequisition pr = PurchaseRequisition.builder()
                .prNumber(generatePrNumber())
                .distributorId(distributorId)
                .requestedBy(currentUser)
                .description(request.getDescription())
                .justification(request.getJustification())
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .items(itemsToMaps(request.getItems()))
                .estimatedTotalAmount(calculateTotal(request.getItems()))
                .build();

        PurchaseRequisition saved = prRepository.save(pr);
        return PurchaseRequisitionResponse.fromEntity(saved, resolveDistributorName(saved.getDistributorId()));
    }

    @Override
    @Transactional
    public PurchaseRequisitionResponse submitPurchaseRequisition(UUID id, User currentUser) {
        PurchaseRequisition pr = findPrById(id);
        if (pr.getStatus() != PrStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT requisitions can be submitted");
        }
        pr.setStatus(PrStatus.SUBMITTED);
        pr.setSubmittedAt(LocalDateTime.now());
        PurchaseRequisition savedSub = prRepository.save(pr);

        // Route through configurable approval thresholds
        BigDecimal totalAmount = savedSub.getEstimatedTotalAmount() != null
                ? savedSub.getEstimatedTotalAmount() : BigDecimal.ZERO;
        int requiredApprovals = approvalThresholdService.getRequiredApprovals(
                savedSub.getDistributorId(), ApprovalWorkflowType.PURCHASE_REQUISITION, totalAmount);

        approvalService.createRequest(currentUser.getId(), CreateApprovalRequestDto.builder()
                .workflowType(ApprovalWorkflowType.PURCHASE_REQUISITION)
                .entityType("PURCHASE_REQUISITION")
                .entityId(savedSub.getId())
                .entityName(savedSub.getPrNumber())
                .description("Purchase Requisition " + savedSub.getPrNumber() + " — KES " + totalAmount)
                .requiredApprovals(requiredApprovals)
                .amount(totalAmount)
                .build());

        return PurchaseRequisitionResponse.fromEntity(savedSub, resolveDistributorName(savedSub.getDistributorId()));
    }

    @Override
    @Transactional
    public PurchaseRequisitionResponse approvePurchaseRequisition(UUID id, User currentUser) {
        PurchaseRequisition pr = findPrById(id);
        if (pr.getStatus() != PrStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED requisitions can be approved");
        }
        pr.setStatus(PrStatus.APPROVED);
        pr.setApprovedAt(LocalDateTime.now());
        pr.setApprovedBy(currentUser);
        PurchaseRequisition savedApp = prRepository.save(pr);
        return PurchaseRequisitionResponse.fromEntity(savedApp, resolveDistributorName(savedApp.getDistributorId()));
    }

    @Override
    @Transactional
    public PurchaseRequisitionResponse rejectPurchaseRequisition(UUID id, String reason, User currentUser) {
        PurchaseRequisition pr = findPrById(id);
        if (pr.getStatus() != PrStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED requisitions can be rejected");
        }
        pr.setStatus(PrStatus.REJECTED);
        pr.setRejectionReason(reason);
        PurchaseRequisition savedRej = prRepository.save(pr);
        return PurchaseRequisitionResponse.fromEntity(savedRej, resolveDistributorName(savedRej.getDistributorId()));
    }

    @Override
    @Transactional
    public PurchaseRequisitionResponse cancelPurchaseRequisition(UUID id, User currentUser) {
        PurchaseRequisition pr = findPrById(id);
        if (pr.getStatus() == PrStatus.CONVERTED_TO_PO) {
            throw new IllegalStateException("Cannot cancel a requisition that has been converted to a PO");
        }
        pr.setStatus(PrStatus.CANCELLED);
        PurchaseRequisition savedCan = prRepository.save(pr);
        return PurchaseRequisitionResponse.fromEntity(savedCan, resolveDistributorName(savedCan.getDistributorId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> getPurchaseOrders(UUID distributorId, PoStatus status, UUID supplierId, Pageable pageable) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return poRepository.findWithFilters(effectiveDistributorId, status, supplierId, pageable)
                .map(po -> PurchaseOrderResponse.fromEntity(po, resolveDistributorName(po.getDistributorId())));
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponse getPurchaseOrderById(UUID id) {
        PurchaseOrder po = findPoById(id);
        return PurchaseOrderResponse.fromEntity(po, resolveDistributorName(po.getDistributorId()));
    }

    @Override
    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request, User currentUser) {
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", request.getSupplierId().toString()));

        UUID distributorId = request.getDistributorId() != null
                ? request.getDistributorId()
                : securityUtils.getDistributorIdForFiltering();

        PurchaseOrder po = PurchaseOrder.builder()
                .poNumber(generatePoNumber())
                .supplier(supplier)
                .distributorId(distributorId)
                .items(itemsToMaps(request.getItems()))
                .totalAmount(calculateTotal(request.getItems()))
                .deliveryAddress(request.getDeliveryAddress())
                .paymentTermsDays(request.getPaymentTermsDays() != null
                        ? request.getPaymentTermsDays() : supplier.getPaymentTermsDays())
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .notes(request.getNotes())
                .createdBy(currentUser)
                .build();

        if (request.getPurchaseRequisitionId() != null) {
            PurchaseRequisition pr = findPrById(request.getPurchaseRequisitionId());
            po.setPurchaseRequisition(pr);
        }

        PurchaseOrder savedPo = poRepository.save(po);
        return PurchaseOrderResponse.fromEntity(savedPo, resolveDistributorName(savedPo.getDistributorId()));
    }

    @Override
    @Transactional
    public PurchaseOrderResponse sendPurchaseOrder(UUID id, User currentUser) {
        PurchaseOrder po = findPoById(id);
        if (po.getStatus() != PoStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT purchase orders can be sent");
        }
        po.setStatus(PoStatus.SENT);
        po.setSentAt(LocalDateTime.now());
        PurchaseOrder sentPo = poRepository.save(po);
        return PurchaseOrderResponse.fromEntity(sentPo, resolveDistributorName(sentPo.getDistributorId()));
    }

    @Override
    @Transactional
    public PurchaseOrderResponse confirmPurchaseOrder(UUID id, User currentUser) {
        PurchaseOrder po = findPoById(id);
        if (po.getStatus() != PoStatus.SENT) {
            throw new IllegalStateException("Only SENT purchase orders can be confirmed");
        }
        po.setStatus(PoStatus.CONFIRMED);
        po.setConfirmedAt(LocalDateTime.now());
        PurchaseOrder confirmedPo = poRepository.save(po);
        return PurchaseOrderResponse.fromEntity(confirmedPo, resolveDistributorName(confirmedPo.getDistributorId()));
    }

    @Override
    @Transactional
    public PurchaseOrderResponse cancelPurchaseOrder(UUID id, User currentUser) {
        PurchaseOrder po = findPoById(id);
        if (po.getStatus() == PoStatus.RECEIVED) {
            throw new IllegalStateException("Cannot cancel a received purchase order");
        }
        po.setStatus(PoStatus.CANCELLED);
        PurchaseOrder cancelledPo = poRepository.save(po);
        return PurchaseOrderResponse.fromEntity(cancelledPo, resolveDistributorName(cancelledPo.getDistributorId()));
    }

    @Override
    @Transactional
    public PurchaseOrderResponse convertPrToPo(UUID prId, PurchaseOrderRequest request, User currentUser) {
        PurchaseRequisition pr = findPrById(prId);
        if (pr.getStatus() != PrStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED requisitions can be converted to a PO");
        }

        request.setPurchaseRequisitionId(prId);
        PurchaseOrderResponse poResponse = createPurchaseOrder(request, currentUser);

        pr.setStatus(PrStatus.CONVERTED_TO_PO);
        prRepository.save(pr);

        return poResponse;
    }

    private PurchaseRequisition findPrById(UUID id) {
        return prRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseRequisition", "id", id.toString()));
    }

    private PurchaseOrder findPoById(UUID id) {
        return poRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", id.toString()));
    }

    private String resolveDistributorName(UUID distributorId) {
        if (distributorId == null) return null;
        return distributorRepository.findById(distributorId)
                .map(d -> d.getName())
                .orElse(null);
    }
}
