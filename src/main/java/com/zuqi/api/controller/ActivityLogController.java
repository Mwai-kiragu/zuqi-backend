package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.audit.ActivityLogResponse;
import com.zuqi.api.dto.common.PageResponse;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Activity audit trail")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    @GetMapping("/{id}")
    @Operation(summary = "Get a single audit log entry")
    public ResponseEntity<ApiResponse<ActivityLogResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(activityLogService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Query audit logs with filters")
    public ResponseEntity<ApiResponse<PageResponse<ActivityLogResponse>>> getAll(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) ActivityAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageResponse<ActivityLogResponse> result =
                activityLogService.getAll(userId, action, entityType, module, from, to, success, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @Operation(summary = "Get audit trail for a specific entity")
    public ResponseEntity<ApiResponse<PageResponse<ActivityLogResponse>>> getForEntity(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageResponse<ActivityLogResponse> result =
                activityLogService.getForEntity(entityType, entityId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get audit trail for a specific user")
    public ResponseEntity<ApiResponse<PageResponse<ActivityLogResponse>>> getForUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageResponse<ActivityLogResponse> result = activityLogService.getForUser(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
