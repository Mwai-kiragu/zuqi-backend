package com.zuqi.ai.demand;

import com.zuqi.api.dto.CashFlowForecastEntry;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.procurement.PurchaseOrder;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.PaymentRepository;
import com.zuqi.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes a day-by-day cash flow forecast using:
 *
 * <ul>
 *   <li><b>Baseline inflow</b>: 30-day average of completed payments</li>
 *   <li><b>Pending receivables</b>: Active orders spread evenly over 7 days</li>
 *   <li><b>Outflows</b>: Pending purchase orders on their expected delivery dates</li>
 *   <li><b>Optimistic/pessimistic bands</b>: ±15% around expected inflow</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashFlowForecastService {

    private static final int   HISTORY_DAYS      = 30;
    private static final int   COLLECTION_WINDOW = 7;   // days to collect pending orders
    private static final double OPTIMISTIC_FACTOR = 1.15;
    private static final double PESSIMISTIC_FACTOR = 0.85;

    private final PaymentRepository       paymentRepository;
    private final OrderRepository         orderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public List<CashFlowForecastEntry> forecast(UUID distributorId, int days) {
        // ── Step 1: historical average daily inflow ──────────────────────────
        LocalDateTime historyFrom = LocalDateTime.now().minusDays(HISTORY_DAYS);
        BigDecimal totalHistorical = paymentRepository
                .findCompletedSince(distributorId, historyFrom)
                .stream()
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgDailyInflow = totalHistorical
                .divide(BigDecimal.valueOf(HISTORY_DAYS), 2, RoundingMode.HALF_UP);

        // ── Step 2: pending receivables → spread over next COLLECTION_WINDOW days ──
        BigDecimal pendingReceivables = orderRepository
                .findPendingReceivablesByDistributor(distributorId)
                .stream()
                .map(this::unreceived)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dailyPendingBoost = pendingReceivables
                .divide(BigDecimal.valueOf(COLLECTION_WINDOW), 2, RoundingMode.HALF_UP);

        // ── Step 3: pending PO outflows grouped by expected delivery date ────
        Map<LocalDate, BigDecimal> outflowByDate = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (PurchaseOrder po : purchaseOrderRepository.findPendingByDistributorId(distributorId)) {
            LocalDate dueDate = po.getExpectedDeliveryDate() != null
                    ? po.getExpectedDeliveryDate()
                    : today.plusDays(7);
            if (!dueDate.isAfter(today.plusDays(days))) {
                outflowByDate.merge(dueDate, safeAmount(po.getTotalAmount()), BigDecimal::add);
            }
        }

        // ── Step 4: build forecast series ──────────────────────────────────
        List<CashFlowForecastEntry> result = new ArrayList<>(days);
        BigDecimal runningBalance = BigDecimal.ZERO;

        for (int i = 0; i < days; i++) {
            LocalDate date = today.plusDays(i);
            BigDecimal inflow = avgDailyInflow.add(i < COLLECTION_WINDOW ? dailyPendingBoost : BigDecimal.ZERO);
            BigDecimal outflow = outflowByDate.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal net = inflow.subtract(outflow);
            runningBalance = runningBalance.add(net);

            result.add(new CashFlowForecastEntry(
                    date.toString(),
                    inflow,
                    scale(inflow.multiply(BigDecimal.valueOf(OPTIMISTIC_FACTOR))),
                    scale(inflow.multiply(BigDecimal.valueOf(PESSIMISTIC_FACTOR))),
                    outflow,
                    net,
                    runningBalance,
                    net.compareTo(BigDecimal.ZERO) < 0
            ));
        }
        return result;
    }

    private BigDecimal unreceived(Order o) {
        BigDecimal paid = o.getPaidAmount() != null ? o.getPaidAmount() : BigDecimal.ZERO;
        return o.getTotalAmount().subtract(paid).max(BigDecimal.ZERO);
    }

    private BigDecimal safeAmount(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
