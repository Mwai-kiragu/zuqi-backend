package com.zuqi.service.impl;

import com.zuqi.api.dto.inventory.*;
import com.zuqi.domain.branch.DistributorBranch;
import com.zuqi.domain.inventory.*;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.StockTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StockTransferServiceImpl implements StockTransferService {

    private final StockTransferRepository transferRepository;
    private final StockTransferItemRepository transferItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final DistributorBranchRepository branchRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final com.zuqi.util.SecurityUtils securityUtils;


    @Override
    @Transactional
    public StockTransferResponse createTransfer(StockTransferRequest request, UUID requestedByUserId) {
        Warehouse sourceWarehouse = warehouseRepository.findById(request.getSourceWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", request.getSourceWarehouseId()));

        Warehouse destinationWarehouse = warehouseRepository.findById(request.getDestinationWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", request.getDestinationWarehouseId()));

        if (request.getSourceWarehouseId().equals(request.getDestinationWarehouseId())) {
            throw new ValidationException("Source and destination warehouses must be different");
        }

        User requestedBy = userRepository.findById(requestedByUserId).orElse(null);

        DistributorBranch sourceBranch = null;
        if (request.getSourceBranchId() != null) {
            sourceBranch = branchRepository.findById(request.getSourceBranchId()).orElse(null);
        }

        DistributorBranch destinationBranch = null;
        if (request.getDestinationBranchId() != null) {
            destinationBranch = branchRepository.findById(request.getDestinationBranchId()).orElse(null);
        }

        StockTransfer transfer = StockTransfer.builder()
                .referenceNumber(generateTransferRef(sourceWarehouse.getDistributor().getName()))
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .sourceBranch(sourceBranch)
                .destinationBranch(destinationBranch)
                .status(StockTransferStatus.PENDING)
                .notes(request.getNotes())
                .requestedBy(requestedBy)
                .build();

        for (StockTransferItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemReq.getProductId()));

            StockTransferItem item = StockTransferItem.builder()
                    .transfer(transfer)
                    .product(product)
                    .requestedQuantity(itemReq.getRequestedQuantity())
                    .notes(itemReq.getNotes())
                    .build();
            transfer.getItems().add(item);
        }

        transfer = transferRepository.save(transfer);
        log.info("Created stock transfer {}", transfer.getReferenceNumber());
        return mapToResponse(transfer);
    }

    @Override
    public Page<StockTransferResponse> getTransfers(String status, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        boolean hasDates = startDate != null && endDate != null;
        LocalDateTime from = hasDates ? startDate.atStartOfDay() : null;
        LocalDateTime to = hasDates ? endDate.plusDays(1).atStartOfDay() : null;

        UUID effectiveBranchId = securityUtils.getEffectiveBranchId();
        if (effectiveBranchId != null) {
            if (hasDates)
                return transferRepository.findByBranchIdAndDateRange(effectiveBranchId, from, to, pageable).map(this::mapToResponse);
            return transferRepository.findBySourceBranchIdOrDestinationBranchId(effectiveBranchId, effectiveBranchId, pageable).map(this::mapToResponse);
        }

        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            if (status != null && !status.isBlank()) {
                StockTransferStatus transferStatus = StockTransferStatus.valueOf(status.toUpperCase());
                if (hasDates)
                    return transferRepository.findByMerchantIdAndStatusAndDateRange(merchantId, transferStatus, from, to, pageable).map(this::mapToResponse);
                return transferRepository.findByMerchantIdAndStatus(merchantId, transferStatus, pageable).map(this::mapToResponse);
            }
            if (hasDates)
                return transferRepository.findByMerchantIdAndDateRange(merchantId, from, to, pageable).map(this::mapToResponse);
            return transferRepository.findByMerchantId(merchantId, pageable).map(this::mapToResponse);
        }

        // HQ distributor or SUPER_ADMIN
        if (status != null && !status.isBlank()) {
            StockTransferStatus transferStatus = StockTransferStatus.valueOf(status.toUpperCase());
            if (hasDates)
                return transferRepository.findByStatusAndCreatedAtBetween(transferStatus, from, to, pageable).map(this::mapToResponse);
            return transferRepository.findByStatus(transferStatus, pageable).map(this::mapToResponse);
        }
        if (hasDates)
            return transferRepository.findByCreatedAtBetween(from, to, pageable).map(this::mapToResponse);
        return transferRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    public StockTransferResponse getTransferById(UUID transferId) {
        StockTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("StockTransfer", "id", transferId));
        return mapToResponse(transfer);
    }

    @Override
    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, UUID approvedByUserId) {
        StockTransfer transfer = getTransferEntity(transferId);
        if (transfer.getStatus() != StockTransferStatus.PENDING) {
            throw new ValidationException("Only PENDING transfers can be approved");
        }

        User approvedBy = userRepository.findById(approvedByUserId).orElse(null);
        transfer.setStatus(StockTransferStatus.APPROVED);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(LocalDateTime.now());

        return mapToResponse(transferRepository.save(transfer));
    }

    @Override
    @Transactional
    public StockTransferResponse cancelTransfer(UUID transferId, String reason) {
        StockTransfer transfer = getTransferEntity(transferId);
        if (transfer.getStatus() == StockTransferStatus.RECEIVED) {
            throw new ValidationException("Cannot cancel a received transfer");
        }

        transfer.setStatus(StockTransferStatus.CANCELLED);
        transfer.setNotes(reason);

        return mapToResponse(transferRepository.save(transfer));
    }

    @Override
    @Transactional
    public StockTransferResponse receiveTransfer(UUID transferId, UUID receivedByUserId) {
        StockTransfer transfer = getTransferEntity(transferId);
        if (transfer.getStatus() != StockTransferStatus.APPROVED && transfer.getStatus() != StockTransferStatus.IN_TRANSIT) {
            throw new ValidationException("Transfer must be APPROVED or IN_TRANSIT to be received");
        }

        User receivedBy = userRepository.findById(receivedByUserId).orElse(null);

        for (StockTransferItem item : transfer.getItems()) {
            BigDecimal qty = item.getReceivedQuantity() != null ?
                    item.getReceivedQuantity() : item.getRequestedQuantity();

            // Decrement source stock
            stockRepository.findByWarehouseIdAndProductId(
                    transfer.getSourceWarehouse().getId(), item.getProduct().getId())
                    .ifPresent(sourceStock -> {
                        sourceStock.setQuantity(sourceStock.getQuantity().subtract(qty));
                        stockRepository.save(sourceStock);
                    });

            // Increment destination stock
            Stock destStock = stockRepository.findByWarehouseIdAndProductId(
                    transfer.getDestinationWarehouse().getId(), item.getProduct().getId())
                    .orElseGet(() -> Stock.builder()
                            .warehouse(transfer.getDestinationWarehouse())
                            .product(item.getProduct())
                            .quantity(java.math.BigDecimal.ZERO)
                            .reservedQuantity(java.math.BigDecimal.ZERO)
                            .build());

            destStock.setQuantity(destStock.getQuantity().add(qty));
            stockRepository.save(destStock);

            // Record movements
            StockMovement outMovement = StockMovement.builder()
                    .warehouse(transfer.getSourceWarehouse())
                    .product(item.getProduct())
                    .movementType(StockMovement.MovementType.TRANSFER)
                    .quantity(qty.negate())
                    .referenceType("STOCK_TRANSFER")
                    .referenceId(transfer.getId())
                    .notes("Transfer out: " + transfer.getReferenceNumber())
                    .createdBy(receivedBy)
                    .build();
            stockMovementRepository.save(outMovement);

            StockMovement inMovement = StockMovement.builder()
                    .warehouse(transfer.getDestinationWarehouse())
                    .product(item.getProduct())
                    .movementType(StockMovement.MovementType.TRANSFER)
                    .quantity(qty)
                    .referenceType("STOCK_TRANSFER")
                    .referenceId(transfer.getId())
                    .notes("Transfer in: " + transfer.getReferenceNumber())
                    .createdBy(receivedBy)
                    .build();
            stockMovementRepository.save(inMovement);
        }

        transfer.setStatus(StockTransferStatus.RECEIVED);
        transfer.setReceivedBy(receivedBy);
        transfer.setReceivedAt(LocalDateTime.now());

        return mapToResponse(transferRepository.save(transfer));
    }

    private StockTransfer getTransferEntity(UUID id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockTransfer", "id", id));
    }

    private String generateTransferRef(String distributorName) {
        String prefix = initials(distributorName) + "-TRF-";
        Integer maxNum = transferRepository.findMaxTransferNumberByPrefix(prefix);
        int nextNum = (maxNum != null ? maxNum : 0) + 1;
        return prefix + String.format("%02d", nextNum);
    }

    /** Extracts uppercase initials — e.g. "Menace Distributor" → "MD". */
    static String initials(String name) {
        if (name == null || name.isBlank()) return "ORG";
        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank()) sb.append(Character.toUpperCase(w.charAt(0)));
        }
        String result = sb.toString();
        if (result.length() == 1 && words[0].length() >= 2) {
            result = words[0].substring(0, 2).toUpperCase();
        }
        return result.isEmpty() ? "ORG" : result;
    }

    private StockTransferResponse mapToResponse(StockTransfer transfer) {
        List<StockTransferItemResponse> items = transfer.getItems().stream()
                .map(i -> StockTransferItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productName(i.getProduct().getName())
                        .requestedQuantity(i.getRequestedQuantity())
                        .receivedQuantity(i.getReceivedQuantity())
                        .notes(i.getNotes())
                        .build())
                .collect(Collectors.toList());

        return StockTransferResponse.builder()
                .id(transfer.getId())
                .referenceNumber(transfer.getReferenceNumber())
                .sourceWarehouseId(transfer.getSourceWarehouse().getId())
                .sourceWarehouseName(transfer.getSourceWarehouse().getName())
                .destinationWarehouseId(transfer.getDestinationWarehouse().getId())
                .destinationWarehouseName(transfer.getDestinationWarehouse().getName())
                .sourceBranchId(transfer.getSourceBranch() != null ? transfer.getSourceBranch().getId() : null)
                .sourceBranchName(transfer.getSourceBranch() != null ? transfer.getSourceBranch().getName() : null)
                .destinationBranchId(transfer.getDestinationBranch() != null ? transfer.getDestinationBranch().getId() : null)
                .destinationBranchName(transfer.getDestinationBranch() != null ? transfer.getDestinationBranch().getName() : null)
                .status(transfer.getStatus())
                .notes(transfer.getNotes())
                .items(items)
                .requestedById(transfer.getRequestedBy() != null ? transfer.getRequestedBy().getId() : null)
                .requestedByName(transfer.getRequestedBy() != null ?
                        transfer.getRequestedBy().getFirstName() + " " + transfer.getRequestedBy().getLastName() : null)
                .approvedById(transfer.getApprovedBy() != null ? transfer.getApprovedBy().getId() : null)
                .approvedByName(transfer.getApprovedBy() != null ?
                        transfer.getApprovedBy().getFirstName() + " " + transfer.getApprovedBy().getLastName() : null)
                .approvedAt(transfer.getApprovedAt())
                .dispatchedAt(transfer.getDispatchedAt())
                .receivedAt(transfer.getReceivedAt())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .build();
    }
}
