package com.zuqi.service.impl;

import com.zuqi.api.dto.approval.ApprovalActionResponse;
import com.zuqi.api.dto.approval.ApprovalRequestResponse;
import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.api.dto.approval.ProcessApprovalRequest;
import com.zuqi.api.dto.common.PageResponse;
import com.zuqi.config.EmailConfig;
import com.zuqi.domain.approval.ApprovalAction;
import com.zuqi.domain.approval.ApprovalDecision;
import com.zuqi.domain.approval.ApprovalRequest;
import com.zuqi.domain.approval.ApprovalStatus;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.StockMovement;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.procurement.PrStatus;
import com.zuqi.repository.ApprovalActionRepository;
import com.zuqi.repository.ApprovalRequestRepository;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.domain.invoice.InvoiceStatus;
import com.zuqi.repository.InvoiceRepository;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.PosShiftRepository;
import com.zuqi.repository.PriceListRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.PromotionRepository;
import com.zuqi.repository.PurchaseRequisitionRepository;
import com.zuqi.repository.StockMovementRepository;
import com.zuqi.repository.StockRepository;
import com.zuqi.repository.StockTransferRepository;
import com.zuqi.repository.SupplierRepository;
import com.zuqi.repository.WarehouseRepository;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.repository.UserRepository;
import com.zuqi.service.ActivityLogService;
import com.zuqi.service.ApprovalService;
import com.zuqi.service.ApprovalThresholdService;
import com.zuqi.service.ApprovalWorkflowConfigService;
import com.zuqi.service.EmailService;
import com.zuqi.service.GlAutoPostingService;
import com.zuqi.service.InvoiceService;
import com.zuqi.service.NotificationService;
import com.zuqi.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalActionRepository approvalActionRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final PriceListRepository priceListRepository;
    private final PromotionRepository promotionRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockRepository stockRepository;
    private final StockTransferRepository stockTransferRepository;
    private final WarehouseRepository warehouseRepository;
    private final PosShiftRepository posShiftRepository;
    private final PurchaseRequisitionRepository purchaseRequisitionRepository;
    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final ActivityLogService activityLogService;
    private final EmailService emailService;
    private final EmailConfig emailConfig;
    private final ApprovalWorkflowConfigService approvalWorkflowConfigService;
    private final GlAutoPostingService glAutoPostingService;
    private final SecurityUtils securityUtils;

    @Lazy @Autowired
    private InvoiceService invoiceService;

    @Lazy @Autowired
    private NotificationService notificationService;

    @Lazy @Autowired
    private ApprovalThresholdService approvalThresholdService;

    private final AtomicLong sequenceCounter = new AtomicLong(0);

    @Override
    @Transactional
    public ApprovalRequestResponse createRequest(UUID requesterId, CreateApprovalRequestDto dto) {
        log.info("Creating approval request type={} entity={} requester={}",
                dto.getWorkflowType(), dto.getEntityType(), requesterId);

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", requesterId.toString()));

        int requiredApprovals = dto.getRequiredApprovals() != null ? dto.getRequiredApprovals() : 1;

        // Override with the merchant's configured workflow levels if available
        if (dto.getWorkflowType() != null && requester.getDistributorId() != null) {
            int configuredLevels = approvalWorkflowConfigService.countActiveLevels(
                    requester.getDistributorId(), dto.getWorkflowType());
            if (configuredLevels > 0) {
                requiredApprovals = configuredLevels;
            }
        }

        ApprovalRequest request = ApprovalRequest.builder()
                .requestNumber(generateRequestNumber(dto.getWorkflowType()))
                .workflowType(dto.getWorkflowType())
                .entityType(dto.getEntityType())
                .entityId(dto.getEntityId())
                .entityName(dto.getEntityName())
                .distributorId(requester.getDistributorId())
                .requestedById(requesterId)
                .requestedByEmail(requester.getEmail())
                .requestedByName(requester.getFirstName() + " " + requester.getLastName())
                .description(dto.getDescription())
                .currentValues(dto.getCurrentValues() != null ? dto.getCurrentValues() : new HashMap<>())
                .requestedValues(dto.getRequestedValues() != null ? dto.getRequestedValues() : new HashMap<>())
                .requiredApprovals(requiredApprovals)
                .amount(dto.getAmount())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        ApprovalRequest saved = approvalRequestRepository.save(request);

        activityLogService.log(requesterId, requester.getEmail(),
                requester.getFirstName() + " " + requester.getLastName(),
                ActivityAction.CREATE, "APPROVAL_REQUEST", saved.getId(),
                saved.getRequestNumber(), "APPROVAL",
                "Created approval request: " + dto.getWorkflowType().name() + " for " + dto.getEntityName());

        notifyApproversAsync(saved);

        log.info("Approval request created: {}", saved.getRequestNumber());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ApprovalRequestResponse processRequest(UUID requestId, UUID approverId, ProcessApprovalRequest dto) {
        log.info("Processing approval request={} approver={} decision={}",
                requestId, approverId, dto.getDecision());

        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", "id", requestId.toString()));

        if (!request.isPending()) {
            throw new ValidationException("Approval request is not in PENDING status");
        }

        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            request.setStatus(ApprovalStatus.EXPIRED);
            approvalRequestRepository.save(request);
            throw new ValidationException("Approval request has expired");
        }

        if (approvalActionRepository.existsByApprovalRequestIdAndApproverId(requestId, approverId)) {
            throw new ValidationException("You have already acted on this request");
        }

        if (request.getRequestedById().equals(approverId)) {
            throw new ValidationException("The maker cannot approve their own request");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", approverId.toString()));

        ApprovalAction action = ApprovalAction.builder()
                .approvalRequest(request)
                .approverId(approverId)
                .approverEmail(approver.getEmail())
                .approverName(approver.getFirstName() + " " + approver.getLastName())
                .decision(dto.getDecision())
                .approvalLevel(request.getReceivedApprovals() + 1)
                .comments(dto.getComments())
                .actionAt(LocalDateTime.now())
                .build();

        approvalActionRepository.save(action);
        request.getActions().add(action);

        if (dto.getDecision() == ApprovalDecision.APPROVED) {
            request.setReceivedApprovals(request.getReceivedApprovals() + 1);
            // Re-check against current threshold in case requiredApprovals was frozen with a
            // stale/misconfigured value. Use whichever is lower (frozen vs current threshold).
            boolean complete = request.isFullyApproved();
            if (!complete && request.getDistributorId() != null
                    && request.getAmount() != null && request.getWorkflowType() != null) {
                try {
                    int currentRequired = approvalThresholdService.getRequiredApprovals(
                            request.getDistributorId(), request.getWorkflowType(), request.getAmount());
                    if (request.getReceivedApprovals() >= currentRequired) {
                        complete = true;
                        // Align stored value so UI shows correct numbers
                        request.setRequiredApprovals(currentRequired);
                        log.info("Approval request {} completed via updated threshold ({} approvals)",
                                request.getRequestNumber(), currentRequired);
                    }
                } catch (Exception e) {
                    log.warn("Could not re-check threshold for {}: {}", request.getRequestNumber(), e.getMessage());
                }
            }
            if (complete) {
                request.setStatus(ApprovalStatus.APPROVED);
                request.setApprovedAt(LocalDateTime.now());
                log.info("Approval request {} fully approved", request.getRequestNumber());
            }
        } else {
            request.setStatus(ApprovalStatus.REJECTED);
            request.setRejectionReason(dto.getComments());
            request.setRejectedAt(LocalDateTime.now());
            log.info("Approval request {} rejected by {}", request.getRequestNumber(), approver.getEmail());
        }

        ApprovalRequest updated = approvalRequestRepository.save(request);

        ActivityAction auditAction = dto.getDecision() == ApprovalDecision.APPROVED
                ? ActivityAction.APPROVE : ActivityAction.REJECT;
        activityLogService.log(approverId, approver.getEmail(),
                approver.getFirstName() + " " + approver.getLastName(),
                auditAction, "APPROVAL_REQUEST", requestId,
                request.getRequestNumber(), "APPROVAL",
                dto.getDecision().name() + " approval request: " + request.getRequestNumber());

        // Only notify the requester when a final decision has been reached (not on intermediate approvals)
        if (updated.getStatus() == ApprovalStatus.APPROVED || updated.getStatus() == ApprovalStatus.REJECTED) {
            notifyRequesterAsync(updated, approver);
        }

        if (updated.getStatus() == ApprovalStatus.APPROVED) {
            updateEntityApprovalStatus(updated, "APPROVED", approverId);
        } else if (updated.getStatus() == ApprovalStatus.REJECTED) {
            updateEntityApprovalStatus(updated, "REJECTED", approverId);
        }

        return toResponse(updated);
    }

    private void updateEntityApprovalStatus(ApprovalRequest request, String status, UUID approverId) {
        if (request.getEntityId() == null) return;
        UUID entityId = request.getEntityId();
        switch (request.getEntityType()) {
            case "CUSTOMER"       -> customerRepository.updateApprovalStatus(entityId, status);
            case "SUPPLIER"       -> supplierRepository.updateApprovalStatus(entityId, status);
            case "PRODUCT"        -> {
                if ("APPROVED".equals(status)) {
                    productRepository.approveAndActivate(entityId);
                    productRepository.findById(entityId).ifPresent(product -> {
                        if (product.getDistributor() == null) return;
                        UUID distributorId = product.getDistributor().getId();

                        // Prefer the HQ-branch warehouse; fall back to the first warehouse created
                        List<Warehouse> hqWarehouses = warehouseRepository
                                .findByDistributorIdAndBranchHeadquartersTrueAndActiveTrue(distributorId);
                        Warehouse defaultWarehouse = hqWarehouses.isEmpty()
                                ? warehouseRepository
                                        .findFirstByDistributorIdAndActiveTrueOrderByCreatedAtAsc(distributorId)
                                        .orElse(null)
                                : hqWarehouses.get(0);

                        if (defaultWarehouse == null) return;

                        if (product.isHasVariants()) {
                            // Parent template: create stock for each variant child instead of the parent
                            List<com.zuqi.domain.product.Product> variants =
                                    productRepository.findByParentProductId(entityId);
                            for (com.zuqi.domain.product.Product variant : variants) {
                                boolean exists = stockRepository.existsByWarehouseIdAndProductId(
                                        defaultWarehouse.getId(), variant.getId());
                                if (!exists) {
                                    stockRepository.save(Stock.builder()
                                            .warehouse(defaultWarehouse)
                                            .product(variant)
                                            .quantity(java.math.BigDecimal.ZERO)
                                            .reservedQuantity(java.math.BigDecimal.ZERO)
                                            .build());
                                    log.info("Created zero-stock entry for variant {} in warehouse {}",
                                            variant.getName(), defaultWarehouse.getName());
                                }
                            }
                        } else {
                            // Standalone product: create stock for the product itself
                            boolean exists = stockRepository.existsByWarehouseIdAndProductId(
                                    defaultWarehouse.getId(), product.getId());
                            if (!exists) {
                                stockRepository.save(Stock.builder()
                                        .warehouse(defaultWarehouse)
                                        .product(product)
                                        .quantity(java.math.BigDecimal.ZERO)
                                        .reservedQuantity(java.math.BigDecimal.ZERO)
                                        .build());
                                log.info("Created zero-stock entry for approved product {} in warehouse {}",
                                        product.getName(), defaultWarehouse.getName());
                            }
                        }
                    });
                } else {
                    productRepository.updateApprovalStatus(entityId, status);
                }
            }
            case "PRICE_LIST"     -> priceListRepository.updateApprovalStatus(entityId, status);
            case "PROMOTION"      -> promotionRepository.updateApprovalStatus(entityId, status);
            case "STOCK_MOVEMENT" -> {
                stockMovementRepository.updateApprovalStatus(entityId, status);
                if ("APPROVED".equals(status)) {
                    applyApprovedStockMovement(entityId);
                }
            }
            case "POS_SHIFT" -> {
                String reconcileStatus = "APPROVED".equals(status) ? "APPROVED" : "REJECTED";
                posShiftRepository.updateReconciliationStatus(entityId, reconcileStatus,
                        approverId, java.time.LocalDateTime.now());
            }
            case "PURCHASE_REQUISITION" -> {
                PrStatus prStatus = "APPROVED".equals(status) ? PrStatus.APPROVED : PrStatus.REJECTED;
                purchaseRequisitionRepository.updateStatus(entityId, prStatus);
            }
            case "ORDER" -> {
                orderRepository.findById(entityId).ifPresent(order -> {
                    if ("APPROVED".equals(status)) {
                        order.setApprovalStatus("APPROVED");
                        order.setStatus(OrderStatus.CONFIRMED);
                        orderRepository.save(order);
                        try {
                            invoiceService.createInvoiceFromOrder(order);
                        } catch (Exception e) {
                            log.warn("Failed to create invoice for approved order {}: {}", order.getOrderNumber(), e.getMessage());
                        }
                    } else {
                        order.setApprovalStatus("REJECTED");
                        order.setStatus(OrderStatus.CANCELLED);
                        orderRepository.save(order);
                    }
                });
            }
            case "INVOICE" -> invoiceRepository.findById(entityId).ifPresent(invoice -> {
                if ("APPROVED".equals(status)) {
                    invoice.setStatus(InvoiceStatus.UNPAID);
                    invoiceRepository.save(invoice);
                    try { glAutoPostingService.postInvoiceCreated(invoice); } catch (Exception e) {
                        log.warn("GL posting failed for approved invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage());
                    }
                } else {
                    invoice.setStatus(InvoiceStatus.CANCELLED);
                    invoiceRepository.save(invoice);
                }
            });
            case "STOCK_TRANSFER" -> stockTransferRepository.updateApprovalStatus(entityId, status);
            default -> { /* no-op for other entity types */ }
        }
    }

    private void applyApprovedStockMovement(UUID movementId) {
        try {
            StockMovement movement = stockMovementRepository.findById(movementId).orElse(null);
            if (movement == null || !"APPROVED".equals(movement.getApprovalStatus())) return;

            Stock stock = stockRepository.findByWarehouseIdAndProductId(
                    movement.getWarehouse().getId(), movement.getProduct().getId())
                    .orElseGet(() -> {
                        Stock s = new Stock();
                        s.setWarehouse(movement.getWarehouse());
                        s.setProduct(movement.getProduct());
                        s.setQuantity(java.math.BigDecimal.ZERO);
                        s.setReservedQuantity(java.math.BigDecimal.ZERO);
                        return s;
                    });

            java.math.BigDecimal newQty;
            switch (movement.getMovementType()) {
                case IN         -> newQty = stock.getQuantity().add(movement.getQuantity());
                case ADJUSTMENT -> newQty = movement.getQuantity();
                case OUT, TRANSFER -> {
                    newQty = stock.getQuantity().subtract(movement.getQuantity());
                    if (newQty.compareTo(java.math.BigDecimal.ZERO) < 0) {
                        log.warn("Approved stock movement {} would result in negative stock — skipping apply", movementId);
                        return;
                    }
                }
                default -> { return; }
            }
            stock.setQuantity(newQty);
            stock.setLastStockCheck(java.time.LocalDateTime.now());
            stockRepository.save(stock);
            log.info("Applied approved stock movement {} — new balance: {}", movementId, newQty);
        } catch (Exception e) {
            log.warn("Could not apply approved stock movement {}: {}", movementId, e.getMessage());
        }
    }

    @Override
    @Transactional
    public ApprovalRequestResponse cancelRequest(UUID requestId, UUID requesterId) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", "id", requestId.toString()));

        if (!request.isPending()) {
            throw new ValidationException("Only PENDING requests can be cancelled");
        }

        if (!request.getRequestedById().equals(requesterId)) {
            throw new ValidationException("Only the requester can cancel the request");
        }

        request.setStatus(ApprovalStatus.CANCELLED);
        request.setCancelledAt(LocalDateTime.now());
        ApprovalRequest saved = approvalRequestRepository.save(request);

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", requesterId.toString()));

        activityLogService.log(requesterId, requester.getEmail(),
                requester.getFirstName() + " " + requester.getLastName(),
                ActivityAction.CANCEL, "APPROVAL_REQUEST", requestId,
                request.getRequestNumber(), "APPROVAL",
                "Cancelled approval request: " + request.getRequestNumber());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalRequestResponse getById(UUID requestId) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", "id", requestId.toString()));
        return toResponse(request);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApprovalRequestResponse> getAll(ApprovalStatus status, ApprovalWorkflowType workflowType,
                                                         String entityType, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        UUID distributorId = securityUtils.getCurrentUserDistributorId(); // null for SUPER_ADMIN → sees all
        Page<ApprovalRequest> result = approvalRequestRepository.findWithFilters(status, workflowType, entityType, distributorId, pageable);
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApprovalRequestResponse> getMyRequests(UUID requesterId, ApprovalStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        Page<ApprovalRequest> result = distributorId != null
                ? approvalRequestRepository.findByRequestedByIdAndDistributorId(requesterId, distributorId, pageable)
                : approvalRequestRepository.findByRequestedById(requesterId, pageable);
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApprovalRequestResponse> getPendingForApprover(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        Page<ApprovalRequest> result = distributorId != null
                ? approvalRequestRepository.findByStatusAndDistributorId(ApprovalStatus.PENDING, distributorId, pageable)
                : approvalRequestRepository.findByStatus(ApprovalStatus.PENDING, pageable); // SUPER_ADMIN sees all
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending() {
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        return distributorId != null
                ? approvalRequestRepository.countByStatusAndDistributorId(ApprovalStatus.PENDING, distributorId)
                : approvalRequestRepository.countByStatus(ApprovalStatus.PENDING); // SUPER_ADMIN sees all
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalRequestResponse> getByEntity(String entityType, UUID entityId, ApprovalStatus status) {
        return approvalRequestRepository.findByEntityTypeAndEntityIdAndStatus(entityType, entityId, status)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // Run every hour
    public void expireStaleRequests() {
        List<ApprovalRequest> expired = approvalRequestRepository.findExpiredRequests(LocalDateTime.now());
        if (expired.isEmpty()) return;

        log.info("Expiring {} stale approval requests", expired.size());
        expired.forEach(r -> {
            r.setStatus(ApprovalStatus.EXPIRED);
            activityLogService.log(null, "system", "System",
                    ActivityAction.CANCEL, "APPROVAL_REQUEST", r.getId(),
                    r.getRequestNumber(), "APPROVAL",
                    "Auto-expired approval request: " + r.getRequestNumber());
        });
        approvalRequestRepository.saveAll(expired);
    }

    private void notifyApproversAsync(ApprovalRequest request) {
        try {
            notificationService.notifyApprovers(request);
        } catch (Exception e) {
            log.error("Failed to notify approvers for request: {}", request.getRequestNumber(), e);
        }
    }

    private void notifyRequesterAsync(ApprovalRequest request, User approver) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("requestNumber", request.getRequestNumber());
            vars.put("workflowType", formatLabel(request.getWorkflowType().name()));
            vars.put("entityName", request.getEntityName());
            vars.put("status", request.getStatus().name());
            vars.put("approverName", approver.getFirstName() + " " + approver.getLastName());
            vars.put("rejectionReason", request.getRejectionReason());
            vars.put("companyName", emailConfig.getFromName());

            emailService.sendTemplatedEmail(
                    request.getRequestedByEmail(),
                    "Approval Request " + request.getStatus().name() + " - " + request.getRequestNumber(),
                    "approval-decision",
                    vars
            );

            notificationService.notifyRequester(request, approver.getFirstName() + " " + approver.getLastName());
        } catch (Exception e) {
            log.error("Failed to notify requester for request: {}", request.getRequestNumber(), e);
        }
    }

    private String generateRequestNumber(ApprovalWorkflowType type) {
        String prefix = switch (type) {
            case CREDIT_LIMIT_CHANGE -> "CLM";
            case PAYMENT_TERMS_CHANGE -> "PTM";
            case BANK_DETAILS_UPDATE -> "BDU";
            case SUPPLIER_CREATION -> "SUP";
            case SUPPLIER_BANK_DETAILS_UPDATE -> "SBD";
            case PRODUCT_PRICE_EDIT -> "PRE";
            case PRODUCT_COST_EDIT -> "PCE";
            case DISCOUNT_APPROVAL -> "DIS";
            case STOCK_ADJUSTMENT -> "STK";
            case STOCK_WRITE_OFF -> "SWO";
            case PURCHASE_REQUISITION -> "PRQ";
            case PURCHASE_ORDER -> "POA";
            case PAYMENT_APPROVAL -> "PAY";
            case CREDIT_NOTE -> "CRN";
            case JOURNAL_ENTRY -> "JNL";
            default -> "APR";
        };
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        return prefix + "-" + timestamp;
    }

    private String formatLabel(String name) {
        return name.replace("_", " ");
    }

    private ApprovalRequestResponse toResponse(ApprovalRequest request) {
        List<ApprovalActionResponse> actions = request.getActions().stream()
                .map(a -> ApprovalActionResponse.builder()
                        .id(a.getId())
                        .approverId(a.getApproverId())
                        .approverEmail(a.getApproverEmail())
                        .approverName(a.getApproverName())
                        .decision(a.getDecision())
                        .approvalLevel(a.getApprovalLevel())
                        .comments(a.getComments())
                        .actionAt(a.getActionAt())
                        .build())
                .toList();

        return ApprovalRequestResponse.builder()
                .id(request.getId())
                .requestNumber(request.getRequestNumber())
                .workflowType(request.getWorkflowType())
                .workflowTypeLabel(formatLabel(request.getWorkflowType().name()))
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .entityName(request.getEntityName())
                .requestedById(request.getRequestedById())
                .requestedByEmail(request.getRequestedByEmail())
                .requestedByName(request.getRequestedByName())
                .status(request.getStatus())
                .description(request.getDescription())
                .currentValues(request.getCurrentValues())
                .requestedValues(request.getRequestedValues())
                .requiredApprovals(request.getRequiredApprovals())
                .receivedApprovals(request.getReceivedApprovals())
                .amount(request.getAmount())
                .rejectionReason(request.getRejectionReason())
                .approvedAt(request.getApprovedAt())
                .rejectedAt(request.getRejectedAt())
                .expiresAt(request.getExpiresAt())
                .actions(actions)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }

    private PageResponse<ApprovalRequestResponse> toPageResponse(Page<ApprovalRequest> page) {
        return PageResponse.<ApprovalRequestResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .number(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }
}
