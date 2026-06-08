package com.zuqi.service;

import com.zuqi.api.dto.supplier.SupplierCategoryRequest;
import com.zuqi.api.dto.supplier.SupplierCategoryResponse;
import com.zuqi.api.dto.supplier.SupplierRequest;
import com.zuqi.api.dto.supplier.SupplierResponse;
import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface SupplierService {

    Page<SupplierResponse> getAllSuppliers(String search, Pageable pageable);

    Page<SupplierResponse> getBlacklistedSuppliers(Pageable pageable);

    SupplierResponse getSupplierById(String id);

    SupplierResponse createSupplier(SupplierRequest request);

    SupplierResponse updateSupplier(String id, SupplierRequest request);

    SupplierResponse verifySupplier(String id);

    SupplierResponse blacklistSupplier(String id, String reason, User currentUser);

    SupplierResponse unblacklistSupplier(String id);

    void deactivateSupplier(String id, String reason, User currentUser);

    void activateSupplier(String id);

    List<SupplierCategoryResponse> getAllCategories();

    SupplierCategoryResponse createCategory(SupplierCategoryRequest request);

    SupplierCategoryResponse updateCategory(Long id, SupplierCategoryRequest request);

    void deleteCategory(Long id);
}
