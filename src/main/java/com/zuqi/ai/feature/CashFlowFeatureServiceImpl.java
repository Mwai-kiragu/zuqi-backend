package com.zuqi.ai.feature;

import com.zuqi.repository.ExpenseRepository;
import com.zuqi.repository.InvoiceRepository;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.PaymentRepository;
import com.zuqi.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Computes CashFlowFeatures for a distributor on a target date.
 *
 * Queries:
 * - orders         → pending order value (pipeline)
 * - payments       → collection history (inflows)
 * - invoices       → due dates (upcoming receivables)
 * - expenses       → outflow history
 * - purchase_orders → committed spend (outflows)
 *
 * Cached in Redis with 24h TTL (expiryFeatures cache name is per-batch;
 * cashFlowFeatures is per distributor-date pair).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashFlowFeatureServiceImpl {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final ExpenseRepository expenseRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Cacheable(value = "cashFlowFeatures", key = "#distributorId + '_' + #targetDate")
    public CashFlowFeatures computeFeatures(UUID distributorId, LocalDate targetDate) {
        LocalDateTime now = targetDate.atStartOfDay();

        // ── Inflow signals ────────────────────────────────────────────────────
        LocalDateTime start7d  = now.minusDays(7);
        LocalDateTime start30d = now.minusDays(30);

        double collections7d  = sumPayments(distributorId, start7d, now);
        double collections30d = sumPayments(distributorId, start30d, now);
        double avg7d  = collections7d  / 7.0;
        double avg30d = collections30d / 30.0;
        double collectionTrend = avg7d - avg30d;

        // Overdue receivables (invoices unpaid > 30 days past due)
        double overdueReceivables = computeOverdueReceivables(distributorId, targetDate);

        // Payment due in next 7 days (outstanding invoices)
        double paymentDueNext7d = computePaymentsDueNext7d(distributorId, targetDate);

        // Pending orders value (orders confirmed but not yet invoiced/delivered)
        double pendingOrdersValue = computePendingOrdersValue(distributorId);

        // ── Outflow signals ───────────────────────────────────────────────────
        double expenses30d = sumExpenses(distributorId, start30d, now);
        double avgDailyExpenses30d = expenses30d / 30.0;

        double upcomingSupplierPayments = computeUpcomingSupplierPayments(distributorId, targetDate);
        double pendingPurchaseOrdersValue = computePendingPurchaseOrdersValue(distributorId);

        // ── Lagged net flows ─────────────────────────────────────────────────
        double net7dAgo = laggedNetCashFlow(distributorId, targetDate.minusDays(7));
        double net30dAgo = laggedNetCashFlow(distributorId, targetDate.minusDays(30));

        // ── Calendar signals ─────────────────────────────────────────────────
        int dayOfWeek  = targetDate.getDayOfWeek().getValue();  // 1=Mon … 7=Sun
        int dayOfMonth = targetDate.getDayOfMonth();
        double isPaydayWeek = dayOfMonth >= 25 ? 1.0 : 0.0;
        double isMonthEnd   = dayOfMonth >= 25 ? 1.0 : 0.0;

        return new CashFlowFeatures(
                distributorId,
                targetDate,
                pendingOrdersValue,
                avg7d,
                avg30d,
                collectionTrend,
                overdueReceivables,
                paymentDueNext7d,
                pendingPurchaseOrdersValue,
                avgDailyExpenses30d,
                upcomingSupplierPayments,
                dayOfWeek,
                dayOfMonth,
                isPaydayWeek,
                isMonthEnd,
                net7dAgo,
                net30dAgo
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private double sumPayments(UUID distributorId, LocalDateTime from, LocalDateTime to) {
        try {
            return paymentRepository
                    .findByDistributorIdAndPaymentDateBetween(distributorId, from, to)
                    .stream()
                    .filter(p -> p.getAmount() != null)
                    .mapToDouble(p -> p.getAmount().doubleValue())
                    .sum();
        } catch (Exception e) {
            log.warn("Error summing payments for distributor {}: {}", distributorId, e.getMessage());
            return 0.0;
        }
    }

    private double sumExpenses(UUID distributorId, LocalDateTime from, LocalDateTime to) {
        try {
            return expenseRepository
                    .findByDistributorIdAndDateBetween(distributorId, from.toLocalDate(), to.toLocalDate())
                    .stream()
                    .filter(ex -> ex.getAmount() != null)
                    .mapToDouble(ex -> ex.getAmount().doubleValue())
                    .sum();
        } catch (Exception e) {
            log.warn("Error summing expenses for distributor {}: {}", distributorId, e.getMessage());
            return 0.0;
        }
    }

    private double computeOverdueReceivables(UUID distributorId, LocalDate asOf) {
        // Invoices unpaid and due > 30 days ago
        try {
            LocalDate overdueCutoff = asOf.minusDays(30);
            return invoiceRepository
                    .findByDistributorIdAndDueDateBeforeAndPaidFalse(distributorId, overdueCutoff)
                    .stream()
                    .filter(inv -> inv.getTotalAmount() != null)
                    .mapToDouble(inv -> inv.getTotalAmount().doubleValue())
                    .sum();
        } catch (Exception e) {
            log.warn("Error computing overdue receivables for {}: {}", distributorId, e.getMessage());
            return 0.0;
        }
    }

    private double computePaymentsDueNext7d(UUID distributorId, LocalDate asOf) {
        try {
            LocalDate end = asOf.plusDays(7);
            return invoiceRepository
                    .findByDistributorIdAndDueDateBetweenAndPaidFalse(distributorId, asOf, end)
                    .stream()
                    .filter(inv -> inv.getTotalAmount() != null)
                    .mapToDouble(inv -> inv.getTotalAmount().doubleValue())
                    .sum();
        } catch (Exception e) {
            log.warn("Error computing upcoming payments for {}: {}", distributorId, e.getMessage());
            return 0.0;
        }
    }

    private double computePendingOrdersValue(UUID distributorId) {
        try {
            return orderRepository
                    .findPendingOrdersByDistributorId(distributorId)
                    .stream()
                    .filter(o -> o.getTotalAmount() != null)
                    .mapToDouble(o -> o.getTotalAmount().doubleValue())
                    .sum();
        } catch (Exception e) {
            log.warn("Error computing pending orders for {}: {}", distributorId, e.getMessage());
            return 0.0;
        }
    }

    private double computeUpcomingSupplierPayments(UUID distributorId, LocalDate asOf) {
        try {
            LocalDate end = asOf.plusDays(7);
            return purchaseOrderRepository
                    .findByDistributorIdAndExpectedDeliveryDateBetween(distributorId, asOf, end)
                    .stream()
                    .filter(po -> po.getTotalAmount() != null)
                    .mapToDouble(po -> po.getTotalAmount().doubleValue())
                    .sum();
        } catch (Exception e) {
            log.warn("Error computing supplier payments for {}: {}", distributorId, e.getMessage());
            return 0.0;
        }
    }

    private double computePendingPurchaseOrdersValue(UUID distributorId) {
        try {
            return purchaseOrderRepository
                    .findPendingByDistributorId(distributorId)
                    .stream()
                    .filter(po -> po.getTotalAmount() != null)
                    .mapToDouble(po -> po.getTotalAmount().doubleValue())
                    .sum();
        } catch (Exception e) {
            log.warn("Error computing pending POs for {}: {}", distributorId, e.getMessage());
            return 0.0;
        }
    }

    private double laggedNetCashFlow(UUID distributorId, LocalDate date) {
        try {
            LocalDateTime from = date.atStartOfDay();
            LocalDateTime to   = date.atTime(23, 59, 59);
            double inflow  = sumPayments(distributorId, from, to);
            double outflow = sumExpenses(distributorId, from, to);
            return inflow - outflow;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
