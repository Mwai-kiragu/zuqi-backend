package com.zuqi.service.impl;

import com.zuqi.api.dto.common.PageResponse;
import com.zuqi.api.dto.notification.NotificationResponse;
import com.zuqi.domain.approval.ApprovalRequest;
import com.zuqi.domain.approval.ApprovalStatus;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.notification.Notification;
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
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public void notifyApprovers(ApprovalRequest request) {
        try {
            UUID requesterId = request.getRequestedById();
            User requester = userRepository.findById(requesterId).orElse(null);
            if (requester == null || requester.getDistributorId() == null) return;

            List<User> approvers = userRepository.findActiveApproversByDistributorId(requester.getDistributorId());
            if (approvers.isEmpty()) {
                log.info("No approvers found for distributor {} — skipping in-app notifications", requester.getDistributorId());
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
