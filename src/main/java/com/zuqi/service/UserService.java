package com.zuqi.service;

import com.zuqi.api.dto.user.ChangePasswordRequest;
import com.zuqi.api.dto.user.CreateUserRequest;
import com.zuqi.api.dto.user.ResetPasswordRequest;
import com.zuqi.api.dto.user.UpdateProfileRequest;
import com.zuqi.api.dto.user.UpdateUserRequest;
import com.zuqi.api.dto.user.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface UserService {

    Page<UserResponse> getAllUsers(Pageable pageable);

    Page<UserResponse> getUsersByDistributor(UUID distributorId, Pageable pageable);

    List<UserResponse> getUsersByRole(String role, UUID distributorId);

    UserResponse getUserById(UUID id);

    UserResponse createUser(CreateUserRequest request, UUID creatorDistributorId);

    UserResponse updateUser(UUID id, UpdateUserRequest request);

    UserResponse updateProfile(UUID id, UpdateProfileRequest request);

    void resetPassword(UUID id, ResetPasswordRequest request);

    void changePassword(UUID id, ChangePasswordRequest request);

    void deactivateUser(UUID id);

    void activateUser(UUID id);

    List<String> getAvailableRoles(boolean isAdmin);
}
