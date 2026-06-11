package com.zuqi.service.impl;

import com.zuqi.api.dto.gl.CostCenterBulkItemRequest;
import com.zuqi.api.dto.gl.CostCenterBulkResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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

    @Override
    @Transactional
    public CostCenterBulkResponse bulkCreate(UUID distributorId, List<CostCenterBulkItemRequest> items, User currentUser) {
        // Pre-load existing codes for fast duplicate checking within the batch
        Map<String, CostCenter> existingByCode = costCenterRepository
                .findByDistributorIdOrderByCodeAsc(distributorId)
                .stream()
                .collect(Collectors.toMap(CostCenter::getCode, Function.identity()));

        List<CostCenterBulkResponse.RowError> errors = new ArrayList<>();
        int created = 0;
        int skipped = 0;

        for (int i = 0; i < items.size(); i++) {
            CostCenterBulkItemRequest item = items.get(i);
            int rowNum = i + 1;

            if (item.getCode() == null || item.getCode().isBlank()) {
                errors.add(CostCenterBulkResponse.RowError.builder()
                        .row(rowNum).code("").reason("Code is required").build());
                skipped++;
                continue;
            }
            if (item.getName() == null || item.getName().isBlank()) {
                errors.add(CostCenterBulkResponse.RowError.builder()
                        .row(rowNum).code(item.getCode()).reason("Name is required").build());
                skipped++;
                continue;
            }

            String code = item.getCode().trim().toUpperCase();

            if (existingByCode.containsKey(code)) {
                errors.add(CostCenterBulkResponse.RowError.builder()
                        .row(rowNum).code(code).reason("Code already exists — skipped").build());
                skipped++;
                continue;
            }

            // Resolve parentCode → parentId
            UUID parentId = null;
            if (item.getParentCode() != null && !item.getParentCode().isBlank()) {
                String parentCode = item.getParentCode().trim().toUpperCase();
                CostCenter parent = existingByCode.get(parentCode);
                if (parent == null) {
                    errors.add(CostCenterBulkResponse.RowError.builder()
                            .row(rowNum).code(code)
                            .reason("Parent code '" + parentCode + "' not found — row skipped").build());
                    skipped++;
                    continue;
                }
                parentId = parent.getId();
            }

            try {
                CostCenter cc = CostCenter.builder()
                        .distributorId(distributorId)
                        .code(code)
                        .name(item.getName().trim())
                        .description(item.getDescription())
                        .parentId(parentId)
                        .active(true)
                        .build();
                CostCenter saved = costCenterRepository.save(cc);
                // Add to in-memory map so later rows in the same batch can resolve this as a parent
                existingByCode.put(code, saved);
                created++;
            } catch (Exception e) {
                log.warn("Failed to save cost centre row {}: {}", rowNum, e.getMessage());
                errors.add(CostCenterBulkResponse.RowError.builder()
                        .row(rowNum).code(code).reason("Save failed: " + e.getMessage()).build());
            }
        }

        log.info("Bulk cost centre import for distributor {}: created={}, skipped={}", distributorId, created, skipped);
        return CostCenterBulkResponse.builder()
                .created(created)
                .skipped(skipped)
                .failed((int) errors.stream().filter(e -> !e.getReason().contains("already exists")).count())
                .errors(errors)
                .build();
    }

    private CostCenter findById(UUID id) {
        return costCenterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CostCenter", "id", id));
    }
}
