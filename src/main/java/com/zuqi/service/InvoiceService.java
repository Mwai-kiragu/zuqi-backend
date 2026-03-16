package com.zuqi.service;

import com.zuqi.api.dto.invoice.InvoiceResponse;
import com.zuqi.api.dto.invoice.ManualInvoiceRequest;
import com.zuqi.domain.invoice.InvoiceStatus;
import com.zuqi.domain.order.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InvoiceService {

    InvoiceResponse createManualInvoice(ManualInvoiceRequest request);

    InvoiceResponse createInvoiceFromOrder(Order order);

    InvoiceResponse createInvoiceFromPosSale(UUID saleId);

    InvoiceResponse getInvoiceBySaleId(UUID saleId);

    InvoiceResponse getInvoiceById(UUID id);

    InvoiceResponse getInvoiceByNumber(String invoiceNumber);

    InvoiceResponse getInvoiceByOrderId(UUID orderId);

    Page<InvoiceResponse> getAllInvoices(Pageable pageable);

    Page<InvoiceResponse> getInvoicesByDistributor(UUID distributorId, Pageable pageable);

    Page<InvoiceResponse> getInvoicesByMerchant(UUID merchantId, Pageable pageable);

    Page<InvoiceResponse> getInvoicesByFilters(
            UUID distributorId,
            InvoiceStatus status,
            UUID merchantId,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable);

    Page<InvoiceResponse> searchInvoices(UUID distributorId, String search, Pageable pageable);

    InvoiceResponse sendInvoice(UUID invoiceId, String email);

    InvoiceResponse markAsViewed(UUID invoiceId);

    InvoiceResponse recordPayment(UUID invoiceId, BigDecimal amount, Long paymentMethodId, String externalReference);

    InvoiceResponse cancelInvoice(UUID invoiceId);

    List<InvoiceResponse> getOverdueInvoices();

    void updateOverdueStatuses();

    long getInvoiceCountByStatus(UUID distributorId, InvoiceStatus status);

    java.util.Map<String, Long> getAllStatusCounts(UUID distributorId);
}
