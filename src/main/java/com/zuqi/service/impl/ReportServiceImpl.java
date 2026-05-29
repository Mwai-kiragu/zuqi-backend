package com.zuqi.service.impl;

import com.zuqi.api.dto.report.InventoryReportResponse;
import com.zuqi.api.dto.report.PaymentReportResponse;
import com.zuqi.api.dto.report.SalesReportResponse;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.ReportService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final StockRepository stockRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final SecurityUtils securityUtils;

    // ── Scope resolution ─────────────────────────────────────────────────────

    /** Merchant ID if the caller is a MERCHANT_ADMIN, otherwise null. */
    private UUID merchantId() {
        return securityUtils.getCurrentUserMerchantId();
    }

    /** Distributor ID for DISTRIBUTOR_ADMIN / CASHIER / etc., null for MERCHANT_ADMIN and SUPER_ADMIN. */
    private UUID distributorId() {
        UUID mid = merchantId();
        return mid != null ? null : securityUtils.getDistributorIdForFiltering();
    }

    private void requireScope(UUID merchantId, UUID distributorId) {
        if (merchantId == null && distributorId == null) {
            throw new ValidationException("No distributor or merchant context found for the current user.");
        }
    }

    // ── Sales report ─────────────────────────────────────────────────────────

    @Override
    public SalesReportResponse generateSalesReport(LocalDate startDate, LocalDate endDate) {
        UUID mid = merchantId();
        UUID did = distributorId();
        requireScope(mid, did);
        log.debug("Generating sales report merchantId={} distributorId={}", mid, did);

        LocalDateTime startDt = startDate.atStartOfDay();
        LocalDateTime endDt = endDate.atTime(LocalTime.MAX);

        // For merchant scope, aggregate across all distributors of the merchant.
        // Reuse distributor-level queries using the first active distributor for now;
        // for multi-distributor merchants use the first found distributor.
        UUID scopedDistributorId = did;
        if (mid != null && did == null) {
            // Use null as distributor — many query methods accept null as "all"
            // Fall through to distributor-scoped queries but with a placeholder.
            // Real aggregation would require merchant-level queries.
            // For sales, use the distributor the user is associated with if available.
            scopedDistributorId = securityUtils.getCurrentUserDistributorId();
            if (scopedDistributorId == null) {
                return SalesReportResponse.builder().startDate(startDate).endDate(endDate)
                        .totalRevenue(BigDecimal.ZERO).totalOrders(0L)
                        .averageOrderValue(BigDecimal.ZERO).dailyData(new ArrayList<>())
                        .salesRepPerformance(new ArrayList<>()).invoices(new ArrayList<>())
                        .productsSold(new ArrayList<>()).build();
            }
        }

        BigDecimal totalRevenue = orderRepository.sumRevenueInPeriod(scopedDistributorId, startDt, endDt);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        long totalOrders = orderRepository.countOrdersInPeriod(scopedDistributorId, startDt, endDt);
        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<Object[]> dailyData = orderRepository.findDailyRevenueData(scopedDistributorId, startDt, endDt);
        List<SalesReportResponse.DailyData> dailyList = dailyData.stream()
                .map(row -> SalesReportResponse.DailyData.builder()
                        .date((LocalDate) row[0]).orderCount((Long) row[1]).revenue((BigDecimal) row[2])
                        .build())
                .toList();

        org.springframework.data.domain.Page<com.zuqi.domain.invoice.Invoice> invoicePage =
                invoiceRepository.findByFilters(scopedDistributorId, null, null, startDate, endDate, PageRequest.of(0, 200));
        List<SalesReportResponse.InvoiceSummary> invoiceList = invoicePage.getContent().stream()
                .map(inv -> SalesReportResponse.InvoiceSummary.builder()
                        .invoiceId(inv.getId().toString()).invoiceNumber(inv.getInvoiceNumber())
                        .customerName(inv.getMerchant() != null ? inv.getMerchant().getBusinessName() : "")
                        .issueDate(inv.getIssueDate()).dueDate(inv.getDueDate())
                        .totalAmount(inv.getTotalAmount())
                        .status(inv.getStatus() != null ? inv.getStatus().name() : "")
                        .build())
                .toList();

        List<Object[]> productRows = orderRepository.findTopProductsSold(scopedDistributorId, startDt, endDt, PageRequest.of(0, 20));
        List<SalesReportResponse.ProductSoldData> productList = productRows.stream()
                .map(row -> SalesReportResponse.ProductSoldData.builder()
                        .productId(row[0].toString()).productName((String) row[1])
                        .productSku(row[2] != null ? (String) row[2] : "")
                        .totalQuantity((BigDecimal) row[3]).totalRevenue((BigDecimal) row[4])
                        .build())
                .toList();

        return SalesReportResponse.builder()
                .startDate(startDate).endDate(endDate).totalRevenue(totalRevenue)
                .totalOrders(totalOrders).averageOrderValue(avgOrderValue)
                .dailyData(dailyList).salesRepPerformance(new ArrayList<>())
                .invoices(invoiceList).productsSold(productList)
                .build();
    }

    // ── Inventory report ──────────────────────────────────────────────────────

    @Override
    public InventoryReportResponse generateInventoryReport() {
        UUID mid = merchantId();
        UUID did = distributorId();
        requireScope(mid, did);
        log.debug("Generating inventory report merchantId={} distributorId={}", mid, did);

        long totalProducts;
        long lowStockCount;
        long outOfStockCount;
        BigDecimal totalStockValue;
        List<Stock> lowStockItems;
        List<Stock> outOfStockItems;
        List<Object[]> whRows;

        if (mid != null) {
            totalProducts    = productRepository.countByDistributorMerchantIdAndActiveTrue(mid);
            lowStockCount    = stockRepository.countLowStockByMerchantId(mid);
            outOfStockCount  = stockRepository.countOutOfStockByMerchantId(mid);
            totalStockValue  = stockRepository.sumStockValueByMerchantId(mid);
            lowStockItems    = stockRepository.findLowStockByMerchantIdFetched(mid, PageRequest.of(0, 20)).getContent();
            outOfStockItems  = stockRepository.findOutOfStockByMerchantId(mid);
            whRows           = stockRepository.warehouseSummaryByMerchantId(mid);
        } else {
            totalProducts    = productRepository.countByDistributorIdAndActiveTrue(did);
            lowStockCount    = stockRepository.countLowStockByDistributorId(did);
            outOfStockCount  = stockRepository.countOutOfStockByDistributorId(did);
            totalStockValue  = stockRepository.sumStockValueByDistributorId(did);
            lowStockItems    = stockRepository.findLowStockByDistributorId(did, PageRequest.of(0, 20)).getContent();
            outOfStockItems  = stockRepository.findOutOfStockByDistributorId(did);
            whRows           = stockRepository.warehouseSummaryByDistributorId(did);
        }

        List<InventoryReportResponse.StockItem> lowStockList = toStockItems(lowStockItems, true);
        List<InventoryReportResponse.StockItem> outOfStockList = outOfStockItems.stream()
                .limit(50)
                .map(s -> InventoryReportResponse.StockItem.builder()
                        .productId(s.getProduct().getId().toString())
                        .productName(s.getProduct().getName())
                        .productSku(s.getProduct().getSku())
                        .warehouseName(s.getWarehouse().getName())
                        .quantity(s.getQuantity())
                        .build())
                .toList();

        List<InventoryReportResponse.WarehouseSummary> warehouseList = whRows.stream()
                .map(row -> InventoryReportResponse.WarehouseSummary.builder()
                        .warehouseId(row[0].toString())
                        .warehouseName((String) row[1])
                        .productCount(((Number) row[2]).longValue())
                        .lowStockCount(((Number) row[3]).longValue())
                        .build())
                .toList();

        return InventoryReportResponse.builder()
                .totalProducts(totalProducts)
                .lowStockCount(lowStockCount)
                .outOfStockCount(outOfStockCount)
                .totalStockValue(totalStockValue != null ? totalStockValue : BigDecimal.ZERO)
                .lowStockItems(lowStockList)
                .outOfStockItems(outOfStockList)
                .warehouseSummaries(warehouseList)
                .build();
    }

    // ── Payment report ────────────────────────────────────────────────────────

    @Override
    public PaymentReportResponse generatePaymentReport(LocalDate startDate, LocalDate endDate) {
        UUID mid = merchantId();
        UUID did = distributorId();
        requireScope(mid, did);
        log.debug("Generating payment report merchantId={} distributorId={}", mid, did);

        // Payment repository queries are distributor-scoped; for MERCHANT_ADMIN
        // use a per-distributor approach if they have a linked distributorId.
        UUID scopedDistributorId = did != null ? did : securityUtils.getCurrentUserDistributorId();
        if (scopedDistributorId == null) {
            return PaymentReportResponse.builder().startDate(startDate).endDate(endDate)
                    .totalCollected(BigDecimal.ZERO).totalOutstanding(BigDecimal.ZERO)
                    .totalPayments(0L).unreconciledCount(0L)
                    .byPaymentMethod(new ArrayList<>()).dailyCollections(new ArrayList<>())
                    .build();
        }

        LocalDateTime startDt = startDate.atStartOfDay();
        LocalDateTime endDt = endDate.atTime(LocalTime.MAX);

        BigDecimal totalCollected = paymentRepository.sumCollectedByDistributorAndDateRange(scopedDistributorId, startDt, endDt);
        if (totalCollected == null) totalCollected = BigDecimal.ZERO;

        long totalPayments = paymentRepository.countCompletedByDistributorAndDateRange(scopedDistributorId, startDt, endDt);

        BigDecimal totalOutstanding = orderRepository.sumOutstandingAmount(scopedDistributorId);
        if (totalOutstanding == null) totalOutstanding = BigDecimal.ZERO;

        long unreconciledCount = paymentRepository.countUnreconciledPayments(scopedDistributorId);

        List<PaymentReportResponse.PaymentMethodSummary> byMethod = paymentRepository
                .summaryByPaymentMethod(scopedDistributorId, startDt, endDt).stream()
                .map(row -> PaymentReportResponse.PaymentMethodSummary.builder()
                        .methodCode((String) row[0]).methodName((String) row[1])
                        .count(((Number) row[2]).longValue()).total((BigDecimal) row[3])
                        .build())
                .toList();

        List<PaymentReportResponse.DailyCollection> daily = paymentRepository
                .dailyCollections(scopedDistributorId, startDt, endDt).stream()
                .map(row -> PaymentReportResponse.DailyCollection.builder()
                        .date((LocalDate) row[0]).count(((Number) row[1]).longValue())
                        .amount((BigDecimal) row[2])
                        .build())
                .toList();

        return PaymentReportResponse.builder()
                .startDate(startDate).endDate(endDate)
                .totalCollected(totalCollected).totalOutstanding(totalOutstanding)
                .totalPayments(totalPayments).unreconciledCount(unreconciledCount)
                .byPaymentMethod(byMethod).dailyCollections(daily)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<InventoryReportResponse.StockItem> toStockItems(List<Stock> items, boolean includeReorderLevel) {
        return items.stream()
                .map(s -> {
                    InventoryReportResponse.StockItem.StockItemBuilder b = InventoryReportResponse.StockItem.builder()
                            .productId(s.getProduct().getId().toString())
                            .productName(s.getProduct().getName())
                            .productSku(s.getProduct().getSku())
                            .warehouseName(s.getWarehouse().getName())
                            .quantity(s.getQuantity());
                    if (includeReorderLevel) b.reorderLevel(s.getReorderLevel());
                    return b.build();
                })
                .toList();
    }
}
