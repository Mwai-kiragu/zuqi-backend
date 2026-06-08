package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.procurement.PoConfirmationDetailsResponse;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.service.PoSupplierConfirmationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/po-confirm")
@RequiredArgsConstructor
public class PoSupplierResponseController {

    private final PoSupplierConfirmationService confirmationService;

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<PoConfirmationDetailsResponse>> getTokenDetails(
            @PathVariable String token) {
        PoConfirmationDetailsResponse details = confirmationService.getTokenDetails(token);
        return ResponseEntity.ok(ApiResponse.success("Token details retrieved", details));
    }

    @PostMapping("/{token}")
    public ResponseEntity<ApiResponse<Void>> processResponse(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        confirmationService.processResponse(token, notes);
        return ResponseEntity.ok(ApiResponse.success("Response recorded. Thank you!", null));
    }
}
