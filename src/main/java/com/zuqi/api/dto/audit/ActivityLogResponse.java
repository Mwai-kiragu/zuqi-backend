package com.zuqi.api.dto.audit;

import com.zuqi.domain.audit.ActivityAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogResponse {
    private UUID id;
    private UUID userId;
    private String userEmail;
    private String userName;
    private ActivityAction action;
    private String actionLabel;
    private String entityType;
    private UUID entityId;
    private String entityName;
    private String module;
    private String description;
    private Map<String, Object> oldValues;
    private Map<String, Object> newValues;
    private String ipAddress;
    private boolean success;
    private String errorMessage;
    private LocalDateTime createdAt;
}
