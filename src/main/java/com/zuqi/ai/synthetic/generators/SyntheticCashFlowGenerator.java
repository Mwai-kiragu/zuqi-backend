package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.dto.SyntheticCashFlowSnapshot;
import com.zuqi.ai.synthetic.dto.SyntheticOrder;
import com.zuqi.ai.synthetic.dto.SyntheticPayment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates synthetic daily cash flow snapshots for training the cash flow
 * prediction model (Model #12).
 *
 * Logic (from phase2-plan.md Section 3.2):
 * - Aggregates daily inflows (payments) and outflows (simulated expenses + supplier payments)
 * - Applies seasonal patterns: month-end spikes, payday effects
 * - Injects occasional shortfall periods (low inflow + high outflow for 5–10 day stretches)
 *
 * Generates 365 days of daily snapshots per distributor bucket.
 */
@Component
@Slf4j
public class SyntheticCashFlowGenerator {

    private static final int HISTORY_DAYS = 365;
    private static final int SHORTFALL_PERIODS = 4; // per year

    /**
     * Generate daily cash flow snapshots from existing synthetic orders and payments.
     *
     * @param orders   synthetic orders (for pending order value)
     * @param payments synthetic payments (for inflow)
     * @param seed     random seed
     * @return list of daily snapshots sorted by date ascending
     */
    public List<SyntheticCashFlowSnapshot> generate(
            List<SyntheticOrder> orders,
            List<SyntheticPayment> payments,
            long seed) {

        Random rng = new Random(seed);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(HISTORY_DAYS - 1);

        // Group payments by date
        Map<LocalDate, List<SyntheticPayment>> paymentsByDate = payments.stream()
                .collect(Collectors.groupingBy(p -> p.paymentDate().toLocalDate()));

        // Build a running daily inflow series for lagged features
        Map<LocalDate, Double> dailyInflow = new LinkedHashMap<>();
        Map<LocalDate, Double> dailyOutflow = new LinkedHashMap<>();

        // Determine shortfall periods: random 5–10 day stretches
        Set<LocalDate> shortfallDates = new HashSet<>();
        for (int s = 0; s < SHORTFALL_PERIODS; s++) {
            LocalDate sfStart = startDate.plusDays(rng.nextInt(HISTORY_DAYS - 10));
            int sfLength = 5 + rng.nextInt(6);
            for (int d = 0; d < sfLength; d++) {
                shortfallDates.add(sfStart.plusDays(d));
            }
        }

        // First pass: compute raw inflow and outflow per day
        for (int d = 0; d < HISTORY_DAYS; d++) {
            LocalDate date = startDate.plusDays(d);
            boolean isShortfall = shortfallDates.contains(date);

            // Inflow: sum payments received + seasonal multiplier
            double rawInflow = paymentsByDate
                    .getOrDefault(date, List.of())
                    .stream()
                    .mapToDouble(p -> p.amount().doubleValue())
                    .sum();

            // Add base inflow for days with no matching payments
            if (rawInflow == 0) {
                rawInflow = 50_000 + rng.nextDouble() * 150_000; // KES 50k–200k baseline
            }

            // Month-end payday spike (days 25–30)
            boolean isPaydayWeek = date.getDayOfMonth() >= 25;
            if (isPaydayWeek) {
                rawInflow *= (1.3 + rng.nextDouble() * 0.4); // 30–70% spike
            }

            // Shortfall: inflow drops 60%
            if (isShortfall) {
                rawInflow *= 0.40;
            }

            // Outflow: expenses + supplier payments
            double baseExpense = 30_000 + rng.nextDouble() * 70_000; // KES 30k–100k
            double supplierPayment = 0;

            // Month-end: large supplier payments
            if (date.getDayOfMonth() == 1 || date.getDayOfMonth() == 15) {
                supplierPayment = 100_000 + rng.nextDouble() * 400_000;
            }

            // Shortfall: outflow spikes
            if (isShortfall) {
                baseExpense *= 1.5;
                supplierPayment += 200_000 + rng.nextDouble() * 300_000;
            }

            dailyInflow.put(date, rawInflow);
            dailyOutflow.put(date, baseExpense + supplierPayment);
        }

        // Second pass: build snapshots with lagged and rolling features
        List<SyntheticCashFlowSnapshot> snapshots = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>(dailyInflow.keySet());
        Collections.sort(dates);

        for (int i = 0; i < dates.size(); i++) {
            LocalDate date = dates.get(i);
            double inflow = dailyInflow.get(date);
            double outflow = dailyOutflow.get(date);
            double net = inflow - outflow;

            // Rolling averages
            double avg7dIn = rollingAvg(dailyInflow, dates, i, 7);
            double avg30dIn = rollingAvg(dailyInflow, dates, i, 30);
            double avg30dOut = rollingAvg(dailyOutflow, dates, i, 30);

            // Lagged net cash flows
            double laggedNet7d = i >= 7
                    ? dailyInflow.get(dates.get(i - 7)) - dailyOutflow.get(dates.get(i - 7))
                    : net;
            double laggedNet30d = i >= 30
                    ? dailyInflow.get(dates.get(i - 30)) - dailyOutflow.get(dates.get(i - 30))
                    : net;

            double collectionTrend = avg7dIn - avg30dIn;

            // Pending orders: rough estimate based on recent order volume
            double pendingOrders = avg7dIn * 2.5;

            // Overdue receivables: cumulative unpaid (simplified)
            double overdueReceivables = Math.max(0, avg30dIn * 1.2 - avg30dIn);

            // Upcoming payments
            double paymentDueNext7d = avg30dIn * 0.8;
            double pendingPoValue = avg30dOut * 3.0;
            double upcomingSupplierPayments = (date.getDayOfMonth() >= 10 && date.getDayOfMonth() <= 15)
                    || (date.getDayOfMonth() >= 28) ? avg30dOut * 5 : avg30dOut;

            boolean isPaydayWeek = date.getDayOfMonth() >= 25;
            boolean isMonthEnd = date.getDayOfMonth() >= 25;

            snapshots.add(new SyntheticCashFlowSnapshot(
                    UUID.randomUUID(),
                    null,
                    date,
                    inflow,
                    outflow,
                    net,
                    pendingOrders,
                    overdueReceivables,
                    paymentDueNext7d,
                    avg7dIn,
                    avg30dIn,
                    collectionTrend,
                    pendingPoValue,
                    avg30dOut,
                    upcomingSupplierPayments,
                    isPaydayWeek,
                    isMonthEnd,
                    laggedNet7d,
                    laggedNet30d
            ));
        }

        log.info("Generated {} synthetic cash flow snapshots ({} shortfall days)",
                snapshots.size(), shortfallDates.size());
        return snapshots;
    }

    private double rollingAvg(Map<LocalDate, Double> series, List<LocalDate> dates,
                               int currentIdx, int windowDays) {
        int fromIdx = Math.max(0, currentIdx - windowDays);
        double sum = 0;
        int count = 0;
        for (int i = fromIdx; i < currentIdx; i++) {
            sum += series.getOrDefault(dates.get(i), 0.0);
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }
}
