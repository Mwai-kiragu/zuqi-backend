package com.zuqi.ai.agent.tools;

import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.repository.OrderRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SalesTrendTool {

    private final OrderRepository orderRepository;

    @Tool("Get sales trend for a distributor over a given period. Returns total orders, " +
          "counts by status (PENDING, CONFIRMED, PROCESSING, DELIVERED, CANCELLED), " +
          "total revenue, the period analysed, and the top 5 merchants by revenue in that period. " +
          "Parameters: distributorId (UUID string), periodDays (number of days to look back, default 30).")
    @Transactional(readOnly = true)
    public String getSalesTrend(
            @P("The distributor UUID (e.g. a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11)") String distributorId,
            @P("Number of days to look back (e.g. 7, 30, 90). Default is 30.") String periodDays) {
        log.info("[TOOL CALLED] getSalesTrend distributorId={} periodDays={}", distributorId, periodDays);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            int days = 30;
            if (periodDays != null && !periodDays.isBlank()) {
                try {
                    days = Integer.parseInt(periodDays.trim());
                    if (days <= 0) days = 30;
                } catch (NumberFormatException e) {
                    days = 30;
                }
            }

            LocalDateTime periodStart = LocalDateTime.now().minusDays(days);
            LocalDateTime periodEnd   = LocalDateTime.now();

            List<Order> orders = orderRepository.findByDistributorIdAndDateRange(distId, periodStart, periodEnd);

            long totalOrders     = orders.size();
            long pendingCount    = orders.stream().filter(o -> OrderStatus.PENDING    == o.getStatus()).count();
            long confirmedCount  = orders.stream().filter(o -> OrderStatus.CONFIRMED  == o.getStatus()).count();
            long processingCount = orders.stream().filter(o -> OrderStatus.PROCESSING == o.getStatus()).count();
            long deliveredCount  = orders.stream().filter(o -> OrderStatus.DELIVERED  == o.getStatus()).count();
            long cancelledCount  = orders.stream().filter(o -> OrderStatus.CANCELLED  == o.getStatus()).count();

            BigDecimal totalRevenue = orders.stream()
                    .filter(o -> o.getTotalAmount() != null)
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Top 5 merchants by revenue in the period
            Map<String, BigDecimal> revenueByMerchant = orders.stream()
                    .filter(o -> o.getMerchant() != null && o.getTotalAmount() != null)
                    .collect(Collectors.groupingBy(
                            o -> o.getMerchant().getBusinessName(),
                            Collectors.reducing(BigDecimal.ZERO, Order::getTotalAmount, BigDecimal::add)));

            List<Map.Entry<String, BigDecimal>> top5 = revenueByMerchant.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .limit(5)
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                    "{ \"tool\": \"SalesTrend\", \"distributorId\": \"%s\", \"periodDays\": %d, " +
                    "\"periodStart\": \"%s\", \"periodEnd\": \"%s\", " +
                    "\"totalOrders\": %d, \"pending\": %d, \"confirmed\": %d, \"processing\": %d, " +
                    "\"delivered\": %d, \"cancelled\": %d, \"totalRevenue\": \"%s\", ",
                    distId, days,
                    periodStart.toLocalDate(), periodEnd.toLocalDate(),
                    totalOrders, pendingCount, confirmedCount, processingCount,
                    deliveredCount, cancelledCount, totalRevenue.toPlainString()));

            sb.append("\"topMerchantsByRevenue\": [");
            for (int i = 0; i < top5.size(); i++) {
                Map.Entry<String, BigDecimal> e = top5.get(i);
                sb.append(String.format("{ \"merchant\": \"%s\", \"revenueKES\": \"%s\" }",
                        e.getKey().replace("\"", "'"), e.getValue().toPlainString()));
                if (i < top5.size() - 1) sb.append(", ");
            }
            sb.append("] }");

            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("SalesTrendTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("SalesTrendTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve sales trend: " + e.getMessage() + "\" }";
        }
    }
}
