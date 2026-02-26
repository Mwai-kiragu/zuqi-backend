package com.zuqi.service;

import com.zuqi.api.dto.gl.GlAccountRequest;
import com.zuqi.api.dto.gl.GlAccountResponse;
import com.zuqi.domain.user.User;

import java.util.List;
import java.util.UUID;

public interface GlAccountService {
    List<GlAccountResponse> getAll(UUID distributorId);
    GlAccountResponse getById(UUID id);
    GlAccountResponse create(UUID distributorId, GlAccountRequest request, User currentUser);
    GlAccountResponse update(UUID id, GlAccountRequest request, User currentUser);
    void deactivate(UUID id, User currentUser);
}
