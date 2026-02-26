package com.zuqi.service.impl;

import com.zuqi.api.dto.gl.CostCenterRequest;
import com.zuqi.api.dto.gl.CostCenterResponse;
import com.zuqi.domain.gl.CostCenter;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.CostCenterRepository;
import com.zuqi.service.CostCenterService;
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
public class CostCenterServiceImpl implements CostCenterService {

    private final CostCenterRepository costCenterRepository;

    @Override
    public List<CostCenterResponse> getAll(UUID distributorId) {
        return costCenterRepository.findByDistributorIdOrderByCodeAsc(distributorId)
                .stream()
                .map(CostCenterResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public CostCenterResponse getById(UUID id) {
        return CostCenterResponse.fromEntity(findById(id));
    }

    @Override
    @Transactional
    public CostCenterResponse create(UUID distributorId, CostCenterRequest request, User currentUser) {
        if (costCenterRepository.existsByDistributorIdAndCode(distributorId, request.getCode())) {
            throw new ValidationException("Cost center code '" + request.getCode() + "' already exists");
        }
        CostCenter cc = CostCenter.builder()
                .distributorId(distributorId)
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .parentId(request.getParentId())
                .active(true)
                .build();
        return CostCenterResponse.fromEntity(costCenterRepository.save(cc));
    }

    @Override
    @Transactional
    public CostCenterResponse update(UUID id, CostCenterRequest request, User currentUser) {
        CostCenter cc = findById(id);
        if (!cc.getCode().equals(request.getCode()) &&
                costCenterRepository.existsByDistributorIdAndCode(cc.getDistributorId(), request.getCode())) {
            throw new ValidationException("Cost center code '" + request.getCode() + "' already exists");
        }
        cc.setCode(request.getCode());
        cc.setName(request.getName());
        cc.setDescription(request.getDescription());
        cc.setParentId(request.getParentId());
        return CostCenterResponse.fromEntity(costCenterRepository.save(cc));
    }

    @Override
    @Transactional
    public void deactivate(UUID id, User currentUser) {
        CostCenter cc = findById(id);
        cc.setActive(false);
        costCenterRepository.save(cc);
    }

    @Override
    @Transactional
    public void activate(UUID id, User currentUser) {
        CostCenter cc = findById(id);
        cc.setActive(true);
        costCenterRepository.save(cc);
    }

    private CostCenter findById(UUID id) {
        return costCenterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CostCenter", "id", id));
    }
}
