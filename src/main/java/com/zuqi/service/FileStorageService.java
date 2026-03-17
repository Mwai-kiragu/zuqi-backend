package com.zuqi.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface FileStorageService {
    /** Store file in a sub-directory, e.g. "receipts". Returns public path like /uploads/receipts/abc.jpg */
    String storeFile(MultipartFile file, String subDirectory) throws IOException;

    /** Store file scoped to a user ID, e.g. user documents/KYC. Returns public path. */
    String storeFile(MultipartFile file, UUID userId, String type) throws IOException;

    void deleteFile(String fileUrl);
}
