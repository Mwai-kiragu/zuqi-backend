package com.zuqi.service;

import com.zuqi.api.dto.role.PermissionFullResponse;
import com.zuqi.api.dto.role.PermissionRequest;

import java.util.List;

public interface PermissionService {

    List<PermissionFullResponse> getAllPermissions();

    List<PermissionFullResponse> getPermissionsByModule(String module);

    PermissionFullResponse getPermissionById(Long id);

    PermissionFullResponse getPermissionByName(String name);

    PermissionFullResponse createPermission(PermissionRequest request);

    PermissionFullResponse updatePermission(Long id, PermissionRequest request);

    void deletePermission(Long id);

    List<String> getAllModules();
}
