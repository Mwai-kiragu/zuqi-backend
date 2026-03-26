package com.zuqi.service.impl;

import com.zuqi.api.dto.accesscontrol.*;
import com.zuqi.domain.accesscontrol.UserGroup;
import com.zuqi.domain.accesscontrol.UserType;
import com.zuqi.domain.accesscontrol.UserTypePermission;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.UserGroupRepository;
import com.zuqi.repository.UserTypeRepository;
import com.zuqi.service.AccessControlService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccessControlServiceImpl implements AccessControlService {

    private final UserTypeRepository userTypeRepository;
    private final UserGroupRepository userGroupRepository;
    private final SecurityUtils securityUtils;


    // ─── UserType ─────────────────────────────────────────────────────────────

    @Override
    public Page<UserTypeResponse> listUserTypes(Pageable pageable) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        Page<UserType> page = distributorId != null
                ? userTypeRepository.findByDistributorId(distributorId, pageable)
                : userTypeRepository.findAll(pageable);
        return page.map(this::toUserTypeResponse);
    }

    @Override
    public UserTypeResponse getUserType(UUID id) {
        UserType ut = userTypeRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserType", "id", id));
        return toUserTypeResponse(ut);
    }

    @Override
    @Transactional
    public UserTypeResponse createUserType(UserTypeRequest request) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null && userTypeRepository.existsByDistributorIdAndName(distributorId, request.getName())) {
            throw new ValidationException("A user type with this name already exists");
        }

        UserType ut = UserType.builder()
                .name(request.getName())
                .description(request.getDescription())
                .distributorId(distributorId)
                .build();

        if (request.getPermissions() != null) {
            List<UserTypePermission> perms = request.getPermissions().stream()
                    .map(dto -> buildPermission(dto, ut))
                    .collect(Collectors.toList());
            ut.setPermissions(perms);
        }

        return toUserTypeResponse(userTypeRepository.save(ut));
    }

    @Override
    @Transactional
    public UserTypeResponse updateUserType(UUID id, UserTypeRequest request) {
        UserType ut = userTypeRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserType", "id", id));

        ut.setName(request.getName());
        ut.setDescription(request.getDescription());

        if (request.getPermissions() != null) {
            // Build a map of incoming permissions keyed by module
            java.util.Map<String, UserTypePermissionDto> incoming = request.getPermissions().stream()
                    .collect(java.util.stream.Collectors.toMap(UserTypePermissionDto::getModule, d -> d));

            // Update existing / remove stale
            ut.getPermissions().removeIf(existing -> {
                UserTypePermissionDto dto = incoming.remove(existing.getModule());
                if (dto == null) {
                    return true; // not in new set → orphanRemoval will delete it
                }
                // Still present — update flags in-place (no DELETE/INSERT)
                existing.setCanCreate(dto.isCanCreate());
                existing.setCanRead(dto.isCanRead());
                existing.setCanUpdate(dto.isCanUpdate());
                existing.setCanDelete(dto.isCanDelete());
                existing.setCanApprove(dto.isCanApprove());
                return false;
            });

            // Insert only truly new modules (remaining entries in map)
            incoming.values().stream()
                    .map(dto -> buildPermission(dto, ut))
                    .forEach(ut.getPermissions()::add);
        } else {
            ut.getPermissions().clear();
        }

        return toUserTypeResponse(userTypeRepository.save(ut));
    }

    @Override
    @Transactional
    public void deleteUserType(UUID id) {
        if (!userTypeRepository.existsById(id)) {
            throw new ResourceNotFoundException("UserType", "id", id);
        }
        userTypeRepository.deleteById(id);
    }

    // ─── UserGroup ────────────────────────────────────────────────────────────

    @Override
    public Page<UserGroupResponse> listUserGroups(Pageable pageable) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        Page<UserGroup> page = distributorId != null
                ? userGroupRepository.findByDistributorId(distributorId, pageable)
                : userGroupRepository.findAll(pageable);
        return page.map(this::toUserGroupResponse);
    }

    @Override
    public UserGroupResponse getUserGroup(UUID id) {
        UserGroup g = userGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserGroup", "id", id));
        return toUserGroupResponse(g);
    }

    @Override
    @Transactional
    public UserGroupResponse createUserGroup(UserGroupRequest request) {
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null && userGroupRepository.existsByDistributorIdAndNameAndUserTypeId(
                distributorId, request.getName(), request.getUserTypeId())) {
            throw new ValidationException("A user group with this name already exists for this user type");
        }

        UserType userType = userTypeRepository.findById(request.getUserTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("UserType", "id", request.getUserTypeId()));

        validateWorkflowTier(request.getWorkflowTier());

        UserGroup g = UserGroup.builder()
                .name(request.getName())
                .description(request.getDescription())
                .distributorId(distributorId)
                .userType(userType)
                .workflowTier(request.getWorkflowTier())
                .approvalLevel(request.getApprovalLevel())
                .build();

        return toUserGroupResponse(userGroupRepository.save(g));
    }

    @Override
    @Transactional
    public UserGroupResponse updateUserGroup(UUID id, UserGroupRequest request) {
        UserGroup g = userGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserGroup", "id", id));

        UserType userType = userTypeRepository.findById(request.getUserTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("UserType", "id", request.getUserTypeId()));

        // Check for name collision only if name or user type is changing
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        boolean nameChanged = !request.getName().equals(g.getName());
        boolean typeChanged = !request.getUserTypeId().equals(g.getUserType().getId());
        if (distributorId != null && (nameChanged || typeChanged)
                && userGroupRepository.existsByDistributorIdAndNameAndUserTypeId(
                        distributorId, request.getName(), request.getUserTypeId())) {
            throw new ValidationException("A user group with this name already exists for this user type");
        }

        validateWorkflowTier(request.getWorkflowTier());

        g.setName(request.getName());
        g.setDescription(request.getDescription());
        g.setUserType(userType);
        g.setWorkflowTier(request.getWorkflowTier());
        g.setApprovalLevel(request.getApprovalLevel());

        return toUserGroupResponse(userGroupRepository.save(g));
    }

    @Override
    @Transactional
    public void deleteUserGroup(UUID id) {
        if (!userGroupRepository.existsById(id)) {
            throw new ResourceNotFoundException("UserGroup", "id", id);
        }
        userGroupRepository.deleteById(id);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UserTypePermission buildPermission(UserTypePermissionDto dto, UserType ut) {
        return UserTypePermission.builder()
                .userType(ut)
                .module(dto.getModule())
                .canCreate(dto.isCanCreate())
                .canRead(dto.isCanRead())
                .canUpdate(dto.isCanUpdate())
                .canDelete(dto.isCanDelete())
                .canApprove(dto.isCanApprove())
                .build();
    }

    private UserTypeResponse toUserTypeResponse(UserType ut) {
        List<UserTypePermissionDto> perms = ut.getPermissions().stream().map(p -> {
            UserTypePermissionDto dto = new UserTypePermissionDto();
            dto.setModule(p.getModule());
            dto.setCanCreate(p.isCanCreate());
            dto.setCanRead(p.isCanRead());
            dto.setCanUpdate(p.isCanUpdate());
            dto.setCanDelete(p.isCanDelete());
            dto.setCanApprove(p.isCanApprove());
            return dto;
        }).collect(Collectors.toList());

        return UserTypeResponse.builder()
                .id(ut.getId())
                .name(ut.getName())
                .description(ut.getDescription())
                .distributorId(ut.getDistributorId())
                .permissions(perms)
                .createdAt(ut.getCreatedAt())
                .updatedAt(ut.getUpdatedAt())
                .build();
    }

    private UserGroupResponse toUserGroupResponse(UserGroup g) {
        return UserGroupResponse.builder()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .distributorId(g.getDistributorId())
                .userTypeId(g.getUserType() != null ? g.getUserType().getId() : null)
                .userTypeName(g.getUserType() != null ? g.getUserType().getName() : null)
                .workflowTier(g.getWorkflowTier())
                .approvalLevel(g.getApprovalLevel())
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt())
                .build();
    }

    private void validateWorkflowTier(String tier) {
        if (tier == null) return;
        if (!List.of("INITIATOR", "VERIFIER", "AUTHORIZER").contains(tier)) {
            throw new ValidationException("Invalid workflowTier. Must be INITIATOR, VERIFIER, or AUTHORIZER");
        }
    }
}
