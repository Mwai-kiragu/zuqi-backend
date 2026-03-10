package com.zuqi.service.impl;

import com.zuqi.api.dto.inventory.*;
import com.zuqi.domain.branch.DistributorBranch;
import com.zuqi.domain.inventory.*;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.StockTakeService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StockTakeServiceImpl implements StockTakeService {

    private final StockTakeBatchRepository batchRepository;
    private final StockTakeItemRepository itemRepository;
    private final WarehouseRepository warehouseRepository;
    private final DistributorBranchRepository branchRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    private static final AtomicInteger batchCounter = new AtomicInteger(1);

    @Override
    @Transactional
    public StockTakeResponse createStockTake(StockTakeRequest request, UUID createdByUserId) {
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", request.getWarehouseId()));

        User createdBy = userRepository.findById(createdByUserId).orElse(null);

        DistributorBranch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId()).orElse(null);
        }

        StockTakeBatch batch = StockTakeBatch.builder()
                .referenceNumber(generateBatchRef())
                .warehouse(warehouse)
                .branch(branch)
                .status(StockTakeBatchStatus.DRAFT)
                .notes(request.getNotes())
                .createdBy(createdBy)
                .startedAt(LocalDateTime.now())
                .build();

        // Snapshot current stock for all products in warehouse
        List<Stock> stocks = stockRepository.findByWarehouseId(warehouse.getId(), Pageable.unpaged()).getContent();
        for (Stock stock : stocks) {
            StockTakeItem item = StockTakeItem.builder()
                    .batch(batch)
                    .product(stock.getProduct())
                    .systemQuantity(stock.getQuantity())
                    .build();
            batch.getItems().add(item);
        }

        batch = batchRepository.save(batch);
        log.info("Created stock take batch {} for warehouse {}", batch.getReferenceNumber(), warehouse.getId());
        return mapToResponse(batch);
    }

    @Override
    public Page<StockTakeResponse> getStockTakesByWarehouse(UUID warehouseId, Pageable pageable) {
        UUID effectiveBranchId = securityUtils.getEffectiveBranchId();

        // Specific branch scope (non-HQ branch context)
        if (effectiveBranchId != null) {
            return batchRepository.findByBranchId(effectiveBranchId, pageable).map(this::mapToResponse);
        }

        // Specific warehouse filter requested by the user
        if (warehouseId != null) {
            return batchRepository.findByWarehouseId(warehouseId, pageable).map(this::mapToResponse);
        }

        // No warehouse filter — scope by identity
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            // MERCHANT_ADMIN: only their merchant's stock takes
            return batchRepository.findByWarehouseDistributorMerchantId(merchantId, pageable).map(this::mapToResponse);
        }

        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            // DISTRIBUTOR_ADMIN / staff: only their distributor's stock takes
            return batchRepository.findByWarehouseDistributorId(distributorId, pageable).map(this::mapToResponse);
        }

        // SUPER_ADMIN: return all
        return batchRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    public StockTakeResponse getStockTakeById(UUID batchId) {
        StockTakeBatch batch = getBatchEntity(batchId);
        return mapToResponse(batch);
    }

    @Override
    @Transactional
    public StockTakeResponse updateItemCount(UUID batchId, UUID productId, StockTakeItemUpdate update) {
        StockTakeBatch batch = getBatchEntity(batchId);
        if (batch.getStatus() == StockTakeBatchStatus.APPROVED || batch.getStatus() == StockTakeBatchStatus.POSTED) {
            throw new ValidationException("Cannot update items in an approved/posted stock take");
        }

        StockTakeItem item = itemRepository.findByBatchIdAndProductId(batchId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("StockTakeItem", "batchId/productId",
                        batchId + "/" + productId));

        item.setCountedQuantity(update.getCountedQuantity());
        item.setNotes(update.getNotes());
        item.recalculateVariance();

        if (batch.getStatus() == StockTakeBatchStatus.DRAFT) {
            batch.setStatus(StockTakeBatchStatus.IN_PROGRESS);
            batchRepository.save(batch);
        }

        itemRepository.save(item);
        return mapToResponse(getBatchEntity(batchId));
    }

    @Override
    @Transactional
    public StockTakeResponse completeStockTake(UUID batchId) {
        StockTakeBatch batch = getBatchEntity(batchId);
        if (batch.getStatus() != StockTakeBatchStatus.IN_PROGRESS && batch.getStatus() != StockTakeBatchStatus.DRAFT) {
            throw new ValidationException("Stock take must be DRAFT or IN_PROGRESS to complete");
        }

        batch.setStatus(StockTakeBatchStatus.COMPLETED);
        batch.setCompletedAt(LocalDateTime.now());

        return mapToResponse(batchRepository.save(batch));
    }

    @Override
    @Transactional
    public StockTakeResponse approveStockTake(UUID batchId, UUID approvedByUserId) {
        StockTakeBatch batch = getBatchEntity(batchId);
        if (batch.getStatus() != StockTakeBatchStatus.COMPLETED) {
            throw new ValidationException("Stock take must be COMPLETED before approval");
        }

        User approvedBy = userRepository.findById(approvedByUserId).orElse(null);

        // Post variances to stock
        for (StockTakeItem item : batch.getItems()) {
            if (item.getVariance() != null && item.getVariance().compareTo(BigDecimal.ZERO) != 0) {
                stockRepository.findByWarehouseIdAndProductId(batch.getWarehouse().getId(), item.getProduct().getId())
                        .ifPresent(stock -> {
                            BigDecimal newQty = stock.getQuantity().add(item.getVariance());
                            stock.setQuantity(newQty.max(BigDecimal.ZERO));
                            stockRepository.save(stock);

                            StockMovement movement = StockMovement.builder()
                                    .warehouse(batch.getWarehouse())
                                    .product(item.getProduct())
                                    .movementType(StockMovement.MovementType.ADJUSTMENT)
                                    .quantity(item.getVariance())
                                    .referenceType("STOCK_TAKE")
                                    .referenceId(batch.getId())
                                    .notes("Stock take variance: " + batch.getReferenceNumber())
                                    .createdBy(approvedBy)
                                    .build();
                            stockMovementRepository.save(movement);
                        });
            }
        }

        batch.setStatus(StockTakeBatchStatus.APPROVED);
        batch.setApprovedBy(approvedBy);
        batch.setApprovedAt(LocalDateTime.now());

        return mapToResponse(batchRepository.save(batch));
    }

    @Override
    @Transactional
    public StockTakeResponse cancelStockTake(UUID batchId) {
        StockTakeBatch batch = getBatchEntity(batchId);
        if (batch.getStatus() == StockTakeBatchStatus.APPROVED || batch.getStatus() == StockTakeBatchStatus.POSTED) {
            throw new ValidationException("Cannot cancel an approved/posted stock take");
        }

        batchRepository.delete(batch);
        // Return a minimal response indicating cancellation
        return StockTakeResponse.builder()
                .id(batchId)
                .referenceNumber(batch.getReferenceNumber())
                .status(StockTakeBatchStatus.DRAFT)
                .notes("Cancelled")
                .build();
    }

    private StockTakeBatch getBatchEntity(UUID batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("StockTakeBatch", "id", batchId));
    }

    private String generateBatchRef() {
        return "STK-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now())
                + "-" + String.format("%05d", batchCounter.getAndIncrement());
    }

    private StockTakeResponse mapToResponse(StockTakeBatch batch) {
        List<StockTakeItemResponse> items = batch.getItems().stream()
                .map(i -> StockTakeItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productName(i.getProduct().getName())
                        .systemQuantity(i.getSystemQuantity())
                        .countedQuantity(i.getCountedQuantity())
                        .variance(i.getVariance())
                        .notes(i.getNotes())
                        .build())
                .collect(Collectors.toList());

        return StockTakeResponse.builder()
                .id(batch.getId())
                .referenceNumber(batch.getReferenceNumber())
                .warehouseId(batch.getWarehouse() != null ? batch.getWarehouse().getId() : null)
                .warehouseName(batch.getWarehouse() != null ? batch.getWarehouse().getName() : null)
                .branchId(batch.getBranch() != null ? batch.getBranch().getId() : null)
                .branchName(batch.getBranch() != null ? batch.getBranch().getName() : null)
                .status(batch.getStatus())
                .notes(batch.getNotes())
                .items(items)
                .createdById(batch.getCreatedBy() != null ? batch.getCreatedBy().getId() : null)
                .createdByName(batch.getCreatedBy() != null ?
                        batch.getCreatedBy().getFirstName() + " " + batch.getCreatedBy().getLastName() : null)
                .approvedById(batch.getApprovedBy() != null ? batch.getApprovedBy().getId() : null)
                .approvedByName(batch.getApprovedBy() != null ?
                        batch.getApprovedBy().getFirstName() + " " + batch.getApprovedBy().getLastName() : null)
                .startedAt(batch.getStartedAt())
                .completedAt(batch.getCompletedAt())
                .approvedAt(batch.getApprovedAt())
                .createdAt(batch.getCreatedAt())
                .updatedAt(batch.getUpdatedAt())
                .build();
    }
}
