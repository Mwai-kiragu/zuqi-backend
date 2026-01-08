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

/**
 * Service interface for Order operations.
 */
public interface OrderService {

    /**
     * Get all orders with pagination.
     */
    Page<OrderResponse> getAllOrders(Pageable pageable);

    /**
     * Get orders by distributor ID with pagination.
     */
    Page<OrderResponse> getOrdersByDistributor(UUID distributorId, Pageable pageable);

    /**
     * Get orders by merchant ID with pagination.
     */
    Page<OrderResponse> getOrdersByMerchant(UUID merchantId, Pageable pageable);

    /**
     * Get orders by sales rep ID with pagination.
     */
    Page<OrderResponse> getOrdersBySalesRep(UUID salesRepId, Pageable pageable);

    /**
     * Get orders with filters.
     */
    Page<OrderResponse> getOrdersByFilters(
            UUID distributorId,
            OrderStatus status,
            UUID merchantId,
            UUID salesRepId,
            PaymentStatus paymentStatus,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable);

    /**
     * Search orders.
     */
    Page<OrderResponse> searchOrders(UUID distributorId, String search, Pageable pageable);

    /**
     * Get order by ID.
     */
    OrderResponse getOrderById(UUID id);

    /**
     * Get order by order number.
     */
    OrderResponse getOrderByNumber(String orderNumber);

    /**
     * Create a new order.
     */
    OrderResponse createOrder(OrderRequest request, User currentUser);

    /**
     * Update an existing order.
     */
    OrderResponse updateOrder(UUID id, OrderRequest request);

    /**
     * Update order status.
     */
    OrderResponse updateOrderStatus(UUID id, StatusUpdateRequest request, User currentUser);

    /**
     * Cancel an order.
     */
    OrderResponse cancelOrder(UUID id, String reason, User currentUser);

    /**
     * Get order status history.
     */
    List<OrderStatusHistoryResponse> getOrderStatusHistory(UUID orderId);

    /**
     * Get order count by status for a distributor.
     */
    long getOrderCountByStatus(UUID distributorId, OrderStatus status);

    /**
     * Get overdue orders.
     */
    List<OrderResponse> getOverdueOrders();
}
