package com.zuqi.service;

import com.zuqi.api.dto.report.InventoryReportResponse;
import com.zuqi.api.dto.report.PaymentReportResponse;
import com.zuqi.api.dto.report.SalesReportResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service interface for report generation.
 */
public interface ReportService {

    /**
     * Generate sales report for a distributor.
     */
    SalesReportResponse generateSalesReport(UUID distributorId, LocalDate startDate, LocalDate endDate);

    /**
     * Generate inventory report for a distributor.
     */
    InventoryReportResponse generateInventoryReport(UUID distributorId);

    /**
     * Generate payment report for a distributor.
     */
    PaymentReportResponse generatePaymentReport(UUID distributorId, LocalDate startDate, LocalDate endDate);
}
