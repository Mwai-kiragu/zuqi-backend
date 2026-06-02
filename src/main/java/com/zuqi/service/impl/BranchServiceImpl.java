package com.zuqi.service.impl;

import com.zuqi.api.dto.branch.*;
import com.zuqi.domain.branch.BranchStatus;
import com.zuqi.domain.branch.BranchUser;
import com.zuqi.domain.branch.BranchUserStatus;
import com.zuqi.domain.branch.DistributorBranch;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.BranchUserRepository;
import com.zuqi.repository.DistributorBranchRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.UserRepository;
import com.zuqi.repository.WarehouseRepository;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.security.JwtService;
import com.zuqi.service.ActivityLogService;
import com.zuqi.service.BranchService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BranchServiceImpl implements BranchService {

    private final DistributorBranchRepository branchRepository;
    private final BranchUserRepository branchUserRepository;
    private final DistributorRepository distributorRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final JwtService jwtService;
    private final SecurityUtils securityUtils;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public BranchResponse createBranch(BranchRequest request, UUID distributorId, UUID createdByUserId) {
        // SUPER_ADMIN passes distributorId in the request body; other roles use their own distributorId
        UUID effectiveDistributorId = (distributorId == null && request.getDistributorId() != null)
                ? request.getDistributorId()
                : distributorId;

        if (effectiveDistributorId == null) {
            throw new com.zuqi.exception.ValidationException("Distributor is required to create a branch");
        }

        Distributor distributor = distributorRepository.findById(effectiveDistributorId)
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", effectiveDistributorId));

        if (request.getCode() != null && branchRepository.existsByCodeAndDistributorId(request.getCode(), effectiveDistributorId)) {
            throw new ValidationException("Branch code already exists for this distributor");
        }

        User createdBy = userRepository.findById(createdByUserId).orElse(null);
        User manager = null;
        if (request.getManagerId() != null) {
            manager = userRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getManagerId()));
        }

        DistributorBranch branch = DistributorBranch.builder()
                .distributor(distributor)
                .name(request.getName())
                .code(request.getCode())
                .address(request.getAddress())
                .city(request.getCity())
                .phone(request.getPhone())
                .email(request.getEmail())
                .headquarters(request.isHeadquarters())
                .status(BranchStatus.ACTIVE)
                .manager(manager)
                .createdBy(createdBy)
                .build();

        branch = branchRepository.save(branch);
        log.info("Created branch {} for distributor {}", branch.getId(), distributorId);

        // Auto-create a warehouse for this branch using the same contact details
        Warehouse warehouse = Warehouse.builder()
                .distributor(distributor)
                .branch(branch)
                .name(request.getName() + " - Warehouse")
                .code(request.getCode())
                .address(request.getAddress())
                .city(request.getCity())
                .active(true)
                .build();
        warehouseRepository.save(warehouse);
        log.info("Auto-created warehouse for branch {}", branch.getId());

        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.CREATE, "BRANCH", branch.getId(),
                branch.getName(), "BRANCHES", "Created branch: " + branch.getName()
            );
        }
        return mapToResponse(branch);
    }

    @Override
    public List<BranchResponse> getBranchesByDistributor(UUID distributorId) {
        if (distributorId == null) {
            // SUPER_ADMIN sees all branches
            return branchRepository.findAll().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }
        return branchRepository.findByDistributorId(distributorId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public BranchResponse getBranchById(UUID branchId) {
        DistributorBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", branchId));
        return mapToResponse(branch);
    }

    @Override
    @Transactional
    public BranchResponse updateBranch(UUID branchId, BranchRequest request) {
        DistributorBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", branchId));

        if (request.getCode() != null && !request.getCode().equals(branch.getCode()) &&
                branchRepository.existsByCodeAndDistributorId(request.getCode(), branch.getDistributor().getId())) {
            throw new ValidationException("Branch code already exists for this distributor");
        }

        branch.setName(request.getName());
        branch.setCode(request.getCode());
        branch.setAddress(request.getAddress());
        branch.setCity(request.getCity());
        branch.setPhone(request.getPhone());
        branch.setEmail(request.getEmail());
        branch.setHeadquarters(request.isHeadquarters());

        if (request.getManagerId() != null) {
            User manager = userRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getManagerId()));
            branch.setManager(manager);
        }

        DistributorBranch updatedBranch = branchRepository.save(branch);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.UPDATE, "BRANCH", updatedBranch.getId(),
                updatedBranch.getName(), "BRANCHES", "Updated branch: " + updatedBranch.getName()
            );
        }
        return mapToResponse(updatedBranch);
    }

    @Override
    @Transactional
    public void activateBranch(UUID branchId) {
        DistributorBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", branchId));
        branch.setStatus(BranchStatus.ACTIVE);
        branchRepository.save(branch);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.ACTIVATE, "BRANCH", branch.getId(),
                branch.getName(), "BRANCHES", "Activated branch: " + branch.getName()
            );
        }
    }

    @Override
    @Transactional
    public void deactivateBranch(UUID branchId) {
        DistributorBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", branchId));
        branch.setStatus(BranchStatus.INACTIVE);
        branchRepository.save(branch);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.DEACTIVATE, "BRANCH", branch.getId(),
                branch.getName(), "BRANCHES", "Deactivated branch: " + branch.getName()
            );
        }
    }

    @Override
    @Transactional
    public BranchUserResponse addUserToBranch(UUID branchId, BranchUserRequest request, UUID assignedByUserId) {
        DistributorBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", branchId));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        if (branchUserRepository.existsByBranchIdAndUserId(branchId, request.getUserId())) {
            throw new ValidationException("User is already assigned to this branch");
        }

        User assignedBy = userRepository.findById(assignedByUserId).orElse(null);

        BranchUser branchUser = BranchUser.builder()
                .branch(branch)
                .user(user)
                .role(request.getRole())
                .status(BranchUserStatus.ACTIVE)
                .assignedBy(assignedBy)
                .build();

        branchUser = branchUserRepository.save(branchUser);
        return mapToUserResponse(branchUser);
    }

    @Override
    public List<BranchUserResponse> getUsersByBranch(UUID branchId) {
        return branchUserRepository.findByBranchId(branchId).stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void removeUserFromBranch(UUID branchId, UUID userId) {
        BranchUser branchUser = branchUserRepository.findByBranchIdAndUserId(branchId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("BranchUser", "branchId/userId", branchId + "/" + userId));
        branchUserRepository.delete(branchUser);
    }

    @Override
    @Transactional
    public SwitchBranchResponse switchBranch(SwitchBranchRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        DistributorBranch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", request.getBranchId()));

        // Check user belongs to branch (or is a distributor admin)
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> r.getName().equals("DISTRIBUTOR_ADMIN") || r.getName().equals("SUPER_ADMIN"));

        if (!isAdmin && !branchUserRepository.existsByBranchIdAndUserId(request.getBranchId(), userId)) {
            throw new ValidationException("User is not assigned to the requested branch");
        }

        String token = jwtService.generateTokenWithBranch(user, branch.getId(), branch.isHeadquarters());

        return SwitchBranchResponse.builder()
                .accessToken(token)
                .expiresIn(jwtService.getAccessTokenExpiration())
                .branchId(branch.getId())
                .branchName(branch.getName())
                .build();
    }

    private BranchResponse mapToResponse(DistributorBranch branch) {
        return BranchResponse.builder()
                .id(branch.getId())
                .distributorId(branch.getDistributor().getId())
                .distributorName(branch.getDistributor().getName())
                .name(branch.getName())
                .code(branch.getCode())
                .address(branch.getAddress())
                .city(branch.getCity())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .status(branch.getStatus())
                .headquarters(branch.isHeadquarters())
                .managerId(branch.getManager() != null ? branch.getManager().getId() : null)
                .managerName(branch.getManager() != null ?
                        branch.getManager().getFirstName() + " " + branch.getManager().getLastName() : null)
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
                .build();
    }

    private BranchUserResponse mapToUserResponse(BranchUser bu) {
        return BranchUserResponse.builder()
                .id(bu.getId())
                .branchId(bu.getBranch().getId())
                .branchName(bu.getBranch().getName())
                .userId(bu.getUser().getId())
                .userName(bu.getUser().getFirstName() + " " + bu.getUser().getLastName())
                .userEmail(bu.getUser().getEmail())
                .role(bu.getRole())
                .status(bu.getStatus())
                .createdAt(bu.getCreatedAt())
                .build();
    }
}
