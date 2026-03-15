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
    public DashboardStatsResponse getStats(UUID distributorId, UUID userId, UUID branchId, LocalDate startDate, LocalDate endDate) {
        // Determine effective distributor ID for filtering
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }

        log.debug("Getting dashboard stats for distributor: {}, branch: {}, period: {} - {}",
                effectiveDistributorId, branchId, startDate, endDate);

        // Use provided date range for pulse metrics; default to today when not supplied
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now();
        LocalDate effectiveEnd   = endDate   != null ? endDate   : LocalDate.now();
        LocalDateTime periodStart = effectiveStart.atStartOfDay();
        LocalDateTime periodEnd   = effectiveEnd.atTime(23, 59, 59);

        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // SUPER_ADMIN/ADMIN get system-wide stats when distributorId is null
        boolean isGlobalView = effectiveDistributorId == null;
        boolean hasBranch    = branchId != null && !isGlobalView;
        // When dates are supplied this is a "pulse" call — scope status counts to the period
        boolean hasPeriod    = startDate != null;

        // Order statistics — branch-filtered when branchId is provided
        long totalOrders;
        long ordersToday;
        long pendingOrders;
        long processingOrders;
        long deliveredOrders;
        long readyForDelivery;
        long outForDelivery;
        BigDecimal totalRevenue;
        BigDecimal revenueToday;
        BigDecimal totalOutstanding;

        if (isGlobalView) {
            totalOrders      = orderRepository.count();
            ordersToday      = orderRepository.countOrdersTodayAll(periodStart);
            pendingOrders    = orderRepository.countByStatus(OrderStatus.PENDING);
            processingOrders = orderRepository.countByStatus(OrderStatus.PROCESSING);
            deliveredOrders  = orderRepository.countByStatus(OrderStatus.DELIVERED);
            readyForDelivery = orderRepository.countByStatus(OrderStatus.READY_FOR_DELIVERY);
            outForDelivery   = orderRepository.countByStatus(OrderStatus.OUT_FOR_DELIVERY);
            totalRevenue     = orderRepository.sumTotalRevenueAll();
            revenueToday     = orderRepository.sumRevenueFromDateAll(periodStart);
            totalOutstanding = orderRepository.sumOutstandingAmountAll();
        } else if (hasBranch) {
            totalOrders      = orderRepository.countByDistributorIdAndBranch(effectiveDistributorId, branchId);
            ordersToday      = orderRepository.countOrdersInPeriodByBranch(effectiveDistributorId, periodStart, periodEnd, branchId);
            pendingOrders    = hasPeriod
                    ? orderRepository.countOrdersInPeriodWithStatusByBranch(effectiveDistributorId, OrderStatus.PENDING, periodStart, periodEnd, branchId)
                    : orderRepository.countByDistributorIdAndStatusAndBranch(effectiveDistributorId, OrderStatus.PENDING, branchId);
            processingOrders = hasPeriod
                    ? orderRepository.countOrdersInPeriodWithStatusByBranch(effectiveDistributorId, OrderStatus.PROCESSING, periodStart, periodEnd, branchId)
                    : orderRepository.countByDistributorIdAndStatusAndBranch(effectiveDistributorId, OrderStatus.PROCESSING, branchId);
            deliveredOrders  = hasPeriod
                    ? orderRepository.countOrdersInPeriodWithStatusByBranch(effectiveDistributorId, OrderStatus.DELIVERED, periodStart, periodEnd, branchId)
                    : orderRepository.countByDistributorIdAndStatusAndBranch(effectiveDistributorId, OrderStatus.DELIVERED, branchId);
            readyForDelivery = hasPeriod
                    ? orderRepository.countOrdersInPeriodWithStatusByBranch(effectiveDistributorId, OrderStatus.READY_FOR_DELIVERY, periodStart, periodEnd, branchId)
                    : orderRepository.countByDistributorIdAndStatusAndBranch(effectiveDistributorId, OrderStatus.READY_FOR_DELIVERY, branchId);
            outForDelivery   = hasPeriod
                    ? orderRepository.countOrdersInPeriodWithStatusByBranch(effectiveDistributorId, OrderStatus.OUT_FOR_DELIVERY, periodStart, periodEnd, branchId)
                    : orderRepository.countByDistributorIdAndStatusAndBranch(effectiveDistributorId, OrderStatus.OUT_FOR_DELIVERY, branchId);
            totalRevenue     = orderRepository.sumTotalRevenueByBranch(effectiveDistributorId, branchId);
            revenueToday     = orderRepository.sumRevenueInPeriodByBranch(effectiveDistributorId, periodStart, periodEnd, branchId);
            totalOutstanding = orderRepository.sumOutstandingAmountByBranch(effectiveDistributorId, branchId);
        } else {
            totalOrders      = orderRepository.countByDistributorId(effectiveDistributorId);
            ordersToday      = orderRepository.countOrdersInPeriod(effectiveDistributorId, periodStart, periodEnd);
            pendingOrders    = hasPeriod
                    ? orderRepository.countOrdersInPeriodWithStatus(effectiveDistributorId, OrderStatus.PENDING, periodStart, periodEnd)
                    : orderRepository.countByDistributorIdAndStatus(effectiveDistributorId, OrderStatus.PENDING);
            processingOrders = hasPeriod
                    ? orderRepository.countOrdersInPeriodWithStatus(effectiveDistributorId, OrderStatus.PROCESSING, periodStart, periodEnd)
                    : orderRepository.countByDistributorIdAndStatus(effectiveDistributorId, OrderStatus.PROCESSING);
            deliveredOrders  = hasPeriod
                    ? orderRepository.countOrdersInPeriodWithStatus(effectiveDistributorId, OrderStatus.DELIVERED, periodStart, periodEnd)
                    : orderRepository.countByDistributorIdAndStatus(effectiveDistributorId, OrderStatus.DELIVERED);
            readyForDelivery = hasPeriod
                    ? orderRepository.countOrdersInPeriodWithStatus(effectiveDistributorId, OrderStatus.READY_FOR_DELIVERY, periodStart, periodEnd)
                    : orderRepository.countByDistributorIdAndStatus(effectiveDistributorId, OrderStatus.READY_FOR_DELIVERY);
            outForDelivery   = hasPeriod
                    ? orderRepository.countOrdersInPeriodWithStatus(effectiveDistributorId, OrderStatus.OUT_FOR_DELIVERY, periodStart, periodEnd)
                    : orderRepository.countByDistributorIdAndStatus(effectiveDistributorId, OrderStatus.OUT_FOR_DELIVERY);
            totalRevenue     = orderRepository.sumTotalRevenue(effectiveDistributorId);
            revenueToday     = orderRepository.sumRevenueInPeriod(effectiveDistributorId, periodStart, periodEnd);
            totalOutstanding = orderRepository.sumOutstandingAmount(effectiveDistributorId);
        }

        // Revenue this month (not period-filtered — it's always the calendar month)
        BigDecimal revenueThisMonth = isGlobalView
                ? orderRepository.sumRevenueFromDateAll(startOfMonth)
                : orderRepository.sumRevenueFromDate(effectiveDistributorId, startOfMonth);

        // Merchant / payment / inventory / team stats are not branch-scoped
        long totalMerchants = isGlobalView
                ? customerRepository.countByActiveTrue()
                : customerRepository.countByDistributorIdAndActiveTrue(effectiveDistributorId);
        long newMerchantsThisMonth = isGlobalView
                ? customerRepository.countNewCustomersFromDateAll(startOfMonth)
                : customerRepository.countNewCustomersFromDate(effectiveDistributorId, startOfMonth);
        long unreconciledPayments = isGlobalView
                ? paymentRepository.countAllUnreconciledPayments()
                : paymentRepository.countUnreconciledPayments(effectiveDistributorId);
        long lowStockProducts = isGlobalView
                ? stockRepository.countAllLowStock()
                : stockRepository.countLowStockByDistributorId(effectiveDistributorId);
        long outOfStockProducts = isGlobalView
                ? stockRepository.countAllOutOfStock()
                : stockRepository.countOutOfStockByDistributorId(effectiveDistributorId);
        long salesReps = isGlobalView
                ? userRepository.countByRole("SALES_REP")
                : userRepository.countByRoleAndDistributor("SALES_REP", effectiveDistributorId);

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
        } else if (branchId != null) {
            orders = orderRepository.findRecentOrdersByBranch(effectiveDistributorId, branchId, PageRequest.of(0, limit));
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
