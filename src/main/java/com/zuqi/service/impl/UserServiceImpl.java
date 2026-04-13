package com.zuqi.service.impl;

import com.zuqi.api.dto.user.ChangePasswordRequest;
import com.zuqi.api.dto.user.CreateUserRequest;
import com.zuqi.api.dto.user.ResetPasswordRequest;
import com.zuqi.api.dto.user.UpdateProfileRequest;
import com.zuqi.api.dto.user.UpdateUserRequest;
import com.zuqi.api.dto.user.UserResponse;
import com.zuqi.domain.branch.BranchUser;
import com.zuqi.domain.branch.BranchUserStatus;
import com.zuqi.domain.branch.DistributorBranch;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.domain.accesscontrol.UserGroup;
import com.zuqi.domain.user.Role;
import com.zuqi.domain.user.RoleName;
import com.zuqi.domain.user.User;
import com.zuqi.exception.DuplicateResourceException;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.BranchUserRepository;
import com.zuqi.repository.DistributorBranchRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.MerchantRepository;
import com.zuqi.repository.RoleRepository;
import com.zuqi.repository.UserGroupRepository;
import com.zuqi.repository.UserRepository;
import com.zuqi.service.ActivityLogService;
import com.zuqi.service.EmailService;
import com.zuqi.service.UserService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DistributorRepository distributorRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserGroupRepository userGroupRepository;
    private final SecurityUtils securityUtils;
    private final ActivityLogService activityLogService;
    private final EmailService emailService;
    private final DistributorBranchRepository distributorBranchRepository;
    private final BranchUserRepository branchUserRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return getAllUsers(pageable, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable, Boolean active) {
        return getAllUsers(pageable, active, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable, Boolean active, String search) {
        log.info("Fetching all users with active filter: {}, search: {}", active, search);

        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            if (search != null && !search.isBlank()) {
                if (active != null && !active) {
                    return userRepository.searchInactiveByMerchantScope(merchantId, search, pageable)
                            .map(this::mapToUserResponse);
                }
                return userRepository.searchByMerchantScope(merchantId, search, pageable)
                        .map(this::mapToUserResponse);
            }
            if (active != null && !active) {
                return userRepository.findInactiveByMerchantScope(merchantId, pageable)
                        .map(this::mapToUserResponse);
            }
            return userRepository.findByMerchantScope(merchantId, pageable)
                    .map(this::mapToUserResponse);
        }

        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            return getUsersByDistributor(distributorId, pageable, active, search);
        }

        // Non-SUPER_ADMIN with no merchant/distributor scope — return only themselves
        if (!securityUtils.isSuperAdmin()) {
            UUID currentUserId = securityUtils.getCurrentUserId();
            return userRepository.findById(currentUserId)
                    .map(u -> {
                        UserResponse resp = mapToUserResponse(u);
                        return (Page<UserResponse>) new PageImpl<>(List.of(resp), pageable, 1L);
                    })
                    .orElse(Page.empty(pageable));
        }

        // SUPER_ADMIN
        if (search != null && !search.isBlank()) {
            if (active != null) {
                return userRepository.searchAllUsersByActive(search, active, pageable)
                        .map(this::mapToUserResponse);
            }
            return userRepository.searchAllUsers(search, pageable)
                    .map(this::mapToUserResponse);
        }

        if (active == null) {
            return userRepository.findAll(pageable).map(this::mapToUserResponse);
        } else if (active) {
            return userRepository.findByActiveTrue(pageable).map(this::mapToUserResponse);
        } else {
            return userRepository.findByActiveFalse(pageable).map(this::mapToUserResponse);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersByDistributor(UUID distributorId, Pageable pageable) {
        return getUsersByDistributor(distributorId, pageable, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersByDistributor(UUID distributorId, Pageable pageable, Boolean active) {
        return getUsersByDistributor(distributorId, pageable, active, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersByDistributor(UUID distributorId, Pageable pageable, Boolean active, String search) {
        log.info("Fetching users for distributor: {} with active filter: {}, search: {}", distributorId, active, search);

        if (search != null && !search.isBlank()) {
            if (active != null) {
                return userRepository.searchByDistributorAndActive(distributorId, search, active, pageable)
                        .map(this::mapToUserResponse);
            }
            return userRepository.searchByDistributor(distributorId, search, pageable)
                    .map(this::mapToUserResponse);
        }

        if (active == null) {
            return userRepository.findByDistributorId(distributorId, pageable).map(this::mapToUserResponse);
        } else if (active) {
            return userRepository.findByDistributorIdAndActiveTrue(distributorId, pageable).map(this::mapToUserResponse);
        } else {
            return userRepository.findByDistributorIdAndActiveFalse(distributorId, pageable).map(this::mapToUserResponse);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByRole(String role, UUID distributorId) {
        log.info("Fetching users by role: {} for distributor: {}", role, distributorId);

        // Determine effective distributor ID for filtering
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        List<User> allUsers;
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (effectiveDistributorId != null) {
            allUsers = userRepository.findByDistributorIdAndActiveTrue(effectiveDistributorId);
        } else if (merchantId != null) {
            allUsers = userRepository.findActiveByMerchantScope(merchantId);
        } else {
            // SUPER_ADMIN can see all users
            allUsers = userRepository.findAll();
        }

        return allUsers.stream()
                .filter(user -> user.isActive() && user.getRoles().stream()
                        .anyMatch(r -> r.getName().equals(role)))
                .map(this::mapToUserResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        log.info("Fetching user by ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request, UUID creatorDistributorId) {
        log.info("Creating new user with email: {}", request.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        // Check if phone number already exists (if provided)
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new DuplicateResourceException("User", "phoneNumber", request.getPhoneNumber());
        }

        // Resolve UserGroup early so we can derive the role from its UserType.baseRole
        UserGroup userGroup = null;
        if (request.getUserGroupId() != null) {
            userGroup = userGroupRepository.findByIdWithUserType(request.getUserGroupId()).orElse(null);
        }

        // Determine effective system role:
        // Priority: UserGroup.userType.baseRole > explicit request.role > UserGroup.workflowTier (fallback for tier-only users)
        String effectiveRole = null;
        if (userGroup != null && userGroup.getUserType() != null
                && userGroup.getUserType().getBaseRole() != null
                && !userGroup.getUserType().getBaseRole().isBlank()) {
            effectiveRole = userGroup.getUserType().getBaseRole();
        } else if (request.getRole() != null && !request.getRole().isBlank()) {
            effectiveRole = request.getRole();
        } else if (userGroup != null && userGroup.getWorkflowTier() != null && !userGroup.getWorkflowTier().isBlank()) {
            // Tier-only user: take the first tier as the primary system role
            // (workflowTier may be comma-separated, e.g. "INITIATOR,VERIFIER")
            effectiveRole = userGroup.getWorkflowTier().split(",")[0].trim();
        }
        if (effectiveRole == null || effectiveRole.isBlank()) {
            String typeName = (userGroup != null && userGroup.getUserType() != null)
                    ? userGroup.getUserType().getName() : "Unknown";
            throw new ValidationException(
                "The User Type '" + typeName + "' has no Base System Role configured and the User Group has no workflow tier. " +
                "Go to Access Control → User Types and set a Base System Role.");
        }

        final String resolvedRole = effectiveRole;
        RoleName roleName;
        try {
            roleName = RoleName.valueOf(resolvedRole);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid role: " + resolvedRole);
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", resolvedRole));

        // Determine distributor ID
        final UUID finalDistributorId;
        if (request.getDistributorId() != null) {
            finalDistributorId = request.getDistributorId();
        } else if (creatorDistributorId != null) {
            finalDistributorId = creatorDistributorId;
        } else {
            finalDistributorId = null;
        }

        // Validate distributor exists if provided
        if (finalDistributorId != null && roleName != RoleName.SUPER_ADMIN && roleName != RoleName.CUSTOMER) {
            distributorRepository.findById(finalDistributorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", finalDistributorId.toString()));
        }

        // Resolve merchantId: use explicit value, else inherit from creator if they are MERCHANT_ADMIN
        UUID resolvedMerchantId = request.getMerchantId();
        if (resolvedMerchantId == null) {
            User creator = securityUtils.getCurrentUser();
            if (creator != null && creator.getMerchantId() != null) {
                resolvedMerchantId = creator.getMerchantId();
            }
        }

        // Validate merchant exists if provided
        if (resolvedMerchantId != null) {
            final UUID mid = resolvedMerchantId;
            merchantRepository.findById(mid)
                    .orElseThrow(() -> new ResourceNotFoundException("Merchant", "id", mid.toString()));
        }

        // Generate password if not provided
        String password = request.getPassword();
        boolean sendWelcomeEmail = false;
        if (password == null || password.isBlank()) {
            password = generateRandomPassword(12);
            sendWelcomeEmail = true;
            log.info("Generated temporary password for user: {} - Password will be sent via email", request.getEmail());
        }

        // Create user
        Set<Role> roles = new HashSet<>();
        roles.add(role);

        // Add workflow tier roles (INITIATOR / VERIFIER / AUTHORIZER)
        // Auto-derive from UserGroup.workflowTier (may be comma-separated); override with explicit request value
        String tierRawValue = (request.getWorkflowTierRole() != null && !request.getWorkflowTierRole().isBlank())
                ? request.getWorkflowTierRole()
                : (userGroup != null ? userGroup.getWorkflowTier() : null);
        if (tierRawValue != null && !tierRawValue.isBlank()) {
            for (String tier : tierRawValue.split(",")) {
                String trimmedTier = tier.trim();
                if (trimmedTier.isEmpty() || trimmedTier.equals(effectiveRole)) continue;
                try {
                    roleRepository.findByName(RoleName.valueOf(trimmedTier)).ifPresent(roles::add);
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown workflow tier role '{}' on UserGroup {}, skipping", trimmedTier,
                            userGroup != null ? userGroup.getId() : "null");
                }
            }
        }

        // UserGroup already resolved above (with UserType fetch)

        // Require merchantId when assigning MERCHANT_ADMIN role
        if (roleName == RoleName.MERCHANT_ADMIN && resolvedMerchantId == null) {
            throw new ValidationException("merchantId is required when creating a MERCHANT_ADMIN user");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(password))
                .roles(roles)
                .distributorId(finalDistributorId)
                .merchantId(resolvedMerchantId)
                .userGroup(userGroup)
                .active(true)
                .emailVerified(false)
                .mustChangePassword(sendWelcomeEmail)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User created successfully with ID: {}", savedUser.getId());

        User creator = securityUtils.getCurrentUser();
        if (creator != null) {
            activityLogService.log(creator.getId(), creator.getEmail(),
                    creator.getFirstName() + " " + creator.getLastName(),
                    ActivityAction.CREATE, "USER", savedUser.getId(),
                    savedUser.getEmail(), "USERS", "Created user: " + savedUser.getEmail()
                            + " with role: " + request.getRole());
        }

        // Assign user to branch (specified or default HQ branch)
        if (finalDistributorId != null) {
            DistributorBranch targetBranch = null;
            if (request.getBranchId() != null) {
                targetBranch = distributorBranchRepository.findById(request.getBranchId()).orElse(null);
            }
            if (targetBranch == null) {
                targetBranch = distributorBranchRepository
                        .findFirstByDistributorIdAndHeadquartersTrue(finalDistributorId)
                        .orElse(null);
            }
            if (targetBranch != null && !branchUserRepository.existsByBranchIdAndUserId(targetBranch.getId(), savedUser.getId())) {
                String effectiveBranchRole = (request.getBranchRole() != null && !request.getBranchRole().isBlank())
                        ? request.getBranchRole()
                        : resolvedRole;
                BranchUser branchUser = BranchUser.builder()
                        .branch(targetBranch)
                        .user(savedUser)
                        .role(effectiveBranchRole)
                        .status(BranchUserStatus.ACTIVE)
                        .build();
                branchUserRepository.save(branchUser);
                log.info("Assigned user {} to branch {}", savedUser.getId(), targetBranch.getId());
            }
        }

        // Send welcome email with temporary password — failure must not roll back the user creation
        if (sendWelcomeEmail) {
            try {
                emailService.sendWelcomeEmail(savedUser, password);
            } catch (Exception e) {
                log.warn("Failed to send welcome email to {} — user was created successfully: {}",
                        savedUser.getEmail(), e.getMessage());
            }
        }

        return mapToUserResponse(savedUser);
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        log.info("Updating user with ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));

        // Check if email is being changed and already exists
        if (!user.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        // Check if phone number is being changed and already exists
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                && !request.getPhoneNumber().equals(user.getPhoneNumber())
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new DuplicateResourceException("User", "phoneNumber", request.getPhoneNumber());
        }

        // Update user fields
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());

        // Update roles only when role is explicitly provided; otherwise keep existing roles
        if (request.getRole() != null && !request.getRole().isBlank()) {
            RoleName roleName;
            try {
                roleName = RoleName.valueOf(request.getRole());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Invalid role: " + request.getRole());
            }
            Role newRole = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "name", request.getRole()));
            user.getRoles().clear();
            user.getRoles().add(newRole);
            if (request.getWorkflowTierRole() != null && !request.getWorkflowTierRole().isBlank()) {
                roleRepository.findByName(RoleName.valueOf(request.getWorkflowTierRole()))
                        .ifPresent(tierRole -> user.getRoles().add(tierRole));
            }
        }

        // Update distributor and merchant IDs
        user.setDistributorId(request.getDistributorId());
        user.setMerchantId(request.getMerchantId());

        // Update UserGroup
        if (request.getUserGroupId() != null) {
            userGroupRepository.findById(request.getUserGroupId()).ifPresent(user::setUserGroup);
        } else {
            user.setUserGroup(null);
        }

        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        User updatedUser = userRepository.save(user);
        log.info("User updated successfully: {}", updatedUser.getId());

        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfileRequest request) {
        log.info("Updating profile for user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));

        // Check if email is being changed and already exists
        if (!user.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        // Check if phone number is being changed and already exists
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                && !request.getPhoneNumber().equals(user.getPhoneNumber())
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new DuplicateResourceException("User", "phoneNumber", request.getPhoneNumber());
        }

        // Update user fields (only profile fields, not role/distributor/etc.)
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());

        User updatedUser = userRepository.save(user);
        log.info("Profile updated successfully for user: {}", updatedUser.getId());

        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest request) {
        log.info("Resetting password for user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        log.info("Password reset successfully for user: {}", id);
    }

    @Override
    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest request) {
        log.info("Changing password for user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ValidationException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(java.time.LocalDateTime.now());
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Send password changed notification email
        emailService.sendPasswordChangedEmail(user);

        log.info("Password changed successfully for user: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getInactiveUsers(Pageable pageable) {
        log.info("Fetching inactive users");
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            return userRepository.findInactiveByMerchantScope(merchantId, pageable)
                    .map(this::mapToUserResponse);
        }
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            return userRepository.findByDistributorIdAndActiveFalse(distributorId, pageable)
                    .map(this::mapToUserResponse);
        }
        if (!securityUtils.isSuperAdmin()) {
            return Page.empty(pageable);
        }
        return userRepository.findByActiveFalse(pageable)
                .map(this::mapToUserResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getInactiveUsersByDistributor(UUID distributorId, Pageable pageable) {
        log.info("Fetching inactive users for distributor: {}", distributorId);
        return userRepository.findByDistributorIdAndActiveFalse(distributorId, pageable)
                .map(this::mapToUserResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));
    }

    @Override
    @Transactional
    public void deactivateUser(UUID id, String reason, User currentUser) {
        log.info("Deactivating user: {} with reason: {}", id, reason);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));

        user.setActive(false);
        user.setDeactivationReason(reason);
        user.setDeactivatedAt(java.time.LocalDateTime.now());
        user.setDeactivatedBy(currentUser);
        userRepository.save(user);

        log.info("User deactivated: {}", id);
    }

    @Override
    @Transactional
    public void activateUser(UUID id) {
        log.info("Activating user: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id.toString()));

        user.setActive(true);
        user.setDeactivationReason(null);
        user.setDeactivatedAt(null);
        user.setDeactivatedBy(null);
        userRepository.save(user);

        log.info("User activated: {}", id);
    }

    @Override
    public List<String> getAvailableRoles(boolean isAdmin) {
        // SUPER_ADMIN can create all roles
        if (securityUtils.isSuperAdmin()) {
            return Arrays.stream(RoleName.values())
                    .map(Enum::name)
                    .collect(Collectors.toList());
        }

        // Non-admin users can only create these roles
        return Arrays.asList(
                RoleName.DISTRIBUTOR_ADMIN.name(),
                RoleName.SALES_REP.name(),
                RoleName.WAREHOUSE_MANAGER.name(),
                RoleName.FINANCE.name(),
                RoleName.DRIVER.name(),
                RoleName.INITIATOR.name(),
                RoleName.VERIFIER.name(),
                RoleName.AUTHORIZER.name()
        );
    }

    private Page<UserResponse> mapToUserResponsePage(Page<User> users, Pageable pageable) {
        List<UserResponse> responseList = users.getContent().stream()
                .map(this::mapToUserResponse)
                .toList();
        return new PageImpl<>(responseList, pageable, users.getTotalElements());
    }

    private UserResponse mapToUserResponse(User user) {
        String distributorName = null;
        String merchantName = null;

        if (user.getDistributorId() != null) {
            distributorName = distributorRepository.findById(user.getDistributorId())
                    .map(Distributor::getName)
                    .orElse(null);
        }

        if (user.getMerchantId() != null) {
            merchantName = merchantRepository.findById(user.getMerchantId())
                    .map(Merchant::getName)
                    .orElse(null);
        }

        return UserResponse.fromEntityWithNames(user, distributorName, merchantName);
    }

    private String generateRandomPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        // Ensure at least one of each type
        password.append(chars.charAt(random.nextInt(26))); // Uppercase
        password.append(chars.charAt(26 + random.nextInt(26))); // Lowercase
        password.append(chars.charAt(52 + random.nextInt(10))); // Digit
        password.append(chars.charAt(62 + random.nextInt(5))); // Special

        // Fill remaining length
        for (int i = 4; i < length; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }

        // Shuffle the password
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }

        return new String(passwordArray);
    }
}
