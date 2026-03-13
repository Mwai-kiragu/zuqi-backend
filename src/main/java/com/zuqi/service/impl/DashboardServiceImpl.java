package com.zuqi.service.impl;

import com.zuqi.api.dto.dashboard.*;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.repository.*;
import com.zuqi.service.DashboardService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    @Override
    public DashboardStatsResponse getStats(UUID distributorId, UUID userId, UUID branchId) {
        // Determine effective distributor ID for filtering
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        log.debug("Getting dashboard stats for distributor: {}", effectiveDistributorId);

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // SUPER_ADMIN/ADMIN get system-wide stats when distributorId is null
        boolean isGlobalView = effectiveDistributorId == null;

        // Order statistics
        long totalOrders = isGlobalView ? orderRepository.count() : orderRepository.countByDistributorId(effectiveDistributorId);
        long ordersToday = isGlobalView ? orderRepository.countOrdersTodayAll(startOfToday) : orderRepository.countOrdersToday(effectiveDistributorId, startOfToday);
        long pendingOrders = isGlobalView ? orderRepository.countByStatus(OrderStatus.PENDING) : orderRepository.countByDistributorIdAndStatus(effectiveDistributorId, OrderStatus.PENDING);
        long processingOrders = isGlobalView ? orderRepository.countByStatus(OrderStatus.PROCESSING) : orderRepository.countByDistributorIdAndStatus(effectiveDistributorId, OrderStatus.PROCESSING);
        long deliveredOrders = isGlobalView ? orderRepository.countByStatus(OrderStatus.DELIVERED) : orderRepository.countByDistributorIdAndStatus(effectiveDistributorId, OrderStatus.DELIVERED);

        // Revenue statistics
        BigDecimal totalRevenue = isGlobalView ? orderRepository.sumTotalRevenueAll() : orderRepository.sumTotalRevenue(effectiveDistributorId);
        BigDecimal revenueToday = isGlobalView ? orderRepository.sumRevenueFromDateAll(startOfToday) : orderRepository.sumRevenueFromDate(effectiveDistributorId, startOfToday);
        BigDecimal revenueThisMonth = isGlobalView ? orderRepository.sumRevenueFromDateAll(startOfMonth) : orderRepository.sumRevenueFromDate(effectiveDistributorId, startOfMonth);
        BigDecimal totalOutstanding = isGlobalView ? orderRepository.sumOutstandingAmountAll() : orderRepository.sumOutstandingAmount(effectiveDistributorId);

        // Merchant statistics
        long totalMerchants = isGlobalView ? customerRepository.countByActiveTrue() : customerRepository.countByDistributorIdAndActiveTrue(effectiveDistributorId);
        long newMerchantsThisMonth = isGlobalView ? customerRepository.countNewCustomersFromDateAll(startOfMonth) : customerRepository.countNewCustomersFromDate(effectiveDistributorId, startOfMonth);

        // Payment statistics
        long unreconciledPayments = isGlobalView ? paymentRepository.countAllUnreconciledPayments() : paymentRepository.countUnreconciledPayments(effectiveDistributorId);

        // Inventory statistics
        long lowStockProducts = isGlobalView ? stockRepository.countAllLowStock() : stockRepository.countLowStockByDistributorId(effectiveDistributorId);
        long outOfStockProducts = isGlobalView ? stockRepository.countAllOutOfStock() : stockRepository.countOutOfStockByDistributorId(effectiveDistributorId);

        // Sales team statistics
        long salesReps = isGlobalView ? userRepository.countByRole("SALES_REP") : userRepository.countByRoleAndDistributor("SALES_REP", effectiveDistributorId);

        // Pending deliveries (orders that are out for delivery or ready for delivery)
        long readyForDelivery = isGlobalView ? orderRepository.countByStatus(OrderStatus.READY_FOR_DELIVERY) : orderRepository.countByDistributorIdAndStatus(effectiveDistributorId, OrderStatus.READY_FOR_DELIVERY);
        long outForDelivery = isGlobalView ? orderRepository.countByStatus(OrderStatus.OUT_FOR_DELIVERY) : orderRepository.countByDistributorIdAndStatus(effectiveDistributorId, OrderStatus.OUT_FOR_DELIVERY);

        return DashboardStatsResponse.builder()
                .totalOrders(totalOrders)
                .ordersToday(ordersToday)
                .pendingOrders(pendingOrders)
                .processingOrders(processingOrders)
                .deliveredOrders(deliveredOrders)
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .revenueToday(revenueToday != null ? revenueToday : BigDecimal.ZERO)
                .revenueThisMonth(revenueThisMonth != null ? revenueThisMonth : BigDecimal.ZERO)
                .totalMerchants(totalMerchants)
                .activeMerchants(totalMerchants)
                .newMerchantsThisMonth(newMerchantsThisMonth)
                .unreconciledPayments(unreconciledPayments)
                .pendingPayments(unreconciledPayments)
                .totalOutstanding(totalOutstanding != null ? totalOutstanding : BigDecimal.ZERO)
                .lowStockProducts(lowStockProducts)
                .outOfStockProducts(outOfStockProducts)
                .pendingDeliveries(readyForDelivery)
                .outForDelivery(outForDelivery)
                .activeSalesReps(salesReps)
                .totalSalesReps(salesReps)
                .build();
    }

    @Override
    public List<RecentOrderResponse> getRecentOrders(UUID distributorId, UUID userId, int limit, UUID branchId) {
        // Determine effective distributor ID for filtering
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        log.debug("Getting recent orders for distributor: {}, limit: {}", effectiveDistributorId, limit);

        // SUPER_ADMIN/ADMIN get all orders when distributorId is null
        Page<Order> orders;
        if (effectiveDistributorId == null) {
            orders = orderRepository.findRecentOrdersAll(PageRequest.of(0, limit));
        } else {
            orders = orderRepository.findRecentOrders(effectiveDistributorId, PageRequest.of(0, limit));
        }

        return orders.getContent().stream()
                .map(this::mapToRecentOrderResponse)
                .toList();
    }

    @Override
    public List<TopMerchantResponse> getTopMerchants(UUID distributorId, int limit) {
        // Determine effective distributor ID for filtering
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        log.debug("Getting top merchants for distributor: {}, limit: {}", effectiveDistributorId, limit);

        // For admins without distributorId, return empty list (would need a global query)
        if (effectiveDistributorId == null) {
            return new ArrayList<>();
        }

        List<Object[]> results = orderRepository.findTopMerchantsByRevenue(
                effectiveDistributorId, PageRequest.of(0, limit));

        return results.stream()
                .map(row -> TopMerchantResponse.builder()
                        .id((UUID) row[0])
                        .businessName((String) row[1])
                        .city((String) row[2])
                        .totalOrders((Long) row[3])
                        .totalSpent((BigDecimal) row[4])
                        .build())
                .toList();
    }

    @Override
    public RevenueChartData getRevenueChart(UUID distributorId, LocalDate startDate, LocalDate endDate) {
        // Determine effective distributor ID for filtering
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        log.debug("Getting revenue chart for distributor: {} from {} to {}",
                effectiveDistributorId, startDate, endDate);

        // For admins without distributorId, return empty chart data (would need a global query)
        if (effectiveDistributorId == null) {
            return RevenueChartData.builder()
                    .daily(new ArrayList<>())
                    .totalPeriod(BigDecimal.ZERO)
                    .averageDaily(BigDecimal.ZERO)
                    .build();
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Object[]> dailyData = orderRepository.findDailyRevenueData(
                effectiveDistributorId, startDateTime, endDateTime);

        List<RevenueChartData.ChartDataPoint> dailyPoints = new ArrayList<>();
        BigDecimal totalPeriod = BigDecimal.ZERO;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");

        for (Object[] row : dailyData) {
            LocalDate date = (LocalDate) row[0];
            Long count = (Long) row[1];
            BigDecimal revenue = (BigDecimal) row[2];

            dailyPoints.add(RevenueChartData.ChartDataPoint.builder()
                    .label(date.format(formatter))
                    .revenue(revenue != null ? revenue : BigDecimal.ZERO)
                    .orderCount(count)
                    .build());

            if (revenue != null) {
                totalPeriod = totalPeriod.add(revenue);
            }
        }

        BigDecimal averageDaily = dailyPoints.isEmpty() ? BigDecimal.ZERO :
                totalPeriod.divide(BigDecimal.valueOf(dailyPoints.size()), 2, RoundingMode.HALF_UP);

        return RevenueChartData.builder()
                .daily(dailyPoints)
                .totalPeriod(totalPeriod)
                .averageDaily(averageDaily)
                .build();
    }

    private RecentOrderResponse mapToRecentOrderResponse(Order order) {
        return RecentOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .merchantName(order.getMerchant() != null ? order.getMerchant().getBusinessName() : null)
                .status(order.getStatus().name())
                .paymentStatus(order.getPaymentStatus().name())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
