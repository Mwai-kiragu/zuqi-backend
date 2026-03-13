package com.zuqi.service.impl;

import com.zuqi.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    private static final String UPLOAD_DIR = "uploads/kyc";

    @Override
    public String storeFile(MultipartFile file, UUID userId, String category) {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR, userId.toString());
            Files.createDirectories(uploadPath);

            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String filename = category + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String relativePath = UPLOAD_DIR + "/" + userId + "/" + filename;
            log.info("File stored: {}", relativePath);
            return relativePath;
        } catch (IOException e) {
            log.error("Failed to store file for user: {}", userId, e);
            throw new RuntimeException("Failed to store file", e);
        }
    }
}
