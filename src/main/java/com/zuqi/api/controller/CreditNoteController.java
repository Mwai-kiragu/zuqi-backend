package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.returns.ApplyCreditNoteRequest;
import com.zuqi.api.dto.returns.CreditNoteResponse;
import com.zuqi.service.CreditNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/credit-notes")
@RequiredArgsConstructor
@Tag(name = "Credit Notes", description = "Customer credit notes management")
public class CreditNoteController {

    private final CreditNoteService creditNoteService;

    @GetMapping
    @Operation(summary = "List credit notes")
    public ResponseEntity<ApiResponse<Page<CreditNoteResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                creditNoteService.getAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get credit note by ID")
    public ResponseEntity<ApiResponse<CreditNoteResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(creditNoteService.getById(id)));
    }

    @PostMapping("/{id}/apply")
    @Operation(summary = "Apply credit note balance to an invoice")
    public ResponseEntity<ApiResponse<CreditNoteResponse>> apply(
            @PathVariable UUID id,
            @Valid @RequestBody ApplyCreditNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Credit applied", creditNoteService.apply(id, request)));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Mark credit note as cash-refunded")
    public ResponseEntity<ApiResponse<CreditNoteResponse>> markRefunded(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Marked as refunded", creditNoteService.markRefunded(id)));
    }
}
