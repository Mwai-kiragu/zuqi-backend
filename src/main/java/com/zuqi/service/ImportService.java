package com.zuqi.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ImportService {

    ImportResult importCustomers(MultipartFile file, UUID distributorId);

    ImportResult importSuppliers(MultipartFile file, UUID distributorId);

    ImportResult importProducts(MultipartFile file, UUID distributorId);

    ImportResult importCategories(MultipartFile file, UUID distributorId);

    record ImportResult(int imported, int failed, java.util.List<RowError> errors) {}

    record RowError(int row, String message) {}
}
