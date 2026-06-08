package com.zuqi.service.impl;

import com.zuqi.api.dto.common.PageResponse;
import com.zuqi.api.dto.notification.NotificationResponse;
import com.zuqi.config.EmailConfig;
import com.zuqi.domain.approval.ApprovalRequest;
import com.zuqi.domain.approval.ApprovalStatus;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.notification.Notification;
import com.zuqi.domain.procurement.PurchaseOrder;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.NotificationRepository;
import com.zuqi.repository.UserRepository;
import com.zuqi.service.EmailService;
import com.zuqi.service.NotificationService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final EmailConfig emailConfig;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public void notifyApprovers(ApprovalRequest request) {
        try {
            UUID requesterId = request.getRequestedById();

            // Prefer the distributor stored on the request (explicitly set at creation time).
            // Fall back to the requester's own distributorId for legacy paths.
            UUID distributorId = request.getDistributorId();
            if (distributorId == null) {
                User requester = userRepository.findById(requesterId).orElse(null);
                if (requester == null) return;
                distributorId = requester.getDistributorId();
            }
            if (distributorId == null) {
                log.info("No distributor context for approval request {} — skipping notifications", request.getRequestNumber());
                return;
            }

            List<User> approvers = userRepository.findActiveApproversByDistributorId(distributorId);
            if (approvers.isEmpty()) {
                log.info("No approvers found for distributor {} — skipping in-app notifications", distributorId);
                return;
            }

            String title = "Approval needed: " + request.getEntityName();
            String message = request.getRequestedByName() + " submitted " + formatLabel(request.getEntityType())
                    + " \"" + request.getEntityName() + "\" for approval.";

            List<Notification> notifications = approvers.stream()
                    .filter(a -> !a.getId().equals(requesterId))
                    .map(approver -> Notification.builder()
                            .userId(approver.getId())
                            .type("APPROVAL_REQUESTED")
                            .title(title)
                            .message(message)
                            .entityType(request.getEntityType())
                            .entityId(request.getEntityId())
                            .entityName(request.getEntityName())
                            .approvalRequestId(request.getId())
                            .build())
                    .toList();

            notificationRepository.saveAll(notifications);
            log.info("Sent APPROVAL_REQUESTED in-app notification to {} approvers for request {}",
                    notifications.size(), request.getRequestNumber());

            // Send email to each approver
            String expiresAt = request.getExpiresAt() != null
                    ? request.getExpiresAt().toLocalDate().toString() : "N/A";
            approvers.stream()
                    .filter(a -> !a.getId().equals(requesterId))
                    .filter(a -> a.getEmail() != null && !a.getEmail().isBlank())
                    .forEach(approver -> {
                        try {
                            emailService.sendTemplatedEmail(
                                    approver.getEmail(),
                                    "Approval Required: " + request.getEntityName() + " [" + request.getRequestNumber() + "]",
                                    "email/approval-request",
                                    Map.of(
                                            "requestNumber", request.getRequestNumber(),
                                            "workflowType", formatLabel(request.getWorkflowType().name()),
                                            "entityName", request.getEntityName(),
                                            "requesterName", request.getRequestedByName() != null ? request.getRequestedByName() : "A team member",
                                            "description", request.getDescription() != null ? request.getDescription() : "",
                                            "expiresAt", expiresAt,
                                            "companyName", emailConfig.getFromName()
                                    ));
                        } catch (Exception e) {
                            log.warn("Failed to send approval request email to {}: {}", approver.getEmail(), e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to send in-app notifications to approvers for request {}: {}",
                    request.getRequestNumber(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void notifyRequester(ApprovalRequest request, String approverName) {
        try {
            boolean approved = request.getStatus() == ApprovalStatus.APPROVED;
            String type = approved ? "APPROVAL_APPROVED" : "APPROVAL_REJECTED";
            String action = approved ? "approved" : "rejected";
            String title = formatLabel(request.getEntityType()) + " \"" + request.getEntityName() + "\" " + action;
            String message = approverName + " has " + action + " your " + formatLabel(request.getEntityType())
                    + " request for \"" + request.getEntityName() + "\".";
            if (!approved && request.getRejectionReason() != null) {
                message += " Reason: " + request.getRejectionReason();
            }

            Notification notification = Notification.builder()
                    .userId(request.getRequestedById())
                    .type(type)
                    .title(title)
                    .message(message)
                    .entityType(request.getEntityType())
                    .entityId(request.getEntityId())
                    .entityName(request.getEntityName())
                    .approvalRequestId(request.getId())
                    .build();

            notificationRepository.save(notification);
            log.info("Sent {} in-app notification to requester {} for request {}",
                    type, request.getRequestedById(), request.getRequestNumber());
        } catch (Exception e) {
            log.error("Failed to send in-app notification to requester for request {}: {}",
                    request.getRequestNumber(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void notifyLowStock(Stock stock) {
        try {
            if (stock.getWarehouse() == null || stock.getWarehouse().getDistributor() == null) return;
            UUID distributorId = stock.getWarehouse().getDistributor().getId();

            List<User> recipients = userRepository.findWarehouseManagersByDistributorId(distributorId);
            if (recipients.isEmpty()) {
                log.info("No warehouse managers found for distributor {} — skipping low stock notification", distributorId);
                return;
            }

            String productName = stock.getProduct() != null ? stock.getProduct().getName() : "Unknown Product";
            String warehouseName = stock.getWarehouse().getName();
            String title = "Low Stock Alert: " + productName;
            String message = "Stock for \"" + productName + "\" in " + warehouseName
                    + " has dropped to " + stock.getQuantity().stripTrailingZeros().toPlainString()
                    + " units (reorder level: " + stock.getReorderLevel().stripTrailingZeros().toPlainString() + ").";

            List<Notification> notifications = recipients.stream()
                    .map(user -> Notification.builder()
                            .userId(user.getId())
                            .type("LOW_STOCK")
                            .title(title)
                            .message(message)
                            .entityType("STOCK")
                            .entityId(stock.getId())
                            .entityName(productName)
                            .build())
                    .toList();
            notificationRepository.saveAll(notifications);

            // Send email to each recipient
            recipients.stream()
                    .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                    .forEach(user -> {
                        try {
                            emailService.sendTemplatedEmail(
                                    user.getEmail(),
                                    "Low Stock Alert: " + productName,
                                    "email/low-stock-alert",
                                    Map.of(
                                            "recipientName", user.getFirstName() + " " + user.getLastName(),
                                            "productName", productName,
                                            "warehouseName", warehouseName,
                                            "currentStock", stock.getQuantity().stripTrailingZeros().toPlainString(),
                                            "reorderLevel", stock.getReorderLevel().stripTrailingZeros().toPlainString()
                                    ));
                        } catch (Exception e) {
                            log.warn("Failed to send low stock email to {}: {}", user.getEmail(), e.getMessage());
                        }
                    });

            log.info("Sent LOW_STOCK notification to {} recipients for product {} in {}",
                    notifications.size(), productName, warehouseName);
        } catch (Exception e) {
            log.error("Failed to send low stock notification for stock {}: {}", stock.getId(), e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getForCurrentUser(int page, int size) {
        UUID userId = securityUtils.getCurrentUserId();
        Page<Notification> result = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        return PageResponse.<NotificationResponse>builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .size(result.getSize())
                .number(result.getNumber())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread() {
        UUID userId = securityUtils.getCurrentUserId();
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID id) {
        UUID userId = securityUtils.getCurrentUserId();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id.toString()));
        if (!notification.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Notification", "id", id.toString());
        }
        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public void markAllAsRead() {
        UUID userId = securityUtils.getCurrentUserId();
        notificationRepository.markAllReadByUserId(userId);
    }

    @Override
    @Transactional
    public void notifyPoSupplierResponse(PurchaseOrder po) {
        try {
            User creator = po.getCreatedBy();
            if (creator == null) {
                log.warn("PO {} has no createdBy user — skipping supplier response notification", po.getPoNumber());
                return;
            }

            String supplierName = po.getSupplier() != null ? po.getSupplier().getName() : "Supplier";
            String action = po.getSupplierResponse();
            String title;
            String message;

            if ("CONFIRM".equals(action)) {
                title = "Supplier confirmed: " + po.getPoNumber();
                message = supplierName + " has confirmed availability for " + po.getPoNumber() + ".";
            } else if ("DECLINE".equals(action)) {
                title = "Supplier declined: " + po.getPoNumber();
                message = supplierName + " cannot fulfill " + po.getPoNumber() + ". Please review and take action.";
            } else {
                title = "Partial fulfillment: " + po.getPoNumber();
                message = supplierName + " indicated partial availability for " + po.getPoNumber() + ".";
                if (po.getSupplierNotes() != null && !po.getSupplierNotes().isBlank()) {
                    message += " Notes: " + po.getSupplierNotes();
                }
            }

            notificationRepository.save(Notification.builder()
                    .userId(creator.getId())
                    .type("PO_SUPPLIER_RESPONSE")
                    .title(title)
                    .message(message)
                    .entityType("PURCHASE_ORDER")
                    .entityId(po.getId())
                    .entityName(po.getPoNumber())
                    .build());

            log.info("Sent PO_SUPPLIER_RESPONSE notification to user {} for PO {}", creator.getId(), po.getPoNumber());
        } catch (Exception e) {
            log.error("Failed to send PO supplier response notification for PO {}: {}", po.getPoNumber(), e.getMessage(), e);
        }
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .entityType(n.getEntityType())
                .entityId(n.getEntityId())
                .entityName(n.getEntityName())
                .approvalRequestId(n.getApprovalRequestId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private String formatLabel(String type) {
        if (type == null) return "";
        return switch (type) {
            case "PURCHASE_REQUISITION" -> "Purchase Requisition";
            case "STOCK_MOVEMENT" -> "Stock Movement";
            case "PRICE_LIST" -> "Price List";
            default -> type.charAt(0) + type.substring(1).toLowerCase();
        };
    }
}
