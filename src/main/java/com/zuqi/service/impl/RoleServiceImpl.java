package com.zuqi.service.impl;

import com.zuqi.api.dto.role.RoleRequest;
import com.zuqi.api.dto.role.RoleResponse;
import com.zuqi.domain.user.Permission;
import com.zuqi.domain.user.Role;
import com.zuqi.exception.DuplicateResourceException;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.PermissionRepository;
import com.zuqi.repository.RoleRepository;
import com.zuqi.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        log.info("Fetching all roles");
        return roleRepository.findAll().stream()
                .map(RoleResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getSystemRoles() {
        log.info("Fetching system roles");
        return roleRepository.findBySystemRoleTrue().stream()
                .map(RoleResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getCustomRoles() {
        log.info("Fetching custom roles");
        return roleRepository.findBySystemRoleFalse().stream()
                .map(RoleResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long id) {
        log.info("Fetching role by ID: {}", id);
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id.toString()));
        return RoleResponse.fromEntity(role);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleByName(String name) {
        log.info("Fetching role by name: {}", name);
        Role role = roleRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", name));
        return RoleResponse.fromEntity(role);
    }

    @Override
    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        log.info("Creating new role: {}", request.getName());

        // Check if role already exists
        if (roleRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Role", "name", request.getName());
        }

        // Get permissions if provided
        Set<Permission> permissions = new HashSet<>();
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            permissions = request.getPermissionIds().stream()
                    .map(id -> permissionRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id.toString())))
                    .collect(Collectors.toSet());
        }

        Role role = Role.builder()
                .name(request.getName())
                .description(request.getDescription())
                .systemRole(false) // Custom roles are not system roles
                .permissions(permissions)
                .build();

        Role savedRole = roleRepository.save(role);
        log.info("Role created successfully with ID: {}", savedRole.getId());

        return RoleResponse.fromEntity(savedRole);
    }

    @Override
    @Transactional
    public RoleResponse updateRole(Long id, RoleRequest request) {
        log.info("Updating role with ID: {}", id);

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id.toString()));

        // System roles can only have their description and permissions updated
        if (role.isSystemRole()) {
            if (!role.getName().equals(request.getName())) {
                throw new ValidationException("Cannot change the name of a system role");
            }
        } else {
            // Check if new name conflicts with existing role
            if (!role.getName().equals(request.getName()) && roleRepository.existsByName(request.getName())) {
                throw new DuplicateResourceException("Role", "name", request.getName());
            }
            role.setName(request.getName());
        }

        role.setDescription(request.getDescription());

        // Update permissions if provided
        if (request.getPermissionIds() != null) {
            Set<Permission> permissions = request.getPermissionIds().stream()
                    .map(permId -> permissionRepository.findById(permId)
                            .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", permId.toString())))
                    .collect(Collectors.toSet());
            role.setPermissions(permissions);
        }

        Role updatedRole = roleRepository.save(role);
        log.info("Role updated successfully: {}", updatedRole.getId());

        return RoleResponse.fromEntity(updatedRole);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        log.info("Deleting role with ID: {}", id);

        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id.toString()));

        if (role.isSystemRole()) {
            throw new ValidationException("Cannot delete a system role");
        }

        roleRepository.delete(role);
        log.info("Role deleted: {}", id);
    }

    @Override
    @Transactional
    public RoleResponse updateRolePermissions(Long roleId, List<Long> permissionIds) {
        log.info("Updating permissions for role: {}", roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", roleId.toString()));

        Set<Permission> permissions = permissionIds.stream()
                .map(id -> permissionRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id.toString())))
                .collect(Collectors.toSet());

        role.setPermissions(permissions);
        Role updatedRole = roleRepository.save(role);

        log.info("Role permissions updated for role: {}", roleId);
        return RoleResponse.fromEntity(updatedRole);
    }
}
