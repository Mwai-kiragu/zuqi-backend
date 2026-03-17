package com.zuqi.ai.agent.tools;

import com.zuqi.domain.inventory.StockTransfer;
import com.zuqi.domain.inventory.StockTransferStatus;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.repository.StockTransferRepository;
import com.zuqi.repository.WarehouseRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockTransferTool {

    private final WarehouseRepository     warehouseRepository;
    private final StockTransferRepository stockTransferRepository;

    @Tool("Get stock transfer summary for a distributor. Returns counts by status " +
         "(PENDING, APPROVED, IN_TRANSIT, RECEIVED, CANCELLED) across all warehouses. " +
         "Use for questions about stock transfers, inter-warehouse movements, transfer requests.")
    @Transactional(readOnly = true)
    public String getStockTransferSummary(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getStockTransferSummary distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            List<Warehouse> warehouses = warehouseRepository.findByDistributorIdAndActiveTrue(distId);
            if (warehouses.isEmpty()) {
                return "{ \"tool\": \"StockTransferSummary\", \"distributorId\": \"" + distId + "\", " +
                       "\"warehouses\": 0, \"totalTransfers\": 0 }";
            }

            Set<UUID> warehouseIds = warehouses.stream().map(Warehouse::getId).collect(Collectors.toSet());

            // Collect all transfers involving any of this distributor's warehouses
            Set<UUID> seen = new HashSet<>();
            List<StockTransfer> transfers = warehouses.stream()
                    .flatMap(w -> stockTransferRepository
                            .findBySourceWarehouseIdOrDestinationWarehouseId(
                                    w.getId(), w.getId(), PageRequest.of(0, 200))
                            .getContent().stream())
                    .filter(t -> seen.add(t.getId()))
                    .collect(Collectors.toList());

            long total     = transfers.size();
            long pending   = transfers.stream().filter(t -> StockTransferStatus.PENDING   == t.getStatus()).count();
            long approved  = transfers.stream().filter(t -> StockTransferStatus.APPROVED  == t.getStatus()).count();
            long inTransit = transfers.stream().filter(t -> StockTransferStatus.IN_TRANSIT== t.getStatus()).count();
            long received  = transfers.stream().filter(t -> StockTransferStatus.RECEIVED  == t.getStatus()).count();
            long cancelled = transfers.stream().filter(t -> StockTransferStatus.CANCELLED == t.getStatus()).count();

            return String.format(
                "{ \"tool\": \"StockTransferSummary\", \"distributorId\": \"%s\", " +
                "\"warehouses\": %d, \"totalTransfers\": %d, " +
                "\"pending\": %d, \"approved\": %d, \"inTransit\": %d, \"received\": %d, \"cancelled\": %d }",
                distId, warehouses.size(), total, pending, approved, inTransit, received, cancelled
            );
        } catch (Exception e) {
            log.error("StockTransferTool: error for distributorId '{}': {}", distributorId, e.getMessage());
            return "{ \"error\": \"Failed to retrieve stock transfer summary: " + e.getMessage() + "\" }";
        }
    }
}
