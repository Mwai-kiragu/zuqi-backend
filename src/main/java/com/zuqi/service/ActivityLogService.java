package com.zuqi.service;

import com.zuqi.api.dto.audit.ActivityLogResponse;
import com.zuqi.api.dto.common.PageResponse;
import com.zuqi.domain.audit.ActivityAction;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public interface ActivityLogService {

    void log(UUID userId, String userEmail, String userName,
             ActivityAction action, String entityType, UUID entityId,
             String entityName, String module, String description,
             Map<String, Object> oldValues, Map<String, Object> newValues);

    void log(UUID userId, String userEmail, String userName,
             ActivityAction action, String entityType, UUID entityId,
             String entityName, String module, String description);

    void logFailure(UUID userId, String userEmail, ActivityAction action,
                    String entityType, String module, String description, String errorMessage);

    ActivityLogResponse getById(UUID id);

    PageResponse<ActivityLogResponse> getAll(UUID userId, ActivityAction action,
                                              String entityType, String module,
                                              LocalDateTime from, LocalDateTime to,
                                              Boolean success, int page, int size);

    PageResponse<ActivityLogResponse> getForEntity(String entityType, UUID entityId, int page, int size);

    PageResponse<ActivityLogResponse> getForUser(UUID userId, int page, int size);
}
