package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.gl.*;
import com.zuqi.domain.gl.JournalEntryStatus;
import com.zuqi.domain.gl.JournalSourceModule;
import com.zuqi.domain.user.User;
import com.zuqi.service.JournalEntryService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/v1/gl/journals")
@RequiredArgsConstructor
@Tag(name = "Journal Entries", description = "General Ledger journal entry management")
public class JournalEntryController {

    private final JournalEntryService journalEntryService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get all journal entries with optional filters")
    public ResponseEntity<ApiResponse<Page<JournalEntryResponse>>> getAll(
            @RequestParam(required = false) UUID distributorId,
            @RequestParam(required = false) JournalEntryStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) JournalSourceModule sourceModule,
            @PageableDefault(size = 20, sort = "entryDate", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success(
                journalEntryService.getAll(effectiveDistributorId, status, fromDate, toDate, sourceModule, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DISTRIBUTOR_ADMIN','FINANCE')")
    @Operation(summary = "Get journal entry by ID")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(journalEntryService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
    @Operation(summary = "Create a DRAFT journal entry")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> create(
            @Valid @RequestBody JournalEntryRequest request,
            @RequestParam(required = false) UUID distributorId,
            @AuthenticationPrincipal User currentUser) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Journal entry created", journalEntryService.create(effectiveDistributorId, request, currentUser)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
    @Operation(summary = "Update a DRAFT journal entry")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody JournalEntryRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Journal entry updated", journalEntryService.update(id, request, currentUser)));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
    @Operation(summary = "Submit journal entry for approval")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> submit(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Journal entry submitted for approval", journalEntryService.submit(id, currentUser)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
    @Operation(summary = "Approve and post a journal entry")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveJournalRequest request,
            @AuthenticationPrincipal User currentUser) {
        String comments = request != null ? request.getComments() : null;
        return ResponseEntity.ok(ApiResponse.success("Journal entry approved and posted", journalEntryService.approve(id, currentUser, comments)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
    @Operation(summary = "Reject a journal entry")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectJournalRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Journal entry rejected", journalEntryService.reject(id, currentUser, request.getReason())));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE')")
    @Operation(summary = "Reverse a posted journal entry")
    public ResponseEntity<ApiResponse<JournalEntryResponse>> reverse(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Journal entry reversed", journalEntryService.reverse(id, currentUser)));
    }
}
