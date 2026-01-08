package com.zuqi.service.impl;

import com.zuqi.api.dto.dashboard.*;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.repository.*;
import com.zuqi.service.DashboardService;
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

/**
 * Implementation of DashboardService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final OrderRepository orderRepository;
    private final MerchantRepository merchantRepository;
    private final PaymentRepository paymentRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;

    @Override
    public DashboardStatsResponse getStats(UUID distributorId, UUID userId) {
        log.debug("Getting dashboard stats for distributor: {}", distributorId);

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // Order statistics
        long totalOrders = orderRepository.countByDistributorId(distributorId);
        long ordersToday = orderRepository.countOrdersToday(distributorId, startOfToday);
        long pendingOrders = orderRepository.countByDistributorIdAndStatus(distributorId, OrderStatus.PENDING);
        long processingOrders = orderRepository.countByDistributorIdAndStatus(distributorId, OrderStatus.PROCESSING);
        long deliveredOrders = orderRepository.countByDistributorIdAndStatus(distributorId, OrderStatus.DELIVERED);

        // Revenue statistics
        BigDecimal totalRevenue = orderRepository.sumTotalRevenue(distributorId);
        BigDecimal revenueToday = orderRepository.sumRevenueFromDate(distributorId, startOfToday);
        BigDecimal revenueThisMonth = orderRepository.sumRevenueFromDate(distributorId, startOfMonth);
        BigDecimal totalOutstanding = orderRepository.sumOutstandingAmount(distributorId);

        // Merchant statistics
        long totalMerchants = merchantRepository.countByDistributorIdAndActiveTrue(distributorId);
        long newMerchantsThisMonth = merchantRepository.countNewMerchantsFromDate(distributorId, startOfMonth);

        // Payment statistics
        long unreconciledPayments = paymentRepository.countUnreconciledPayments(distributorId);

        // Inventory statistics
        long lowStockProducts = stockRepository.countLowStockByDistributorId(distributorId);
        long outOfStockProducts = stockRepository.countOutOfStockByDistributorId(distributorId);

        // Sales team statistics
        long salesReps = userRepository.countByRoleAndDistributor("SALES_REP", distributorId);

        // Pending deliveries (orders that are out for delivery or ready for delivery)
        long readyForDelivery = orderRepository.countByDistributorIdAndStatus(distributorId, OrderStatus.READY_FOR_DELIVERY);
        long outForDelivery = orderRepository.countByDistributorIdAndStatus(distributorId, OrderStatus.OUT_FOR_DELIVERY);

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
    public List<RecentOrderResponse> getRecentOrders(UUID distributorId, UUID userId, int limit) {
        log.debug("Getting recent orders for distributor: {}, limit: {}", distributorId, limit);

        Page<Order> orders = orderRepository.findRecentOrders(distributorId, PageRequest.of(0, limit));

        return orders.getContent().stream()
                .map(this::mapToRecentOrderResponse)
                .toList();
    }

    @Override
    public List<TopMerchantResponse> getTopMerchants(UUID distributorId, int limit) {
        log.debug("Getting top merchants for distributor: {}, limit: {}", distributorId, limit);

        List<Object[]> results = orderRepository.findTopMerchantsByRevenue(
                distributorId, PageRequest.of(0, limit));

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
        log.debug("Getting revenue chart for distributor: {} from {} to {}",
                distributorId, startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Object[]> dailyData = orderRepository.findDailyRevenueData(
                distributorId, startDateTime, endDateTime);

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
