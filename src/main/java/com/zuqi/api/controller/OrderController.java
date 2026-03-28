package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.order.*;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.order.PaymentStatus;
import com.zuqi.domain.user.User;
import com.zuqi.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management APIs")
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "Get all orders", description = "Retrieves orders with pagination and optional filters")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getAllOrders(
            @Parameter(description = "Distributor ID filter") @RequestParam(required = false) UUID distributorId,
            @Parameter(description = "Merchant ID filter") @RequestParam(required = false) UUID merchantId,
            @Parameter(description = "Sales rep ID filter") @RequestParam(required = false) UUID salesRepId,
            @Parameter(description = "Order status filter") @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Payment status filter") @RequestParam(required = false) PaymentStatus paymentStatus,
            @Parameter(description = "Start date filter") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date filter") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<OrderResponse> orders;

        if (search != null && !search.isBlank() && distributorId != null) {
            orders = orderService.searchOrders(distributorId, search, pageable);
        } else if (distributorId != null) {
            orders = orderService.getOrdersByFilters(
                    distributorId, status, merchantId, salesRepId, paymentStatus, startDate, endDate, pageable);
        } else if (merchantId != null) {
            orders = orderService.getOrdersByMerchant(merchantId, pageable);
        } else if (salesRepId != null) {
            orders = orderService.getOrdersBySalesRep(salesRepId, pageable);
        } else {
            orders = orderService.getAllOrders(pageable);
        }

        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", description = "Retrieves a specific order by ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @Parameter(description = "Order ID") @PathVariable UUID id) {
        OrderResponse order = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping("/number/{orderNumber}")
    @Operation(summary = "Get order by number", description = "Retrieves a specific order by order number")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByNumber(
            @Parameter(description = "Order number") @PathVariable String orderNumber) {
        OrderResponse order = orderService.getOrderByNumber(orderNumber);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PostMapping
    @Operation(summary = "Create order", description = "Creates a new order")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'SALES_REP', 'MERCHANT', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderRequest request,
            @AuthenticationPrincipal User currentUser) {
        OrderResponse order = orderService.createOrder(request, currentUser);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created successfully", order));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update order", description = "Updates an existing order (only pending orders)")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'SALES_REP', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrder(
            @Parameter(description = "Order ID") @PathVariable UUID id,
            @Valid @RequestBody OrderRequest request) {
        OrderResponse order = orderService.updateOrder(id, request);
        return ResponseEntity.ok(ApiResponse.success("Order updated successfully", order));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update order status", description = "Updates the status of an order")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'WAREHOUSE_MANAGER', 'DRIVER')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @Parameter(description = "Order ID") @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request,
            @AuthenticationPrincipal User currentUser) {
        OrderResponse order = orderService.updateOrderStatus(id, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", order));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel order", description = "Cancels an order")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'SALES_REP', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @Parameter(description = "Order ID") @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal User currentUser) {
        OrderResponse order = orderService.cancelOrder(id, reason, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", order));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get order history", description = "Retrieves the status history of an order")
    public ResponseEntity<ApiResponse<List<OrderStatusHistoryResponse>>> getOrderHistory(
            @Parameter(description = "Order ID") @PathVariable UUID id) {
        List<OrderStatusHistoryResponse> history = orderService.getOrderStatusHistory(id);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/count")
    @Operation(summary = "Get order count by status", description = "Gets the count of orders by status")
    public ResponseEntity<ApiResponse<Long>> getOrderCount(
            @Parameter(description = "Distributor ID") @RequestParam UUID distributorId,
            @Parameter(description = "Order status") @RequestParam OrderStatus status) {
        long count = orderService.getOrderCountByStatus(distributorId, status);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    @GetMapping("/overdue")
    @Operation(summary = "Get overdue orders", description = "Gets orders that are past their payment due date")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'FINANCE')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOverdueOrders() {
        List<OrderResponse> orders = orderService.getOverdueOrders();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @PatchMapping("/{id}/assign-driver")
    @Operation(summary = "Assign driver", description = "Assigns a driver to an order for delivery")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN', 'WAREHOUSE_MANAGER')")
    public ResponseEntity<ApiResponse<OrderResponse>> assignDriver(
            @Parameter(description = "Order ID") @PathVariable UUID id,
            @Valid @RequestBody AssignDriverRequest request,
            @AuthenticationPrincipal User currentUser) {
        OrderResponse order = orderService.assignDriver(id, request.getDriverId(), request.getNotes(), currentUser);
        return ResponseEntity.ok(ApiResponse.success("Driver assigned successfully", order));
    }

    @GetMapping("/available-drivers")
    @Operation(summary = "Get available drivers", description = "Returns active users with DRIVER role for the distributor")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN', 'WAREHOUSE_MANAGER', 'DRIVER')")
    public ResponseEntity<ApiResponse<List<DriverDto>>> getAvailableDrivers() {
        List<DriverDto> drivers = orderService.getAvailableDrivers();
        return ResponseEntity.ok(ApiResponse.success(drivers));
    }
}
