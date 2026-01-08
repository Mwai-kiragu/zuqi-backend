package com.zuqi.service;

import com.zuqi.api.dto.role.RoleRequest;
import com.zuqi.api.dto.role.RoleResponse;

import java.util.List;

public interface RoleService {

    List<RoleResponse> getAllRoles();

    List<RoleResponse> getSystemRoles();

    List<RoleResponse> getCustomRoles();

    RoleResponse getRoleById(Long id);

    RoleResponse getRoleByName(String name);

    RoleResponse createRole(RoleRequest request);

    RoleResponse updateRole(Long id, RoleRequest request);

    void deleteRole(Long id);

    RoleResponse updateRolePermissions(Long roleId, List<Long> permissionIds);
}
