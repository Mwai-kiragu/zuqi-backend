package com.zuqi.ai.agent.tools;

import com.zuqi.domain.inventory.Stock;
import com.zuqi.repository.StockRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryHealthTool {

    private final StockRepository stockRepository;

    @Tool("Get inventory health summary for a distributor. Returns overall health status, " +
          "aggregate counts (belowReorderLevel, outOfStock), and the actual product names with " +
          "quantities for out-of-stock items and the top 10 low-stock items. " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getInventoryHealth(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getInventoryHealth distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            long belowReorderLevel = stockRepository.countLowStockByDistributorId(distId);
            long outOfStock        = stockRepository.countOutOfStockByDistributorId(distId);

            String healthStatus;
            if (outOfStock == 0 && belowReorderLevel == 0) {
                healthStatus = "HEALTHY";
            } else if (outOfStock > 0 && belowReorderLevel > 5) {
                healthStatus = "CRITICAL";
            } else if (outOfStock > 0 || belowReorderLevel > 2) {
                healthStatus = "WARNING";
            } else {
                healthStatus = "FAIR";
            }

            // Out-of-stock items with product names
            List<Stock> outOfStockItems = stockRepository.findOutOfStockByDistributorId(distId);

            // Low-stock items (below reorder level, not zero) — top 10
            List<Stock> lowStockItems = stockRepository
                    .findLowStockByDistributorId(distId, PageRequest.of(0, 10))
                    .getContent();

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"InventoryHealth\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"healthStatus\": \"").append(healthStatus).append("\", ");
            sb.append("\"belowReorderLevel\": ").append(belowReorderLevel).append(", ");
            sb.append("\"outOfStock\": ").append(outOfStock).append(", ");

            // Out-of-stock product details
            sb.append("\"outOfStockItems\": [");
            for (int i = 0; i < outOfStockItems.size(); i++) {
                Stock s = outOfStockItems.get(i);
                String productName = s.getProduct() != null ? s.getProduct().getName().replace("\"", "'") : "Unknown";
                String warehouseName = s.getWarehouse() != null ? s.getWarehouse().getName().replace("\"", "'") : "Unknown";
                sb.append(String.format("{ \"product\": \"%s\", \"warehouse\": \"%s\", \"quantity\": %s }",
                        productName, warehouseName, s.getQuantity().toPlainString()));
                if (i < outOfStockItems.size() - 1) sb.append(", ");
            }
            sb.append("], ");

            // Low-stock product details
            sb.append("\"lowStockItems\": [");
            for (int i = 0; i < lowStockItems.size(); i++) {
                Stock s = lowStockItems.get(i);
                String productName = s.getProduct() != null ? s.getProduct().getName().replace("\"", "'") : "Unknown";
                String warehouseName = s.getWarehouse() != null ? s.getWarehouse().getName().replace("\"", "'") : "Unknown";
                String reorderLevel = s.getReorderLevel() != null ? s.getReorderLevel().toPlainString() : "null";
                sb.append(String.format(
                        "{ \"product\": \"%s\", \"warehouse\": \"%s\", \"quantity\": %s, \"reorderLevel\": %s }",
                        productName, warehouseName,
                        s.getQuantity().toPlainString(), reorderLevel));
                if (i < lowStockItems.size() - 1) sb.append(", ");
            }
            sb.append("] }");

            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("InventoryHealthTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("InventoryHealthTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve inventory health: " + e.getMessage() + "\" }";
        }
    }
}
