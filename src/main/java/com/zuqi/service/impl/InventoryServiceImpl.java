package com.zuqi.service.impl;

import com.zuqi.api.dto.inventory.*;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.StockMovement;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.domain.branch.DistributorBranch;
import com.zuqi.repository.*;
import com.zuqi.ai.event.StockAdjustedEvent;
import com.zuqi.ai.feature.FeatureStore;
import com.zuqi.service.InventoryService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final DistributorRepository distributorRepository;
    private final DistributorBranchRepository branchRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final ApplicationEventPublisher eventPublisher;
    private final FeatureStore featureStore;


    @Override
    public Page<StockResponse> getStock(UUID distributorId, UUID warehouseId, Pageable pageable) {
        return stockRepository.findByFilters(distributorId, warehouseId, pageable)
                .map(this::mapToStockResponse);
    }

    @Override
    public Page<StockResponse> getStockByWarehouse(UUID warehouseId, Pageable pageable) {
        validateWarehouseExists(warehouseId);
        return stockRepository.findByWarehouseId(warehouseId, pageable)
                .map(this::mapToStockResponse);
    }

    @Override
    public StockResponse getStockByWarehouseAndProduct(UUID warehouseId, UUID productId) {
        Stock stock = stockRepository.findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Stock", "warehouse and product", warehouseId + "/" + productId));
        return mapToStockResponse(stock);
    }

    @Override
    public List<StockResponse> getStockByProduct(UUID productId) {
        validateProductExists(productId);
        return stockRepository.findByProductId(productId).stream()
                .map(this::mapToStockResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StockResponse adjustStock(StockAdjustmentRequest request, UUID userId) {
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", request.getWarehouseId()));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Get or create stock record
        Stock stock = stockRepository.findByWarehouseIdAndProductId(
                request.getWarehouseId(), request.getProductId())
                .orElseGet(() -> Stock.builder()
                        .warehouse(warehouse)
                        .product(product)
                        .quantity(BigDecimal.ZERO)
                        .reservedQuantity(BigDecimal.ZERO)
                        .build());

        BigDecimal previousQuantity = stock.getQuantity();

        // Calculate new quantity based on movement type
        BigDecimal newQuantity;
        switch (request.getMovementType()) {
            case IN:
                newQuantity = stock.getQuantity().add(request.getQuantity());
                break;
            case OUT:
                newQuantity = stock.getQuantity().subtract(request.getQuantity());
                if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ValidationException("Insufficient stock. Available: " + stock.getQuantity());
                }
                break;
            case ADJUSTMENT:
                newQuantity = request.getQuantity(); // Direct set for adjustments
                break;
            case TRANSFER:
                // For transfer, this is the source - subtract
                newQuantity = stock.getQuantity().subtract(request.getQuantity());
                if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
                    throw new ValidationException("Insufficient stock for transfer. Available: " + stock.getQuantity());
                }
                break;
            default:
                throw new ValidationException("Invalid movement type");
        }

        stock.setQuantity(newQuantity);
        stock.setLastStockCheck(LocalDateTime.now());
        Stock savedStock = stockRepository.save(stock);

        // Create movement record
        StockMovement movement = StockMovement.builder()
                .warehouse(warehouse)
                .product(product)
                .movementType(request.getMovementType())
                .quantity(request.getQuantity())
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .notes(request.getNotes())
                .createdBy(user)
                .build();
        stockMovementRepository.save(movement);

        log.info("Stock adjusted - Warehouse: {}, Product: {}, Type: {}, Qty: {}, New Balance: {}",
                warehouse.getName(), product.getName(), request.getMovementType(),
                request.getQuantity(), newQuantity);

        // Invalidate inventory feature cache for this warehouse (affects shrinkage detection)
        featureStore.invalidateWarehouseCache(warehouse.getId());
        log.debug("Invalidated feature cache for warehouse {} after stock adjustment", warehouse.getId());

        // Publish AI event for shrinkage detection and stockout prediction
        publishStockAdjustedEvent(savedStock, previousQuantity, request);

        return mapToStockResponse(savedStock);
    }

    @Override
    public Page<StockResponse> getLowStock(UUID distributorId, Pageable pageable) {
        // Determine effective distributor ID for filtering
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        if (effectiveDistributorId != null) {
            validateDistributorExists(effectiveDistributorId);
            return stockRepository.findLowStockByDistributorId(effectiveDistributorId, pageable)
                    .map(this::mapToStockResponse);
        }

        // SUPER_ADMIN/ADMIN can see all low stock items
        return stockRepository.findAllLowStock(pageable)
                .map(this::mapToStockResponse);
    }


    @Override
    public List<WarehouseResponse> getWarehousesByDistributor(UUID distributorId) {
        // Determine effective distributor ID for filtering
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        if (effectiveDistributorId != null) {
            validateDistributorExists(effectiveDistributorId);
            return warehouseRepository.findByDistributorIdAndActiveTrue(effectiveDistributorId).stream()
                    .map(this::mapToWarehouseResponse)
                    .collect(Collectors.toList());
        }

        // SUPER_ADMIN/ADMIN can see all warehouses
        return warehouseRepository.findByActiveTrue().stream()
                .map(this::mapToWarehouseResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<WarehouseResponse> getWarehousesByBranch(UUID branchId) {
        return warehouseRepository.findByBranchIdAndActiveTrue(branchId).stream()
                .map(this::mapToWarehouseResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<WarehouseResponse> getWarehousesByDistributor(UUID distributorId, Pageable pageable) {
        // Determine effective distributor ID for filtering
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        if (effectiveDistributorId != null) {
            validateDistributorExists(effectiveDistributorId);
            return warehouseRepository.findByDistributorIdAndActiveTrue(effectiveDistributorId, pageable)
                    .map(this::mapToWarehouseResponse);
        }

        // SUPER_ADMIN/ADMIN can see all warehouses
        return warehouseRepository.findByActiveTrue(pageable)
                .map(this::mapToWarehouseResponse);
    }

    @Override
    public Page<WarehouseResponse> getInactiveWarehousesByDistributor(UUID distributorId, Pageable pageable) {
        // Determine effective distributor ID for filtering
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        if (effectiveDistributorId != null) {
            validateDistributorExists(effectiveDistributorId);
            return warehouseRepository.findByDistributorIdAndActiveFalse(effectiveDistributorId, pageable)
                    .map(this::mapToWarehouseResponse);
        }

        // SUPER_ADMIN/ADMIN can see all inactive warehouses
        return warehouseRepository.findByActiveFalse(pageable)
                .map(this::mapToWarehouseResponse);
    }

    @Override
    public WarehouseResponse getWarehouseById(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", warehouseId));
        return mapToWarehouseResponse(warehouse);
    }

    @Override
    @Transactional
    public WarehouseResponse createWarehouse(WarehouseRequest request) {
        // Validate distributor
        Distributor distributor = distributorRepository.findById(request.getDistributorId())
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId()));

        // Check for duplicate code
        if (warehouseRepository.existsByCodeAndDistributorId(request.getCode(), request.getDistributorId())) {
            throw new ValidationException("Warehouse with code '" + request.getCode() + "' already exists");
        }

        Warehouse warehouse = Warehouse.builder()
                .code(request.getCode())
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .distributor(distributor)
                .active(request.isActive())
                .build();

        // Set branch if provided
        if (request.getBranchId() != null) {
            DistributorBranch branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", request.getBranchId()));
            warehouse.setBranch(branch);
        }

        // Set manager if provided
        if (request.getManagerId() != null) {
            User manager = userRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getManagerId()));
            warehouse.setManager(manager);
        }

        Warehouse savedWarehouse = warehouseRepository.save(warehouse);
        log.info("Created warehouse: {} ({})", savedWarehouse.getName(), savedWarehouse.getCode());

        return mapToWarehouseResponse(savedWarehouse);
    }

    @Override
    @Transactional
    public WarehouseResponse updateWarehouse(UUID warehouseId, WarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", warehouseId));

        // Check for duplicate code if changed
        if (!warehouse.getCode().equals(request.getCode()) &&
                warehouseRepository.existsByCodeAndDistributorId(request.getCode(), warehouse.getDistributor().getId())) {
            throw new ValidationException("Warehouse with code '" + request.getCode() + "' already exists");
        }

        warehouse.setCode(request.getCode());
        warehouse.setName(request.getName());
        warehouse.setAddress(request.getAddress());
        warehouse.setCity(request.getCity());
        warehouse.setLatitude(request.getLatitude());
        warehouse.setLongitude(request.getLongitude());
        warehouse.setActive(request.isActive());

        // Update branch if provided
        if (request.getBranchId() != null) {
            DistributorBranch branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", request.getBranchId()));
            warehouse.setBranch(branch);
        } else {
            warehouse.setBranch(null);
        }

        // Update manager if provided
        if (request.getManagerId() != null) {
            User manager = userRepository.findById(request.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getManagerId()));
            warehouse.setManager(manager);
        } else {
            warehouse.setManager(null);
        }

        Warehouse savedWarehouse = warehouseRepository.save(warehouse);
        log.info("Updated warehouse: {} ({})", savedWarehouse.getName(), savedWarehouse.getCode());

        return mapToWarehouseResponse(savedWarehouse);
    }

    @Override
    @Transactional
    public void deactivateWarehouse(UUID warehouseId, String reason, User currentUser) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", warehouseId));

        warehouse.setActive(false);
        warehouse.setDeactivationReason(reason);
        warehouse.setDeactivatedAt(LocalDateTime.now());
        warehouse.setDeactivatedBy(currentUser);
        warehouseRepository.save(warehouse);
        log.info("Deactivated warehouse: {} ({}) with reason: {}", warehouse.getName(), warehouse.getCode(), reason);
    }

    @Override
    @Transactional
    public void activateWarehouse(UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", warehouseId));

        warehouse.setActive(true);
        warehouse.setDeactivationReason(null);
        warehouse.setDeactivatedAt(null);
        warehouse.setDeactivatedBy(null);
        warehouseRepository.save(warehouse);
        log.info("Activated warehouse: {} ({})", warehouse.getName(), warehouse.getCode());
    }


    @Override
    public Page<StockMovementResponse> getMovementsByWarehouse(UUID warehouseId, Pageable pageable) {
        validateWarehouseExists(warehouseId);
        return stockMovementRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId, pageable)
                .map(this::mapToStockMovementResponse);
    }

    @Override
    public Page<StockMovementResponse> getMovementsByWarehouseAndProduct(UUID warehouseId, UUID productId, Pageable pageable) {
        return stockMovementRepository.findByWarehouseIdAndProductIdOrderByCreatedAtDesc(warehouseId, productId, pageable)
                .map(this::mapToStockMovementResponse);
    }

    @Override
    public Page<StockMovementResponse> getMovementsByDateRange(UUID warehouseId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        validateWarehouseExists(warehouseId);
        return stockMovementRepository.findByWarehouseIdAndDateRange(warehouseId, startDate, endDate, pageable)
                .map(this::mapToStockMovementResponse);
    }


    private void validateWarehouseExists(UUID warehouseId) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new ResourceNotFoundException("Warehouse", "id", warehouseId);
        }
    }

    private void validateProductExists(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", "id", productId);
        }
    }

    private void validateDistributorExists(UUID distributorId) {
        if (!distributorRepository.existsById(distributorId)) {
            throw new ResourceNotFoundException("Distributor", "id", distributorId);
        }
    }

    private StockResponse mapToStockResponse(Stock stock) {
        var distributor = stock.getWarehouse().getDistributor();
        return StockResponse.builder()
                .id(stock.getId())
                .distributorId(distributor != null ? distributor.getId() : null)
                .distributorName(distributor != null ? distributor.getName() : null)
                .warehouseId(stock.getWarehouse().getId())
                .warehouseName(stock.getWarehouse().getName())
                .warehouseCode(stock.getWarehouse().getCode())
                .productId(stock.getProduct().getId())
                .productName(stock.getProduct().getName())
                .productSku(stock.getProduct().getSku())
                .quantity(stock.getQuantity())
                .reservedQuantity(stock.getReservedQuantity())
                .availableQuantity(stock.getAvailableQuantity())
                .reorderLevel(stock.getReorderLevel())
                .lowStock(stock.isLowStock())
                .lastStockCheck(stock.getLastStockCheck())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }

    private WarehouseResponse mapToWarehouseResponse(Warehouse warehouse) {
        return WarehouseResponse.builder()
                .id(warehouse.getId())
                .code(warehouse.getCode())
                .name(warehouse.getName())
                .address(warehouse.getAddress())
                .city(warehouse.getCity())
                .latitude(warehouse.getLatitude())
                .longitude(warehouse.getLongitude())
                .distributorId(warehouse.getDistributor().getId())
                .distributorName(warehouse.getDistributor().getName())
                .managerId(warehouse.getManager() != null ? warehouse.getManager().getId() : null)
                .managerName(warehouse.getManager() != null ? warehouse.getManager().getFullName() : null)
                .branchId(warehouse.getBranch() != null ? warehouse.getBranch().getId() : null)
                .branchName(warehouse.getBranch() != null ? warehouse.getBranch().getName() : null)
                .active(warehouse.isActive())
                .createdAt(warehouse.getCreatedAt())
                .updatedAt(warehouse.getUpdatedAt())
                .deactivationReason(warehouse.getDeactivationReason())
                .deactivatedAt(warehouse.getDeactivatedAt())
                .deactivatedByName(warehouse.getDeactivatedBy() != null ? warehouse.getDeactivatedBy().getFullName() : null)
                .build();
    }

    private StockMovementResponse mapToStockMovementResponse(StockMovement movement) {
        return StockMovementResponse.builder()
                .id(movement.getId())
                .warehouseId(movement.getWarehouse().getId())
                .warehouseName(movement.getWarehouse().getName())
                .productId(movement.getProduct().getId())
                .productName(movement.getProduct().getName())
                .productSku(movement.getProduct().getSku())
                .movementType(movement.getMovementType())
                .quantity(movement.getQuantity())
                .referenceType(movement.getReferenceType())
                .referenceId(movement.getReferenceId())
                .notes(movement.getNotes())
                .createdById(movement.getCreatedBy() != null ? movement.getCreatedBy().getId() : null)
                .createdByName(movement.getCreatedBy() != null ? movement.getCreatedBy().getFullName() : null)
                .createdAt(movement.getCreatedAt())
                .build();
    }

    private void publishStockAdjustedEvent(Stock stock, BigDecimal previousQuantity, StockAdjustmentRequest request) {
        StockAdjustedEvent event = new StockAdjustedEvent(
                stock.getId(),
                stock.getWarehouse().getId(),
                stock.getProduct().getId(),
                stock.getWarehouse().getDistributor().getId(),
                previousQuantity,
                stock.getQuantity(),
                request.getQuantity(),
                request.getMovementType().name(),
                request.getNotes(),
                LocalDateTime.now()
        );
        eventPublisher.publishEvent(event);
        log.debug("Published StockAdjustedEvent for stock {}", stock.getId());
    }
}
