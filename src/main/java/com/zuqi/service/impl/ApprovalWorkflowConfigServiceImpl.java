package com.zuqi.service.impl;

import com.zuqi.api.dto.approval.ApprovalWorkflowConfigRequest;
import com.zuqi.api.dto.approval.ApprovalWorkflowConfigResponse;
import com.zuqi.domain.approval.ApprovalWorkflowConfig;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.ApprovalWorkflowConfigRepository;
import com.zuqi.service.ApprovalWorkflowConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalWorkflowConfigServiceImpl implements ApprovalWorkflowConfigService {

    private final ApprovalWorkflowConfigRepository repository;

    @Override
    public List<ApprovalWorkflowConfigResponse> getAll(UUID distributorId) {
        return repository.findByDistributorIdOrderByWorkflowTypeAscLevelNumberAsc(distributorId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<ApprovalWorkflowConfigResponse> getByWorkflowType(UUID distributorId, ApprovalWorkflowType type) {
        return repository.findByDistributorIdAndWorkflowTypeAndActiveTrueOrderByLevelNumberAsc(distributorId, type)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public int countActiveLevels(UUID distributorId, ApprovalWorkflowType type) {
        if (distributorId == null) return 0;
        return repository.countByDistributorIdAndWorkflowTypeAndActiveTrue(distributorId, type);
    }

    @Override
    @Transactional
    public ApprovalWorkflowConfigResponse create(UUID distributorId, ApprovalWorkflowConfigRequest request) {
        if (repository.existsByDistributorIdAndWorkflowTypeAndLevelNumber(
                distributorId, request.getWorkflowType(), request.getLevelNumber())) {
            throw new ValidationException(
                    "Level " + request.getLevelNumber() + " already exists for this workflow type");
        }

        ApprovalWorkflowConfig config = ApprovalWorkflowConfig.builder()
                .distributorId(distributorId)
                .workflowType(request.getWorkflowType())
                .levelNumber(request.getLevelNumber())
                .roleLabel(request.getRoleLabel())
                .requiredRole(request.getRequiredRole())
                .active(true)
                .build();

        return toResponse(repository.save(config));
    }

    @Override
    @Transactional
    public ApprovalWorkflowConfigResponse update(UUID id, ApprovalWorkflowConfigRequest request) {
        ApprovalWorkflowConfig config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalWorkflowConfig", "id", id));

        config.setRoleLabel(request.getRoleLabel());
        config.setRequiredRole(request.getRequiredRole());
        // levelNumber and workflowType are immutable after creation

        return toResponse(repository.save(config));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("ApprovalWorkflowConfig", "id", id);
        }
        repository.deleteById(id);
    }

    private ApprovalWorkflowConfigResponse toResponse(ApprovalWorkflowConfig c) {
        return ApprovalWorkflowConfigResponse.builder()
                .id(c.getId())
                .distributorId(c.getDistributorId())
                .workflowType(c.getWorkflowType())
                .levelNumber(c.getLevelNumber())
                .roleLabel(c.getRoleLabel())
                .requiredRole(c.getRequiredRole())
                .active(c.isActive())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
