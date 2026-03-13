package com.zuqi.service;

import com.zuqi.api.dto.branch.*;

import java.util.List;
import java.util.UUID;

public interface BranchService {

    BranchResponse createBranch(BranchRequest request, UUID distributorId, UUID createdByUserId);

    List<BranchResponse> getBranchesByDistributor(UUID distributorId);

    BranchResponse getBranchById(UUID branchId);

    BranchResponse updateBranch(UUID branchId, BranchRequest request);

    void activateBranch(UUID branchId);

    void deactivateBranch(UUID branchId);

    BranchUserResponse addUserToBranch(UUID branchId, BranchUserRequest request, UUID assignedByUserId);

    List<BranchUserResponse> getUsersByBranch(UUID branchId);

    void removeUserFromBranch(UUID branchId, UUID userId);

    SwitchBranchResponse switchBranch(SwitchBranchRequest request, UUID userId);
}
