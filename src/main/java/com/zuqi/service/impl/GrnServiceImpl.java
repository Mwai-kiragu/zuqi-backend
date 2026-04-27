package com.zuqi.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuqi.api.dto.procurement.GrnItemDto;
import com.zuqi.api.dto.procurement.GrnRequest;
import com.zuqi.api.dto.procurement.GrnResponse;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.ProductBatch;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.StockMovement;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.procurement.GoodsReceiptNote;
import com.zuqi.domain.procurement.GrnStatus;
import com.zuqi.domain.procurement.PoStatus;
import com.zuqi.domain.procurement.PurchaseOrder;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.GrnService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GrnServiceImpl implements GrnService {

    private final GoodsReceiptNoteRepository grnRepository;
    private final PurchaseOrderRepository poRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final ProductBatchRepository productBatchRepository;
    private final DistributorRepository distributorRepository;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    // ─── Queries ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<GrnResponse> getGrns(UUID distributorId, GrnStatus status, UUID supplierId,
                                     UUID purchaseOrderId, Pageable pageable) {
        UUID effective = distributorId != null ? distributorId : securityUtils.getDistributorIdForFiltering();
        return grnRepository.findWithFilters(effective, status, supplierId, purchaseOrderId, pageable)
                .map(grn -> GrnResponse.fromEntity(grn, resolveWarehouseName(grn.getWarehouseId())));
    }

    @Override
    @Transactional(readOnly = true)
    public GrnResponse getGrnById(UUID id) {
        GoodsReceiptNote grn = findById(id);
        return GrnResponse.fromEntity(grn, resolveWarehouseName(grn.getWarehouseId()));
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public GrnResponse createGrn(GrnRequest request, User currentUser) {
        PurchaseOrder po = poRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", request.getPurchaseOrderId()));

        if (po.getStatus() == PoStatus.DRAFT || po.getStatus() == PoStatus.SENT) {
            throw new ValidationException("Cannot receive goods for a PO that has not been confirmed by the supplier");
        }
        if (po.getStatus() == PoStatus.RECEIVED) {
            throw new ValidationException("This purchase order has already been fully received");
        }
        if (po.getStatus() == PoStatus.CANCELLED) {
            throw new ValidationException("Cannot create a GRN for a cancelled purchase order");
        }

        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", request.getWarehouseId()));

        List<Map<String, Object>> itemMaps = itemsToMaps(request.getItems());
        BigDecimal total = calculateTotal(request.getItems());

        GoodsReceiptNote grn = GoodsReceiptNote.builder()
                .grnNumber(generateGrnNumber())
                .purchaseOrder(po)
                .supplier(po.getSupplier())
                .distributorId(po.getDistributorId())
                .warehouseId(warehouse.getId())
                .deliveryNoteNumber(request.getDeliveryNoteNumber())
                .notes(request.getNotes())
                .items(itemMaps)
                .totalAmount(total)
                .createdBy(currentUser)
                .build();

        GoodsReceiptNote saved = grnRepository.save(grn);
        log.info("Created GRN {} for PO {}", saved.getGrnNumber(), po.getPoNumber());
        return GrnResponse.fromEntity(saved, warehouse.getName());
    }

    // ─── Confirm (triggers stock update) ─────────────────────────────────────

    @Override
    @Transactional
    public GrnResponse confirmGrn(UUID id, User currentUser) {
        GoodsReceiptNote grn = findById(id);

        if (grn.getStatus() != GrnStatus.DRAFT) {
            throw new ValidationException("Only DRAFT GRNs can be confirmed");
        }

        Warehouse warehouse = warehouseRepository.findById(grn.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", grn.getWarehouseId()));

        // Update stock for each received item
        for (Map<String, Object> itemMap : grn.getItems()) {
            Object productIdObj  = itemMap.get("productId");
            Object receivedQtyObj = itemMap.get("receivedQuantity");

            if (productIdObj == null || receivedQtyObj == null) continue;

            Integer receivedQty = ((Number) receivedQtyObj).intValue();
            if (receivedQty <= 0) continue;

            UUID productId = UUID.fromString(productIdObj.toString());
            BigDecimal qty = new BigDecimal(receivedQty);

            // Find or create Stock record for this (warehouse, product) pair
            Stock stock = stockRepository.findByWarehouseIdAndProductId(warehouse.getId(), productId)
                    .orElseGet(() -> {
                        Product product = productRepository.findById(productId)
                                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
                        return Stock.builder()
                                .warehouse(warehouse)
                                .product(product)
                                .quantity(BigDecimal.ZERO)
                                .reservedQuantity(BigDecimal.ZERO)
                                .build();
                    });

            stock.setQuantity(stock.getQuantity().add(qty));
            stockRepository.save(stock);

            // Create ProductBatch if expiry date was provided
            Object expiryObj = itemMap.get("expiryDate");
            if (expiryObj != null && !expiryObj.toString().isBlank()) {
                LocalDate expiryDate = LocalDate.parse(expiryObj.toString());
                String batchNumber = "GRN-" + grn.getGrnNumber() + "-" + productId.toString().substring(0, 8).toUpperCase();
                Distributor distributor = distributorRepository.findById(grn.getDistributorId())
                        .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", grn.getDistributorId()));
                ProductBatch batch = ProductBatch.builder()
                        .distributor(distributor)
                        .warehouse(warehouse)
                        .product(stock.getProduct())
                        .batchNumber(batchNumber)
                        .expiryDate(expiryDate)
                        .initialQuantity(qty.doubleValue())
                        .currentQuantity(qty.doubleValue())
                        .status("ACTIVE")
                        .build();
                productBatchRepository.save(batch);
            }

            // Record inbound movement
            StockMovement movement = StockMovement.builder()
                    .warehouse(warehouse)
                    .product(stock.getProduct())
                    .movementType(StockMovement.MovementType.IN)
                    .quantity(qty)
                    .referenceType("GRN")
                    .referenceId(grn.getId())
                    .notes("Auto-updated on GRN " + grn.getGrnNumber() + " confirmation")
                    .createdById(currentUser.getId())
                    .build();
            stockMovementRepository.save(movement);

            log.info("Stock updated: +{} units of product {} in warehouse {}", qty, productId, warehouse.getId());
        }

        // Advance GRN status
        grn.setStatus(GrnStatus.CONFIRMED);
        grn.setConfirmedBy(currentUser);
        grn.setConfirmedAt(LocalDateTime.now());
        GoodsReceiptNote confirmed = grnRepository.save(grn);

        // Update PO status
        advancePoStatus(grn.getPurchaseOrder(), grn);

        log.info("GRN {} confirmed — stock updated", grn.getGrnNumber());
        return GrnResponse.fromEntity(confirmed, warehouse.getName());
    }

    // ─── Reject ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public GrnResponse rejectGrn(UUID id, String reason, User currentUser) {
        GoodsReceiptNote grn = findById(id);

        if (grn.getStatus() != GrnStatus.DRAFT) {
            throw new ValidationException("Only DRAFT GRNs can be rejected");
        }

        grn.setStatus(GrnStatus.REJECTED);
        grn.setRejectedBy(currentUser);
        grn.setRejectedAt(LocalDateTime.now());
        grn.setRejectionReason(reason);

        GoodsReceiptNote rejected = grnRepository.save(grn);
        log.info("GRN {} rejected: {}", grn.getGrnNumber(), reason);
        return GrnResponse.fromEntity(rejected, resolveWarehouseName(rejected.getWarehouseId()));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private GoodsReceiptNote findById(UUID id) {
        return grnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GoodsReceiptNote", "id", id));
    }

    private String generateGrnNumber() {
        long count = grnRepository.countAll();
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        return String.format("GRN-%s%04d", datePart, count + 1);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itemsToMaps(List<GrnItemDto> items) {
        return items.stream()
                .map(item -> (Map<String, Object>) objectMapper.convertValue(item, Map.class))
                .toList();
    }

    private BigDecimal calculateTotal(List<GrnItemDto> items) {
        return items.stream()
                .map(item -> {
                    if (item.getUnitCost() == null || item.getReceivedQuantity() == null) return BigDecimal.ZERO;
                    return item.getUnitCost().multiply(BigDecimal.valueOf(item.getReceivedQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String resolveWarehouseName(UUID warehouseId) {
        if (warehouseId == null) return null;
        return warehouseRepository.findById(warehouseId)
                .map(Warehouse::getName)
                .orElse(null);
    }

    /**
     * Advance the PO status when a GRN is confirmed.
     * <ul>
     *   <li>If total ordered qty equals total confirmed received qty → RECEIVED</li>
     *   <li>Otherwise → PARTIALLY_RECEIVED</li>
     * </ul>
     */
    private void advancePoStatus(PurchaseOrder po, GoodsReceiptNote confirmedGrn) {
        // Sum received quantities across all CONFIRMED GRNs for this PO
        List<GoodsReceiptNote> allGrns = grnRepository.findByPurchaseOrderId(po.getId());
        int totalReceived = allGrns.stream()
                .filter(g -> g.getStatus() == GrnStatus.CONFIRMED)
                .flatMap(g -> g.getItems().stream())
                .mapToInt(item -> {
                    Object qty = item.get("receivedQuantity");
                    return qty != null ? ((Number) qty).intValue() : 0;
                })
                .sum();

        int totalOrdered = po.getItems().stream()
                .mapToInt(item -> {
                    Object qty = item.get("quantity");
                    return qty != null ? ((Number) qty).intValue() : 0;
                })
                .sum();

        if (totalOrdered > 0 && totalReceived >= totalOrdered) {
            po.setStatus(PoStatus.RECEIVED);
            po.setReceivedAt(LocalDateTime.now());
        } else {
            po.setStatus(PoStatus.PARTIALLY_RECEIVED);
        }
        poRepository.save(po);
    }
}
