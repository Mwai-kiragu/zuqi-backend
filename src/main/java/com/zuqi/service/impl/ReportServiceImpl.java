package com.zuqi.service.impl;

import com.zuqi.api.dto.report.InventoryReportResponse;
import com.zuqi.api.dto.report.PaymentReportResponse;
import com.zuqi.api.dto.report.SalesReportResponse;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.repository.*;
import com.zuqi.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final StockRepository stockRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    @Override
    public SalesReportResponse generateSalesReport(UUID distributorId, LocalDate startDate, LocalDate endDate) {
        log.debug("Generating sales report for distributor: {} from {} to {}", distributorId, startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // Get total revenue
        BigDecimal totalRevenue = orderRepository.sumRevenueFromDate(distributorId, startDateTime);
        if (totalRevenue == null) {
            totalRevenue = BigDecimal.ZERO;
        }

        // Get order count
        long totalOrders = orderRepository.countByDistributorId(distributorId);

        // Calculate average order value
        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Get daily data
        List<Object[]> dailyData = orderRepository.findDailyRevenueData(distributorId, startDateTime, endDateTime);
        List<SalesReportResponse.DailyData> dailyList = dailyData.stream()
                .map(row -> SalesReportResponse.DailyData.builder()
                        .date((LocalDate) row[0])
                        .orderCount((Long) row[1])
                        .revenue((BigDecimal) row[2])
                        .build())
                .toList();

        return SalesReportResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .averageOrderValue(avgOrderValue)
                .dailyData(dailyList)
                .salesRepPerformance(new ArrayList<>())
                .build();
    }

    @Override
    public InventoryReportResponse generateInventoryReport(UUID distributorId) {
        log.debug("Generating inventory report for distributor: {}", distributorId);

        // Get counts
        long totalProducts = productRepository.countByDistributorIdAndActiveTrue(distributorId);
        long lowStockCount = stockRepository.countLowStockByDistributorId(distributorId);
        long outOfStockCount = stockRepository.countOutOfStockByDistributorId(distributorId);

        // Get low stock items
        Pageable pageable = PageRequest.of(0, 20);
        List<Stock> lowStockItems = stockRepository.findLowStockByDistributorId(distributorId, pageable).getContent();

        List<InventoryReportResponse.StockItem> lowStockList = lowStockItems.stream()
                .map(stock -> InventoryReportResponse.StockItem.builder()
                        .productId(stock.getProduct().getId().toString())
                        .productName(stock.getProduct().getName())
                        .productSku(stock.getProduct().getSku())
                        .warehouseName(stock.getWarehouse().getName())
                        .quantity(stock.getQuantity())
                        .reorderLevel(stock.getReorderLevel())
                        .build())
                .toList();

        return InventoryReportResponse.builder()
                .totalProducts(totalProducts)
                .lowStockCount(lowStockCount)
                .outOfStockCount(outOfStockCount)
                .totalStockValue(BigDecimal.ZERO) // Would need additional calculation
                .lowStockItems(lowStockList)
                .outOfStockItems(new ArrayList<>())
                .warehouseSummaries(new ArrayList<>())
                .build();
    }

    @Override
    public PaymentReportResponse generatePaymentReport(UUID distributorId, LocalDate startDate, LocalDate endDate) {
        log.debug("Generating payment report for distributor: {} from {} to {}", distributorId, startDate, endDate);

        // Get totals
        BigDecimal totalOutstanding = orderRepository.sumOutstandingAmount(distributorId);
        if (totalOutstanding == null) {
            totalOutstanding = BigDecimal.ZERO;
        }

        long unreconciledCount = paymentRepository.countUnreconciledPayments(distributorId);

        return PaymentReportResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalCollected(BigDecimal.ZERO)
                .totalOutstanding(totalOutstanding)
                .totalPayments(0L)
                .unreconciledCount(unreconciledCount)
                .byPaymentMethod(new ArrayList<>())
                .dailyCollections(new ArrayList<>())
                .build();
    }
}
