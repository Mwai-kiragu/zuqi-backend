package com.zuqi.service;

import com.zuqi.api.dto.order.*;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.order.PaymentStatus;
import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OrderService {

    Page<OrderResponse> getAllOrders(Pageable pageable);

    Page<OrderResponse> getOrdersByDistributor(UUID distributorId, Pageable pageable);

    Page<OrderResponse> getOrdersByMerchant(UUID merchantId, Pageable pageable);

    Page<OrderResponse> getOrdersBySalesRep(UUID salesRepId, Pageable pageable);

    Page<OrderResponse> getOrdersByFilters(
            UUID distributorId,
            OrderStatus status,
            UUID merchantId,
            UUID salesRepId,
            PaymentStatus paymentStatus,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable);

    Page<OrderResponse> searchOrders(UUID distributorId, String search, Pageable pageable);

    OrderResponse getOrderById(UUID id);

    OrderResponse getOrderByNumber(String orderNumber);

    OrderResponse createOrder(OrderRequest request, User currentUser);

    OrderResponse updateOrder(UUID id, OrderRequest request);

    OrderResponse updateOrderStatus(UUID id, StatusUpdateRequest request, User currentUser);

    OrderResponse cancelOrder(UUID id, String reason, User currentUser);

    List<OrderStatusHistoryResponse> getOrderStatusHistory(UUID orderId);

    long getOrderCountByStatus(UUID distributorId, OrderStatus status);

    List<OrderResponse> getOverdueOrders();

    OrderResponse assignDriver(UUID orderId, UUID driverId, String notes, User currentUser);

    List<DriverDto> getAvailableDrivers();

    OrderStatsResponse getOrderStats();
}
