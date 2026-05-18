package com.zuqi.service.impl;

import com.zuqi.api.dto.approvalthreshold.ApprovalThresholdRequest;
import com.zuqi.api.dto.approvalthreshold.ApprovalThresholdResponse;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.approvalthreshold.ApprovalThreshold;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.ApprovalThresholdRepository;
import com.zuqi.service.ApprovalThresholdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ApprovalThresholdServiceImpl implements ApprovalThresholdService {

    private final ApprovalThresholdRepository repository;

    @Override
    public List<ApprovalThresholdResponse> getAll(UUID distributorId) {
        return repository.findByDistributorIdAndActiveTrue(distributorId)
                .stream()
                .map(ApprovalThresholdResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public ApprovalThresholdResponse getById(UUID id) {
        return ApprovalThresholdResponse.from(find(id));
    }

    @Override
    @Transactional
    public ApprovalThresholdResponse create(UUID distributorId, ApprovalThresholdRequest request) {
        ApprovalThreshold threshold = ApprovalThreshold.builder()
                .distributorId(distributorId)
                .workflowType(request.getWorkflowType())
                .minAmount(request.getMinAmount())
                .maxAmount(request.getMaxAmount())
                .requiredApprovals(request.getRequiredApprovals())
                .active(true)
                .build();
        return ApprovalThresholdResponse.from(repository.save(threshold));
    }

    @Override
    @Transactional
    public ApprovalThresholdResponse update(UUID id, ApprovalThresholdRequest request) {
        ApprovalThreshold threshold = find(id);
        threshold.setWorkflowType(request.getWorkflowType());
        threshold.setMinAmount(request.getMinAmount());
        threshold.setMaxAmount(request.getMaxAmount());
        threshold.setRequiredApprovals(request.getRequiredApprovals());
        return ApprovalThresholdResponse.from(repository.save(threshold));
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        ApprovalThreshold threshold = find(id);
        threshold.setActive(false);
        repository.save(threshold);
    }

    @Override
    public int getRequiredApprovals(UUID distributorId, ApprovalWorkflowType workflowType, BigDecimal amount) {
        return findThresholdApprovals(distributorId, workflowType, amount).orElse(1);
    }

    @Override
    public Optional<Integer> findThresholdApprovals(UUID distributorId, ApprovalWorkflowType workflowType, BigDecimal amount) {
        if (amount == null) return Optional.empty();
        List<ApprovalThreshold> matches = repository.findMatchingThresholds(distributorId, workflowType, amount);
        if (matches.isEmpty() && distributorId != null) {
            // Fall back to global (distributor-agnostic) thresholds
            matches = repository.findMatchingThresholds(null, workflowType, amount);
        }
        if (matches.isEmpty()) return Optional.empty();
        return Optional.of(matches.get(0).getRequiredApprovals());
    }

    private ApprovalThreshold find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalThreshold", "id", id));
    }
}
