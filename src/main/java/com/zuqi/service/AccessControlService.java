package com.zuqi.service;

import com.zuqi.api.dto.accesscontrol.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AccessControlService {

    // ─── UserType ─────────────────────────────────────────────────────────────

    Page<UserTypeResponse> listUserTypes(Pageable pageable);

    UserTypeResponse getUserType(UUID id);

    UserTypeResponse createUserType(UserTypeRequest request);

    UserTypeResponse updateUserType(UUID id, UserTypeRequest request);

    void deleteUserType(UUID id);

    // ─── UserGroup ────────────────────────────────────────────────────────────

    Page<UserGroupResponse> listUserGroups(Pageable pageable);

    UserGroupResponse getUserGroup(UUID id);

    UserGroupResponse createUserGroup(UserGroupRequest request);

    UserGroupResponse updateUserGroup(UUID id, UserGroupRequest request);

    void deleteUserGroup(UUID id);
}
