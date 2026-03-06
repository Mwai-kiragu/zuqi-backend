package com.zuqi.service.impl;

import com.zuqi.api.dto.pos.*;
import com.zuqi.domain.branch.DistributorBranch;
import com.zuqi.domain.inventory.*;
import com.zuqi.domain.pos.*;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.PosService;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PosServiceImpl implements PosService {

    private final PosTerminalRepository terminalRepository;
    private final PosShiftRepository shiftRepository;
    private final PosSaleRepository saleRepository;
    private final PosSaleItemRepository saleItemRepository;
    private final PosSalePaymentRepository paymentRepository;
    private final DistributorBranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    private static final AtomicInteger receiptCounter = new AtomicInteger(1);

    @Override
    @Transactional
    public PosTerminalResponse createTerminal(PosTerminalRequest request, UUID createdByUserId) {
        DistributorBranch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", request.getBranchId()));

        if (request.getCode() != null && terminalRepository.existsByCode(request.getCode())) {
            throw new ValidationException("Terminal code already exists");
        }

        User createdBy = userRepository.findById(createdByUserId).orElse(null);

        PosTerminal terminal = PosTerminal.builder()
                .branch(branch)
                .name(request.getName())
                .code(request.getCode())
                .status(PosTerminalStatus.ACTIVE)
                .createdBy(createdBy)
                .build();

        terminal = terminalRepository.save(terminal);
        return mapToTerminalResponse(terminal);
    }

    @Override
    public List<PosTerminalResponse> getTerminalsByBranch(UUID branchId) {
        UUID effectiveBranchId = securityUtils.getEffectiveBranchId();
        // Non-null effectiveBranchId means enforced branch scope (non-HQ user)
        UUID filterBranchId = effectiveBranchId != null ? effectiveBranchId : branchId;
        if (filterBranchId == null) {
            // HQ branch or SUPER_ADMIN: return all terminals
            return terminalRepository.findAll().stream()
                    .map(this::mapToTerminalResponse)
                    .collect(Collectors.toList());
        }
        return terminalRepository.findByBranchId(filterBranchId).stream()
                .map(this::mapToTerminalResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PosShiftResponse openShift(OpenShiftRequest request, UUID cashierId) {
        DistributorBranch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", request.getBranchId()));

        // Check if cashier already has open shift
        shiftRepository.findByBranchIdAndCashierIdAndStatus(request.getBranchId(), cashierId, PosShiftStatus.OPEN)
                .ifPresent(s -> { throw new ValidationException("Cashier already has an open shift"); });

        User cashier = userRepository.findById(cashierId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", cashierId));

        PosTerminal terminal = null;
        if (request.getTerminalId() != null) {
            terminal = terminalRepository.findById(request.getTerminalId())
                    .orElseThrow(() -> new ResourceNotFoundException("Terminal", "id", request.getTerminalId()));
        }

        PosShift shift = PosShift.builder()
                .branch(branch)
                .terminal(terminal)
                .cashier(cashier)
                .status(PosShiftStatus.OPEN)
                .openingFloat(request.getOpeningFloat() != null ? request.getOpeningFloat() : BigDecimal.ZERO)
                .notes(request.getNotes())
                .openedAt(LocalDateTime.now())
                .build();

        shift = shiftRepository.save(shift);
        log.info("Opened shift {} for cashier {} at branch {}", shift.getId(), cashierId, request.getBranchId());
        return mapToShiftResponse(shift);
    }

    @Override
    @Transactional
    public PosShiftResponse closeShift(UUID shiftId, CloseShiftRequest request, UUID cashierId) {
        PosShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", shiftId));

        if (!shift.getCashier().getId().equals(cashierId)) {
            throw new ValidationException("Only the shift owner can close this shift");
        }
        if (shift.getStatus() == PosShiftStatus.CLOSED) {
            throw new ValidationException("Shift is already closed");
        }

        // Calculate expected cash
        BigDecimal cashSales = saleRepository
                .sumTotalByBranchAndStatusAndDateRange(
                        shift.getBranch().getId(),
                        PosSaleStatus.COMPLETED,
                        shift.getOpenedAt(),
                        LocalDateTime.now());

        shift.setStatus(PosShiftStatus.CLOSED);
        shift.setClosingFloat(request.getClosingFloat());
        shift.setExpectedCash(shift.getOpeningFloat().add(cashSales != null ? cashSales : BigDecimal.ZERO));
        shift.setNotes(request.getNotes());
        shift.setClosedAt(LocalDateTime.now());

        return mapToShiftResponse(shiftRepository.save(shift));
    }

    @Override
    public PosShiftResponse getCurrentShift(UUID branchId, UUID cashierId) {
        PosShift shift = shiftRepository.findByBranchIdAndCashierIdAndStatus(branchId, cashierId, PosShiftStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("Open Shift", "branchId/cashierId", branchId + "/" + cashierId));
        return mapToShiftResponse(shift);
    }

    @Override
    @Transactional
    public PosSaleResponse createSale(CreateSaleRequest request, UUID cashierId) {
        DistributorBranch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", request.getBranchId()));

        User cashier = userRepository.findById(cashierId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", cashierId));

        PosShift shift = null;
        if (request.getShiftId() != null) {
            shift = shiftRepository.findById(request.getShiftId())
                    .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", request.getShiftId()));
        }

        PosSale sale = PosSale.builder()
                .branch(branch)
                .shift(shift)
                .cashier(cashier)
                .status(PosSaleStatus.DRAFT)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .notes(request.getNotes())
                .build();

        sale = saleRepository.save(sale);
        return mapToSaleResponse(sale);
    }

    @Override
    @Transactional
    public PosSaleResponse updateSaleItems(UUID saleId, UpdateSaleItemsRequest request) {
        PosSale sale = getSaleEntity(saleId);
        validateSaleDraft(sale);

        // Clear existing items
        sale.getItems().clear();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (SaleItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemReq.getProductId()));

            BigDecimal discount = itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal lineTotal = itemReq.getUnitPrice()
                    .multiply(itemReq.getQuantity())
                    .subtract(discount);

            PosSaleItem item = PosSaleItem.builder()
                    .sale(sale)
                    .product(product)
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .discountAmount(discount)
                    .lineTotal(lineTotal)
                    .build();

            sale.getItems().add(item);
            subtotal = subtotal.add(lineTotal);
        }

        sale.setSubtotal(subtotal);
        sale.setTotalAmount(subtotal.subtract(sale.getDiscountAmount()).add(sale.getTaxAmount()));

        return mapToSaleResponse(saleRepository.save(sale));
    }

    @Override
    @Transactional
    public PosSaleResponse addPayment(UUID saleId, ProcessPaymentRequest request) {
        PosSale sale = getSaleEntity(saleId);
        validateSaleDraft(sale);

        PosSalePayment payment = PosSalePayment.builder()
                .sale(sale)
                .paymentMethod(request.getPaymentMethod())
                .amount(request.getAmount())
                .referenceNumber(request.getReferenceNumber())
                .notes(request.getNotes())
                .build();

        sale.getPayments().add(payment);

        BigDecimal totalPaid = sale.getPayments().stream()
                .map(PosSalePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        sale.setAmountPaid(totalPaid);
        BigDecimal change = totalPaid.subtract(sale.getTotalAmount());
        sale.setChangeGiven(change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO);

        return mapToSaleResponse(saleRepository.save(sale));
    }

    @Override
    @Transactional
    public PosSaleResponse completeSale(UUID saleId, UUID warehouseId) {
        PosSale sale = getSaleEntity(saleId);
        validateSaleDraft(sale);

        if (sale.getItems().isEmpty()) {
            throw new ValidationException("Cannot complete a sale with no items");
        }
        if (sale.getAmountPaid().compareTo(sale.getTotalAmount()) < 0) {
            throw new ValidationException("Payment is insufficient");
        }

        // Deduct stock from warehouse
        if (warehouseId != null) {
            Warehouse warehouse = warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", warehouseId));

            User cashier = sale.getCashier();

            for (PosSaleItem item : sale.getItems()) {
                Stock stock = stockRepository.findByWarehouseIdAndProductId(warehouseId, item.getProduct().getId())
                        .orElseThrow(() -> new ValidationException(
                                "No stock found for product: " + item.getProductName()));

                if (stock.getAvailableQuantity().compareTo(item.getQuantity()) < 0) {
                    throw new ValidationException("Insufficient stock for product: " + item.getProductName());
                }

                stock.setQuantity(stock.getQuantity().subtract(item.getQuantity()));
                stockRepository.save(stock);

                StockMovement movement = StockMovement.builder()
                        .warehouse(warehouse)
                        .product(item.getProduct())
                        .movementType(StockMovement.MovementType.OUT)
                        .quantity(item.getQuantity())
                        .referenceType("POS_SALE")
                        .referenceId(sale.getId())
                        .notes("POS sale: " + (sale.getReceiptNumber() != null ? sale.getReceiptNumber() : saleId))
                        .createdBy(cashier)
                        .build();
                stockMovementRepository.save(movement);
            }
        }

        sale.setStatus(PosSaleStatus.COMPLETED);
        sale.setReceiptNumber(generateReceiptNumber());
        sale.setCompletedAt(LocalDateTime.now());

        return mapToSaleResponse(saleRepository.save(sale));
    }

    @Override
    @Transactional
    public PosSaleResponse cancelSale(UUID saleId, String reason) {
        PosSale sale = getSaleEntity(saleId);
        if (sale.getStatus() == PosSaleStatus.COMPLETED) {
            throw new ValidationException("Cannot cancel a completed sale. Use refund instead.");
        }
        if (sale.getStatus() == PosSaleStatus.CANCELLED) {
            throw new ValidationException("Sale is already cancelled");
        }

        sale.setStatus(PosSaleStatus.CANCELLED);
        sale.setNotes(reason);
        sale.setCancelledAt(LocalDateTime.now());

        return mapToSaleResponse(saleRepository.save(sale));
    }

    @Override
    @Transactional
    public PosSaleResponse refundSale(UUID saleId, UUID cashierId) {
        PosSale original = getSaleEntity(saleId);
        if (original.getStatus() != PosSaleStatus.COMPLETED) {
            throw new ValidationException("Can only refund completed sales");
        }

        User cashier = userRepository.findById(cashierId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", cashierId));

        PosSale refund = PosSale.builder()
                .branch(original.getBranch())
                .shift(original.getShift())
                .cashier(cashier)
                .status(PosSaleStatus.REFUNDED)
                .subtotal(original.getSubtotal().negate())
                .totalAmount(original.getTotalAmount().negate())
                .amountPaid(original.getAmountPaid().negate())
                .refundOf(original)
                .receiptNumber(generateReceiptNumber())
                .completedAt(LocalDateTime.now())
                .build();

        original.setStatus(PosSaleStatus.REFUNDED);
        saleRepository.save(original);

        return mapToSaleResponse(saleRepository.save(refund));
    }

    @Override
    public Page<PosSaleResponse> getSales(UUID branchId, String status, Pageable pageable) {
        UUID effectiveBranchId = securityUtils.getEffectiveBranchId();
        // Enforce branch scope for non-HQ users; HQ/SUPER_ADMIN may filter by requested branchId or see all
        UUID filterBranchId = effectiveBranchId != null ? effectiveBranchId : branchId;

        if (filterBranchId == null) {
            // HQ or SUPER_ADMIN with no specific branch filter — return all
            if (status != null && !status.isBlank()) {
                PosSaleStatus saleStatus = PosSaleStatus.valueOf(status.toUpperCase());
                return saleRepository.findByStatus(saleStatus, pageable).map(this::mapToSaleResponse);
            }
            return saleRepository.findAll(pageable).map(this::mapToSaleResponse);
        }
        if (status != null && !status.isBlank()) {
            PosSaleStatus saleStatus = PosSaleStatus.valueOf(status.toUpperCase());
            return saleRepository.findByBranchIdAndStatus(filterBranchId, saleStatus, pageable)
                    .map(this::mapToSaleResponse);
        }
        return saleRepository.findByBranchId(filterBranchId, pageable).map(this::mapToSaleResponse);
    }

    @Override
    public PosSaleResponse getSaleById(UUID saleId) {
        return mapToSaleResponse(getSaleEntity(saleId));
    }

    @Override
    public PosSummaryResponse getDailySummary(UUID branchId, LocalDate date) {
        DistributorBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", "id", branchId));

        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        long total = saleRepository.countByBranchAndStatusAndDateRange(branchId, PosSaleStatus.COMPLETED, from, to) +
                saleRepository.countByBranchAndStatusAndDateRange(branchId, PosSaleStatus.CANCELLED, from, to);
        long completed = saleRepository.countByBranchAndStatusAndDateRange(branchId, PosSaleStatus.COMPLETED, from, to);
        long cancelled = saleRepository.countByBranchAndStatusAndDateRange(branchId, PosSaleStatus.CANCELLED, from, to);
        BigDecimal revenue = saleRepository.sumTotalByBranchAndStatusAndDateRange(branchId, PosSaleStatus.COMPLETED, from, to);

        return PosSummaryResponse.builder()
                .branchId(branchId)
                .branchName(branch.getName())
                .date(date)
                .totalTransactions(total)
                .completedTransactions(completed)
                .cancelledTransactions(cancelled)
                .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
                .totalDiscounts(BigDecimal.ZERO)
                .averageTransactionValue(completed > 0 && revenue != null ?
                        revenue.divide(BigDecimal.valueOf(completed), 2, java.math.RoundingMode.HALF_UP) :
                        BigDecimal.ZERO)
                .build();
    }

    // ---- Helpers ----

    private PosSale getSaleEntity(UUID saleId) {
        return saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale", "id", saleId));
    }

    private void validateSaleDraft(PosSale sale) {
        if (sale.getStatus() != PosSaleStatus.DRAFT) {
            throw new ValidationException("Operation only allowed on DRAFT sales. Current status: " + sale.getStatus());
        }
    }

    private String generateReceiptNumber() {
        return "RCP-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now())
                + "-" + String.format("%05d", receiptCounter.getAndIncrement());
    }

    private PosTerminalResponse mapToTerminalResponse(PosTerminal terminal) {
        return PosTerminalResponse.builder()
                .id(terminal.getId())
                .branchId(terminal.getBranch().getId())
                .branchName(terminal.getBranch().getName())
                .name(terminal.getName())
                .code(terminal.getCode())
                .status(terminal.getStatus())
                .createdAt(terminal.getCreatedAt())
                .build();
    }

    private PosShiftResponse mapToShiftResponse(PosShift shift) {
        return PosShiftResponse.builder()
                .id(shift.getId())
                .branchId(shift.getBranch().getId())
                .branchName(shift.getBranch().getName())
                .terminalId(shift.getTerminal() != null ? shift.getTerminal().getId() : null)
                .terminalName(shift.getTerminal() != null ? shift.getTerminal().getName() : null)
                .cashierId(shift.getCashier().getId())
                .cashierName(shift.getCashier().getFirstName() + " " + shift.getCashier().getLastName())
                .status(shift.getStatus())
                .openingFloat(shift.getOpeningFloat())
                .closingFloat(shift.getClosingFloat())
                .expectedCash(shift.getExpectedCash())
                .notes(shift.getNotes())
                .openedAt(shift.getOpenedAt())
                .closedAt(shift.getClosedAt())
                .createdAt(shift.getCreatedAt())
                .build();
    }

    private PosSaleResponse mapToSaleResponse(PosSale sale) {
        List<PosSaleItemResponse> items = sale.getItems().stream()
                .map(i -> PosSaleItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productName(i.getProductName())
                        .productSku(i.getProductSku())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .discountAmount(i.getDiscountAmount())
                        .lineTotal(i.getLineTotal())
                        .build())
                .collect(Collectors.toList());

        List<PosSalePaymentResponse> payments = sale.getPayments().stream()
                .map(p -> PosSalePaymentResponse.builder()
                        .id(p.getId())
                        .paymentMethod(p.getPaymentMethod())
                        .amount(p.getAmount())
                        .referenceNumber(p.getReferenceNumber())
                        .notes(p.getNotes())
                        .createdAt(p.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return PosSaleResponse.builder()
                .id(sale.getId())
                .branchId(sale.getBranch().getId())
                .branchName(sale.getBranch().getName())
                .shiftId(sale.getShift() != null ? sale.getShift().getId() : null)
                .cashierId(sale.getCashier().getId())
                .cashierName(sale.getCashier().getFirstName() + " " + sale.getCashier().getLastName())
                .receiptNumber(sale.getReceiptNumber())
                .status(sale.getStatus())
                .subtotal(sale.getSubtotal())
                .discountAmount(sale.getDiscountAmount())
                .taxAmount(sale.getTaxAmount())
                .totalAmount(sale.getTotalAmount())
                .amountPaid(sale.getAmountPaid())
                .changeGiven(sale.getChangeGiven())
                .customerName(sale.getCustomerName())
                .customerPhone(sale.getCustomerPhone())
                .notes(sale.getNotes())
                .items(items)
                .payments(payments)
                .completedAt(sale.getCompletedAt())
                .cancelledAt(sale.getCancelledAt())
                .createdAt(sale.getCreatedAt())
                .updatedAt(sale.getUpdatedAt())
                .build();
    }
}
