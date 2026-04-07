package com.zuqi.api.dto.notification;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private String type;
    private String title;
    private String message;
    private String entityType;
    private UUID entityId;
    private String entityName;
    private UUID approvalRequestId;
    private boolean read;
    private LocalDateTime createdAt;
}
