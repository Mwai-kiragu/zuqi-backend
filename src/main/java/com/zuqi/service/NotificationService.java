package com.zuqi.service;

import com.zuqi.api.dto.common.PageResponse;
import com.zuqi.api.dto.notification.NotificationResponse;
import com.zuqi.domain.approval.ApprovalRequest;

import java.util.UUID;

public interface NotificationService {

    void notifyApprovers(ApprovalRequest request);

    void notifyRequester(ApprovalRequest request, String approverName);

    PageResponse<NotificationResponse> getForCurrentUser(int page, int size);

    long countUnread();

    NotificationResponse markAsRead(UUID id);

    void markAllAsRead();
}
