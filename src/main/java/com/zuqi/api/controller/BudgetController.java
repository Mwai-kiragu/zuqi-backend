package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.gl.BudgetBulkRequest;
import com.zuqi.api.dto.gl.BudgetResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.BudgetService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/gl/budgets")
@RequiredArgsConstructor
@Tag(name = "GL Budgets", description = "Budget management")
public class BudgetController {

    private final BudgetService budgetService;
    private final SecurityUtils securityUtils;

    @GetMapping
    @Operation(summary = "Get budgets by year and optional month")
    public ResponseEntity<ApiResponse<List<BudgetResponse>>> getBudgets(
            @RequestParam int year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) UUID distributorId) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        List<BudgetResponse> result = month != null
                ? budgetService.getByYearAndMonth(effectiveDistributorId, year, month)
                : budgetService.getByYear(effectiveDistributorId, year);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk create or update budgets for a period")
    public ResponseEntity<ApiResponse<List<BudgetResponse>>> bulkUpsert(
            @Valid @RequestBody BudgetBulkRequest request,
            @RequestParam(required = false) UUID distributorId,
            @AuthenticationPrincipal User currentUser) {
        UUID effectiveDistributorId = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return ResponseEntity.ok(ApiResponse.success("Budgets saved", budgetService.upsert(effectiveDistributorId, request, currentUser)));
    }
}
