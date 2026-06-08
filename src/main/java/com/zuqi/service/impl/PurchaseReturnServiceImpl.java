package com.zuqi.service.impl;

import com.zuqi.api.dto.returns.*;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.procurement.GoodsReceiptNote;
import com.zuqi.domain.procurement.GrnStatus;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.returns.*;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.domain.supplier.SupplierBill;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.service.ActivityLogService;
import com.zuqi.service.PurchaseReturnService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseReturnServiceImpl implements PurchaseReturnService {

    private final PurchaseReturnRepository    purchaseReturnRepository;
    private final SupplierRepository          supplierRepository;
    private final SupplierBillRepository      supplierBillRepository;
    private final ProductRepository           productRepository;
    private final UserRepository              userRepository;
    private final DistributorRepository       distributorRepository;
    private final GoodsReceiptNoteRepository  grnRepository;
    private final SecurityUtils               securityUtils;
    private final ActivityLogService          activityLogService;

    @Override
    @Transactional
    public PurchaseReturnResponse create(CreatePurchaseReturnRequest request, UUID createdById) {
        UUID distId = securityUtils.getDistributorIdForFiltering();
        Distributor distributor = distId != null
                ? distributorRepository.findById(distId)
                        .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", distId))
                : null;
        if (distributor == null) throw new ValidationException("Cannot determine distributor for purchase return");

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", request.getSupplierId()));

        SupplierBill bill = request.getSupplierBillId() != null
                ? supplierBillRepository.findById(request.getSupplierBillId())
                        .orElseThrow(() -> new ResourceNotFoundException("SupplierBill", "id", request.getSupplierBillId()))
                : null;

        GoodsReceiptNote grn = null;
        if (request.getGrnId() != null) {
            grn = grnRepository.findById(request.getGrnId())
                    .orElseThrow(() -> new ResourceNotFoundException("GoodsReceiptNote", "id", request.getGrnId()));
            if (grn.getStatus() != GrnStatus.CONFIRMED) {
                throw new ValidationException("Only goods received on a CONFIRMED GRN can be returned");
            }
            if (grn.getSupplier() != null && !grn.getSupplier().getId().equals(supplier.getId())) {
                throw new ValidationException("The selected GRN does not belong to the chosen supplier");
            }
        }

        // Validate return quantities against GRN received quantities
        if (grn != null) {
            // Build map: productId → net receivable quantity (received − rejected) from GRN
            Map<UUID, BigDecimal> grnReceived = new HashMap<>();
            for (Map<String, Object> item : grn.getItems()) {
                Object pidObj = item.get("productId");
                Object recvObj = item.get("receivedQuantity");
                Object rejObj  = item.get("rejectedQuantity");
                if (pidObj == null || recvObj == null) continue;
                UUID pid    = UUID.fromString(pidObj.toString());
                BigDecimal received = new BigDecimal(recvObj.toString());
                BigDecimal rejected = rejObj != null ? new BigDecimal(rejObj.toString()) : BigDecimal.ZERO;
                grnReceived.merge(pid, received.subtract(rejected), BigDecimal::add);
            }

            // Build map: productId → already returned quantity (non-CANCELLED) against this GRN
            Map<UUID, BigDecimal> alreadyReturned = new HashMap<>();
            purchaseReturnRepository.sumReturnedQuantitiesByGrnId(grn.getId())
                    .forEach(row -> alreadyReturned.put((UUID) row[0], (BigDecimal) row[1]));

            // Validate each line
            for (CreatePurchaseReturnRequest.LineItem line : request.getItems()) {
                BigDecimal grnQty      = grnReceived.getOrDefault(line.getProductId(), BigDecimal.ZERO);
                BigDecimal returnedQty = alreadyReturned.getOrDefault(line.getProductId(), BigDecimal.ZERO);
                BigDecimal available   = grnQty.subtract(returnedQty);
                if (line.getQuantity().compareTo(available) > 0) {
                    Product p = productRepository.findById(line.getProductId()).orElse(null);
                    String name = p != null ? p.getName() : line.getProductId().toString();
                    throw new ValidationException(
                        "Return quantity " + line.getQuantity().stripTrailingZeros().toPlainString() +
                        " for '" + name + "' exceeds the returnable quantity of " +
                        available.stripTrailingZeros().toPlainString() +
                        " (received from GRN " + grn.getGrnNumber() + ")."
                    );
                }
            }
        }

        User createdBy = createdById != null
                ? userRepository.findById(createdById).orElse(null)
                : null;

        PurchaseReturn pr = PurchaseReturn.builder()
                .returnNumber(generateNumber("PR"))
                .distributor(distributor)
                .supplier(supplier)
                .supplierBill(bill)
                .grn(grn)
                .reason(request.getReason())
                .status(ReturnStatus.DRAFT)
                .totalAmount(BigDecimal.ZERO)
                .createdBy(createdBy)
                .build();

        List<PurchaseReturnItem> items = request.getItems().stream().map(line -> {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", line.getProductId()));
            BigDecimal total = line.getUnitPrice().multiply(line.getQuantity());
            return PurchaseReturnItem.builder()
                    .purchaseReturn(pr)
                    .product(product)
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPrice())
                    .totalAmount(total)
                    .build();
        }).collect(Collectors.toList());

        pr.setItems(items);
        pr.setTotalAmount(items.stream().map(PurchaseReturnItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        PurchaseReturn savedPr = purchaseReturnRepository.save(pr);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.CREATE, "PURCHASE_RETURN", savedPr.getId(),
                savedPr.getReturnNumber(), "PURCHASE_RETURNS", "Created purchase return: " + savedPr.getReturnNumber()
            );
        }
        return toResponse(savedPr);
    }

    @Override
    @Transactional
    public PurchaseReturnResponse confirm(UUID id) {
        PurchaseReturn pr = findOrThrow(id);
        if (pr.getStatus() != ReturnStatus.DRAFT) {
            throw new ValidationException("Only DRAFT returns can be confirmed");
        }
        pr.setStatus(ReturnStatus.CONFIRMED);
        PurchaseReturn confirmedPr = purchaseReturnRepository.save(pr);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.APPROVE, "PURCHASE_RETURN", confirmedPr.getId(),
                confirmedPr.getReturnNumber(), "PURCHASE_RETURNS", "Approved purchase return: " + confirmedPr.getReturnNumber()
            );
        }
        return toResponse(confirmedPr);
    }

    @Override
    @Transactional
    public PurchaseReturnResponse cancel(UUID id) {
        PurchaseReturn pr = findOrThrow(id);
        if (pr.getStatus() == ReturnStatus.CONFIRMED) {
            throw new ValidationException("Confirmed returns cannot be cancelled");
        }
        pr.setStatus(ReturnStatus.CANCELLED);
        return toResponse(purchaseReturnRepository.save(pr));
    }

    @Override
    public PurchaseReturnResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public Page<PurchaseReturnResponse> getAll(Pageable pageable) {
        UUID distId = securityUtils.getDistributorIdForFiltering();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (distId != null) {
            return purchaseReturnRepository.findByDistributorId(distId, pageable).map(this::toResponse);
        } else if (merchantId != null) {
            return purchaseReturnRepository.findByDistributorMerchantId(merchantId, pageable).map(this::toResponse);
        }
        return purchaseReturnRepository.findAll(pageable).map(this::toResponse);
    }

    private PurchaseReturn findOrThrow(UUID id) {
        return purchaseReturnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseReturn", "id", id));
    }

    private String generateNumber(String prefix) {
        return prefix + "-" + System.currentTimeMillis();
    }

    private PurchaseReturnResponse toResponse(PurchaseReturn pr) {
        List<PurchaseReturnResponse.ItemResponse> items = pr.getItems().stream()
                .map(i -> PurchaseReturnResponse.ItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productName(i.getProduct().getName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .totalAmount(i.getTotalAmount())
                        .build())
                .collect(Collectors.toList());
        return PurchaseReturnResponse.builder()
                .id(pr.getId())
                .returnNumber(pr.getReturnNumber())
                .distributorId(pr.getDistributor().getId())
                .supplierId(pr.getSupplier().getId())
                .supplierName(pr.getSupplier().getName())
                .supplierBillId(pr.getSupplierBill() != null ? pr.getSupplierBill().getId() : null)
                .grnId(pr.getGrn() != null ? pr.getGrn().getId() : null)
                .grnNumber(pr.getGrn() != null ? pr.getGrn().getGrnNumber() : null)
                .reason(pr.getReason())
                .status(pr.getStatus().name())
                .totalAmount(pr.getTotalAmount())
                .items(items)
                .createdAt(pr.getCreatedAt())
                .updatedAt(pr.getUpdatedAt())
                .build();
    }
}
