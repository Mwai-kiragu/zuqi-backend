package com.zuqi.service.impl;

import com.zuqi.api.dto.audit.ActivityLogResponse;
import com.zuqi.api.dto.common.PageResponse;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.domain.audit.ActivityLog;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.ActivityLogRepository;
import com.zuqi.repository.ActivityLogSpecification;
import com.zuqi.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityLogServiceImpl implements ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, String userEmail, String userName,
                    ActivityAction action, String entityType, UUID entityId,
                    String entityName, String module, String description,
                    Map<String, Object> oldValues, Map<String, Object> newValues) {
        try {
            ActivityLog logEntry = ActivityLog.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .userName(userName)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .entityName(entityName)
                    .module(module)
                    .description(description)
                    .oldValues(oldValues != null ? oldValues : Map.of())
                    .newValues(newValues != null ? newValues : Map.of())
                    .success(true)
                    .build();

            activityLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to save activity log for user: {} action: {} entity: {}",
                    userEmail, action, entityType, e);
        }
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, String userEmail, String userName,
                    ActivityAction action, String entityType, UUID entityId,
                    String entityName, String module, String description) {
        log(userId, userEmail, userName, action, entityType, entityId, entityName, module, description, null, null);
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(UUID userId, String userEmail, ActivityAction action,
                           String entityType, String module, String description, String errorMessage) {
        try {
            ActivityLog logEntry = ActivityLog.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .action(action)
                    .entityType(entityType)
                    .module(module)
                    .description(description)
                    .success(false)
                    .errorMessage(errorMessage)
                    .build();

            activityLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to save failure activity log for user: {} action: {}", userEmail, action, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityLogResponse getById(UUID id) {
        ActivityLog log = activityLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityLog", "id", id.toString()));
        return toResponse(log);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> getAll(UUID userId, ActivityAction action,
                                                     String entityType, String module,
                                                     LocalDateTime from, LocalDateTime to,
                                                     Boolean success,
                                                     int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ActivityLog> result = activityLogRepository.findAll(
                ActivityLogSpecification.withFilters(userId, action, entityType, module, from, to, success),
                pageable);
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> getForEntity(String entityType, UUID entityId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ActivityLog> result = activityLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> getForUser(UUID userId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ActivityLog> result = activityLogRepository.findByUserId(userId, pageable);
        return toPageResponse(result);
    }

    private ActivityLogResponse toResponse(ActivityLog log) {
        return ActivityLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .userEmail(log.getUserEmail())
                .userName(log.getUserName())
                .action(log.getAction())
                .actionLabel(formatActionLabel(log.getAction()))
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .entityName(log.getEntityName())
                .module(log.getModule())
                .description(log.getDescription())
                .oldValues(log.getOldValues())
                .newValues(log.getNewValues())
                .ipAddress(log.getIpAddress())
                .success(log.isSuccess())
                .errorMessage(log.getErrorMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private PageResponse<ActivityLogResponse> toPageResponse(Page<ActivityLog> page) {
        return PageResponse.<ActivityLogResponse>builder()
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

    private String formatActionLabel(ActivityAction action) {
        if (action == null) return "";
        return action.name().replace("_", " ");
    }
}
