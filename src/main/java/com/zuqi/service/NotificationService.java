package com.zuqi.service;

import com.zuqi.api.dto.common.PageResponse;
import com.zuqi.api.dto.notification.NotificationResponse;
import com.zuqi.domain.approval.ApprovalRequest;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.procurement.PurchaseOrder;

import java.util.UUID;

public interface NotificationService {

    void notifyApprovers(ApprovalRequest request);

    void notifyLowStock(Stock stock);

    void notifyRequester(ApprovalRequest request, String approverName);

    void notifyPoSupplierResponse(PurchaseOrder po);

    PageResponse<NotificationResponse> getForCurrentUser(int page, int size);

    long countUnread();

    NotificationResponse markAsRead(UUID id);

    void markAllAsRead();
}
