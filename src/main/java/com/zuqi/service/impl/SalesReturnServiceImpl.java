package com.zuqi.service.impl;

import com.zuqi.api.dto.returns.*;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.returns.*;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.SalesReturnService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesReturnServiceImpl implements SalesReturnService {

    private final SalesReturnRepository  salesReturnRepository;
    private final OrderRepository         orderRepository;
    private final CustomerRepository      customerRepository;
    private final ProductRepository       productRepository;
    private final UserRepository          userRepository;
    private final DistributorRepository   distributorRepository;
    private final SecurityUtils           securityUtils;

    @Override
    @Transactional
    public SalesReturnResponse create(CreateSalesReturnRequest request, UUID createdById) {

        // Resolve distributor
        UUID distId = securityUtils.getDistributorIdForFiltering();
        Distributor distributor = null;
        if (distId != null) {
            distributor = distributorRepository.findById(distId)
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", distId));
        } else if (request.getOrderId() != null) {
            Order linkedOrder = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()));
            distributor = linkedOrder.getDistributor();
        }
        if (distributor == null) throw new ValidationException("Cannot determine distributor for return");

        Order order = request.getOrderId() != null
                ? orderRepository.findById(request.getOrderId())
                        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()))
                : null;

        Customer customer = request.getCustomerId() != null
                ? customerRepository.findById(request.getCustomerId())
                        .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()))
                : null;

        User createdBy = createdById != null
                ? userRepository.findById(createdById).orElse(null)
                : null;

        SalesReturn sr = SalesReturn.builder()
                .returnNumber(generateNumber("SR"))
                .distributor(distributor)
                .order(order)
                .customer(customer)
                .reason(request.getReason())
                .refundMethod(request.getRefundMethod())
                .status(ReturnStatus.DRAFT)
                .totalAmount(BigDecimal.ZERO)
                .createdBy(createdBy)
                .build();

        final Distributor finalDistributor = distributor;
        List<SalesReturnItem> items = request.getItems().stream().map(line -> {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", line.getProductId()));
            BigDecimal total = line.getUnitPrice().multiply(line.getQuantity());
            return SalesReturnItem.builder()
                    .salesReturn(sr)
                    .product(product)
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPrice())
                    .totalAmount(total)
                    .reason(line.getReason())
                    .build();
        }).collect(Collectors.toList());

        sr.setItems(items);
        sr.setTotalAmount(items.stream().map(SalesReturnItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));

        return toResponse(salesReturnRepository.save(sr));
    }

    @Override
    @Transactional
    public SalesReturnResponse confirm(UUID id) {
        SalesReturn sr = findOrThrow(id);
        if (sr.getStatus() != ReturnStatus.DRAFT) {
            throw new ValidationException("Only DRAFT returns can be confirmed");
        }
        sr.setStatus(ReturnStatus.CONFIRMED);
        return toResponse(salesReturnRepository.save(sr));
    }

    @Override
    @Transactional
    public SalesReturnResponse cancel(UUID id) {
        SalesReturn sr = findOrThrow(id);
        if (sr.getStatus() == ReturnStatus.CONFIRMED) {
            throw new ValidationException("Confirmed returns cannot be cancelled");
        }
        sr.setStatus(ReturnStatus.CANCELLED);
        return toResponse(salesReturnRepository.save(sr));
    }

    @Override
    public SalesReturnResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public Page<SalesReturnResponse> getAll(Pageable pageable) {
        UUID distId = securityUtils.getDistributorIdForFiltering();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (distId != null) {
            return salesReturnRepository.findByDistributorId(distId, pageable).map(this::toResponse);
        } else if (merchantId != null) {
            return salesReturnRepository.findByDistributorMerchantId(merchantId, pageable).map(this::toResponse);
        }
        return salesReturnRepository.findAll(pageable).map(this::toResponse);
    }

    private SalesReturn findOrThrow(UUID id) {
        return salesReturnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SalesReturn", "id", id));
    }

    private String generateNumber(String prefix) {
        return prefix + "-" + System.currentTimeMillis();
    }

    private SalesReturnResponse toResponse(SalesReturn sr) {
        List<SalesReturnResponse.ItemResponse> items = sr.getItems().stream()
                .map(i -> SalesReturnResponse.ItemResponse.builder()
                        .id(i.getId())
                        .productId(i.getProduct().getId())
                        .productName(i.getProduct().getName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .totalAmount(i.getTotalAmount())
                        .reason(i.getReason())
                        .build())
                .collect(Collectors.toList());
        return SalesReturnResponse.builder()
                .id(sr.getId())
                .returnNumber(sr.getReturnNumber())
                .distributorId(sr.getDistributor().getId())
                .orderId(sr.getOrder() != null ? sr.getOrder().getId() : null)
                .customerId(sr.getCustomer() != null ? sr.getCustomer().getId() : null)
                .customerName(sr.getCustomer() != null ? sr.getCustomer().getBusinessName() : null)
                .reason(sr.getReason())
                .status(sr.getStatus().name())
                .totalAmount(sr.getTotalAmount())
                .refundMethod(sr.getRefundMethod())
                .items(items)
                .createdAt(sr.getCreatedAt())
                .updatedAt(sr.getUpdatedAt())
                .build();
    }
}
