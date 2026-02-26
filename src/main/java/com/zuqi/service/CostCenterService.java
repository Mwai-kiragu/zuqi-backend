package com.zuqi.service;

import com.zuqi.api.dto.gl.CostCenterRequest;
import com.zuqi.api.dto.gl.CostCenterResponse;
import com.zuqi.domain.user.User;

import java.util.List;
import java.util.UUID;

public interface CostCenterService {
    List<CostCenterResponse> getAll(UUID distributorId);
    CostCenterResponse getById(UUID id);
    CostCenterResponse create(UUID distributorId, CostCenterRequest request, User currentUser);
    CostCenterResponse update(UUID id, CostCenterRequest request, User currentUser);
    void deactivate(UUID id, User currentUser);
    void activate(UUID id, User currentUser);
}
