package com.zuqi.service;

import com.zuqi.api.dto.report.InventoryReportResponse;
import com.zuqi.api.dto.report.PaymentReportResponse;
import com.zuqi.api.dto.report.SalesReportResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface ReportService {

    SalesReportResponse generateSalesReport(UUID distributorId, LocalDate startDate, LocalDate endDate);

    InventoryReportResponse generateInventoryReport(UUID distributorId);

    PaymentReportResponse generatePaymentReport(UUID distributorId, LocalDate startDate, LocalDate endDate);
}
