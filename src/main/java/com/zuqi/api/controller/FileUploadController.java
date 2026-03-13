package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/v1/files")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "File upload endpoints")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    @Operation(summary = "Upload file", description = "Upload a file for KYC or other purposes")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadFile(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "document") String type) {
        String filePath = fileStorageService.storeFile(file, user.getId(), type);
        return ResponseEntity.ok(ApiResponse.success("File uploaded successfully",
                Map.of("url", filePath, "filename", file.getOriginalFilename() != null ? file.getOriginalFilename() : "file")));
    }
}
