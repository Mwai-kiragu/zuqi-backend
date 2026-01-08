package com.zuqi.service.impl;

import com.zuqi.api.dto.role.PermissionFullResponse;
import com.zuqi.api.dto.role.PermissionRequest;
import com.zuqi.domain.user.Permission;
import com.zuqi.exception.DuplicateResourceException;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.PermissionRepository;
import com.zuqi.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PermissionFullResponse> getAllPermissions() {
        log.info("Fetching all permissions");
        return permissionRepository.findAll().stream()
                .map(PermissionFullResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionFullResponse> getPermissionsByModule(String module) {
        log.info("Fetching permissions by module: {}", module);
        return permissionRepository.findByModule(module).stream()
                .map(PermissionFullResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionFullResponse getPermissionById(Long id) {
        log.info("Fetching permission by ID: {}", id);
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id.toString()));
        return PermissionFullResponse.fromEntity(permission);
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionFullResponse getPermissionByName(String name) {
        log.info("Fetching permission by name: {}", name);
        Permission permission = permissionRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "name", name));
        return PermissionFullResponse.fromEntity(permission);
    }

    @Override
    @Transactional
    public PermissionFullResponse createPermission(PermissionRequest request) {
        log.info("Creating new permission: {}", request.getName());

        // Check if permission already exists
        if (permissionRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Permission", "name", request.getName());
        }

        Permission permission = Permission.builder()
                .name(request.getName())
                .description(request.getDescription())
                .module(request.getModule())
                .build();

        Permission savedPermission = permissionRepository.save(permission);
        log.info("Permission created successfully with ID: {}", savedPermission.getId());

        return PermissionFullResponse.fromEntity(savedPermission);
    }

    @Override
    @Transactional
    public PermissionFullResponse updatePermission(Long id, PermissionRequest request) {
        log.info("Updating permission with ID: {}", id);

        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id.toString()));

        // Check if new name conflicts with existing permission
        if (!permission.getName().equals(request.getName()) && permissionRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Permission", "name", request.getName());
        }

        permission.setName(request.getName());
        permission.setDescription(request.getDescription());
        permission.setModule(request.getModule());

        Permission updatedPermission = permissionRepository.save(permission);
        log.info("Permission updated successfully: {}", updatedPermission.getId());

        return PermissionFullResponse.fromEntity(updatedPermission);
    }

    @Override
    @Transactional
    public void deletePermission(Long id) {
        log.info("Deleting permission with ID: {}", id);

        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id.toString()));

        // Check if permission is assigned to any roles
        if (!permission.getRoles().isEmpty()) {
            throw new ValidationException("Cannot delete permission that is assigned to roles. Remove from roles first.");
        }

        permissionRepository.delete(permission);
        log.info("Permission deleted: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllModules() {
        log.info("Fetching all permission modules");
        return permissionRepository.findDistinctModules();
    }
}
