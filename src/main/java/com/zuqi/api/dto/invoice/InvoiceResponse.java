package com.zuqi.api.dto.invoice;

import com.zuqi.api.dto.order.OrderItemResponse;
import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.invoice.InvoiceStatus;
import com.zuqi.domain.pos.PosSaleItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponse {

    private UUID id;
    private String invoiceNumber;
    private String sourceType;
    private UUID orderId;
    private String orderNumber;
    private UUID posOrderId;
    private String posReceiptNumber;
    private String posCustomerName;
    private String posCustomerPhone;
    private String posCashierName;

    // Distributor info
    private UUID distributorId;
    private String distributorName;
    private String distributorAddress;
    private String distributorPhone;
    private String distributorEmail;

    // Merchant info
    private UUID merchantId;
    private String merchantBusinessName;
    private String merchantOwnerName;
    private String merchantAddress;
    private String merchantPhone;
    private String merchantEmail;

    // Amounts
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceDue;

    // Status and dates
    private InvoiceStatus status;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDateTime sentAt;
    private LocalDateTime viewedAt;
    private LocalDateTime paidAt;

    private String recipientEmail;
    private String notes;
    private String termsAndConditions;

    // Order items
    private List<OrderItemResponse> items;

    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InvoiceResponse fromEntity(Invoice invoice) {
        InvoiceResponseBuilder builder = InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .sourceType(invoice.getSourceType())
                .subtotal(invoice.getSubtotal())
                .discountAmount(invoice.getDiscountAmount())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .paidAmount(invoice.getPaidAmount())
                .balanceDue(invoice.getBalanceDue())
                .status(invoice.getStatus())
                .issueDate(invoice.getIssueDate())
                .dueDate(invoice.getDueDate())
                .sentAt(invoice.getSentAt())
                .viewedAt(invoice.getViewedAt())
                .paidAt(invoice.getPaidAt())
                .recipientEmail(invoice.getRecipientEmail())
                .notes(invoice.getNotes())
                .termsAndConditions(invoice.getTermsAndConditions())
                .metadata(invoice.getMetadata())
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt());

        // Order info
        if (invoice.getOrder() != null) {
            builder.orderId(invoice.getOrder().getId())
                   .orderNumber(invoice.getOrder().getOrderNumber());

            // Include order items
            if (invoice.getOrder().getItems() != null) {
                builder.items(invoice.getOrder().getItems().stream()
                        .map(OrderItemResponse::fromEntity)
                        .toList());
            }
        }

        // POS sale info
        if (invoice.getPosOrder() != null) {
            var pos = invoice.getPosOrder();
            builder.posOrderId(pos.getId())
                   .posReceiptNumber(pos.getReceiptNumber())
                   .posCustomerName(pos.getCustomerName())
                   .posCustomerPhone(pos.getCustomerPhone())
                   .posCashierName(pos.getCashier() != null
                           ? pos.getCashier().getFirstName() + " " + pos.getCashier().getLastName()
                           : null);

            // Map POS sale items to the shared items list
            if (pos.getItems() != null && !pos.getItems().isEmpty()) {
                builder.items(pos.getItems().stream()
                        .map(item -> OrderItemResponse.builder()
                                .id(item.getId())
                                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                                .productSku(item.getProductSku())
                                .productName(item.getProductName())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .discountPercent(BigDecimal.ZERO)
                                .totalAmount(item.getLineTotal())
                                .build())
                        .toList());
            }
        }

        // Distributor info
        if (invoice.getDistributor() != null) {
            builder.distributorId(invoice.getDistributor().getId())
                   .distributorName(invoice.getDistributor().getName())
                   .distributorAddress(invoice.getDistributor().getAddress())
                   .distributorPhone(invoice.getDistributor().getPhone())
                   .distributorEmail(invoice.getDistributor().getEmail());
        }

        // Merchant info
        if (invoice.getMerchant() != null) {
            builder.merchantId(invoice.getMerchant().getId())
                   .merchantBusinessName(invoice.getMerchant().getBusinessName())
                   .merchantOwnerName(invoice.getMerchant().getOwnerName())
                   .merchantAddress(invoice.getMerchant().getAddress())
                   .merchantPhone(invoice.getMerchant().getPhone())
                   .merchantEmail(invoice.getMerchant().getEmail());
        }

        return builder.build();
    }
}
