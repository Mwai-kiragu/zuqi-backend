package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.PriceTrend;
import com.zuqi.repository.PriceTrendRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Agent tool: supplier-product price trend analysis.
 *
 * <p>Reads from ai_price_trends (pre-computed by PriceTrendJob monthly).
 * Statistical — no AI confidence modifier needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceTrendTool {

    private final PriceTrendRepository priceTrendRepository;

    @Tool("Get price trends for supplier-product combinations in a distributor. " +
          "Returns trend direction (INCREASING, DECREASING, STABLE), " +
          "percentage price change over 3 months, current unit price vs market average, " +
          "and price volatility. " +
          "Use this to advise on procurement timing: buy more now if prices are rising, " +
          "or wait if prices are falling. " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getPriceTrends(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getPriceTrends distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());
            List<PriceTrend> trends = priceTrendRepository.findByDistributorId(distId);

            long increasing = trends.stream().filter(t -> "INCREASING".equals(t.getTrendDirection())).count();
            long decreasing = trends.stream().filter(t -> "DECREASING".equals(t.getTrendDirection())).count();
            long stable     = trends.stream().filter(t -> "STABLE".equals(t.getTrendDirection())).count();

            // Show increasing trends first (most actionable)
            List<PriceTrend> sorted = trends.stream()
                    .sorted((a, b) -> {
                        int order = trendOrder(b.getTrendDirection()) - trendOrder(a.getTrendDirection());
                        return order != 0 ? order : Double.compare(
                                b.getPctChange3m() != null ? Math.abs(b.getPctChange3m()) : 0,
                                a.getPctChange3m() != null ? Math.abs(a.getPctChange3m()) : 0);
                    })
                    .limit(30)
                    .toList();

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"PriceTrends\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"summary\": { \"total\": ").append(trends.size())
              .append(", \"increasing\": ").append(increasing)
              .append(", \"decreasing\": ").append(decreasing)
              .append(", \"stable\": ").append(stable).append(" }, ");
            sb.append("\"trends\": [");

            for (int i = 0; i < sorted.size(); i++) {
                PriceTrend t = sorted.get(i);
                String supplierName = t.getSupplier() != null
                        ? t.getSupplier().getName().replace("\"", "'") : "Unknown";
                String productName = t.getProduct() != null
                        ? t.getProduct().getName().replace("\"", "'") : "Unknown";
                sb.append(String.format(
                        "{ \"supplier\": \"%s\", \"product\": \"%s\", " +
                        "\"direction\": \"%s\", \"pctChange3m\": %.1f, " +
                        "\"currentPrice\": %.2f, \"marketAvg\": %.2f, " +
                        "\"volatility\": %.2f }",
                        supplierName, productName,
                        t.getTrendDirection() != null ? t.getTrendDirection() : "STABLE",
                        t.getPctChange3m() != null ? t.getPctChange3m() : 0.0,
                        t.getCurrentUnitPrice() != null ? t.getCurrentUnitPrice() : 0.0,
                        t.getMarketAvgPrice() != null ? t.getMarketAvgPrice() : 0.0,
                        t.getPriceVolatility() != null ? t.getPriceVolatility() : 0.0));
                if (i < sorted.size() - 1) sb.append(", ");
            }
            sb.append("] }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("PriceTrendTool error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve price trends: " + e.getMessage() + "\" }";
        }
    }

    private int trendOrder(String direction) {
        if ("INCREASING".equals(direction)) return 2;
        if ("DECREASING".equals(direction)) return 1;
        return 0;
    }
}
