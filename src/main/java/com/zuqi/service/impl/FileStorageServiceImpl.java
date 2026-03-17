package com.zuqi.service.impl;

import com.zuqi.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public String storeFile(MultipartFile file, String subDirectory) throws IOException {
        Path dir = Paths.get(uploadDir, subDirectory);
        Files.createDirectories(dir);

        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID().toString() + (ext != null ? "." + ext : "");
        Path target = dir.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/" + subDirectory + "/" + filename;
    }

    @Override
    public String storeFile(MultipartFile file, UUID userId, String type) throws IOException {
        return storeFile(file, userId.toString() + "/" + type);
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            if (fileUrl != null && fileUrl.startsWith("/uploads/")) {
                Path target = Paths.get(uploadDir + fileUrl.substring("/uploads".length()));
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", fileUrl, e);
        }
    }
}
