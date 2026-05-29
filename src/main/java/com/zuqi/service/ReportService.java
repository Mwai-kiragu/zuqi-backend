package com.zuqi.service;

import com.zuqi.api.dto.report.InventoryReportResponse;
import com.zuqi.api.dto.report.PaymentReportResponse;
import com.zuqi.api.dto.report.SalesReportResponse;

import java.time.LocalDate;

public interface ReportService {

    SalesReportResponse generateSalesReport(LocalDate startDate, LocalDate endDate);

    InventoryReportResponse generateInventoryReport();

    PaymentReportResponse generatePaymentReport(LocalDate startDate, LocalDate endDate);
}
