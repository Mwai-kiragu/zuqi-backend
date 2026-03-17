package com.zuqi.ai.agent.tools;

import com.zuqi.domain.procurement.PoStatus;
import com.zuqi.domain.procurement.PurchaseOrder;
import com.zuqi.repository.PurchaseOrderRepository;
import com.zuqi.repository.PurchaseRequisitionRepository;
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
public class ProcurementTool {

    private final PurchaseOrderRepository       purchaseOrderRepository;
    private final PurchaseRequisitionRepository purchaseRequisitionRepository;

    @Tool("Get procurement summary for a distributor. Returns purchase order counts by status " +
         "(DRAFT, SENT, PARTIALLY_RECEIVED, RECEIVED, CANCELLED), purchase requisition counts, " +
         "and the top 5 open purchase orders (SENT/PARTIALLY_RECEIVED) with supplier name, " +
         "PO number, total amount, and expected delivery date. " +
         "Use for questions about procurement, purchase orders, suppliers, restocking orders.")
    @Transactional(readOnly = true)
    public String getProcurementSummary(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getProcurementSummary distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            // Load all POs for this distributor (typically small dataset per distributor)
            List<PurchaseOrder> pos = purchaseOrderRepository.findByDistributorId(distId, PageRequest.of(0, 500)).getContent();
            long totalPOs     = pos.size();
            long draftPOs     = pos.stream().filter(p -> PoStatus.DRAFT               == p.getStatus()).count();
            long sentPOs      = pos.stream().filter(p -> PoStatus.SENT                == p.getStatus()).count();
            long partialPOs   = pos.stream().filter(p -> PoStatus.PARTIALLY_RECEIVED  == p.getStatus()).count();
            long receivedPOs  = pos.stream().filter(p -> PoStatus.RECEIVED            == p.getStatus()).count();
            long cancelledPOs = pos.stream().filter(p -> PoStatus.CANCELLED           == p.getStatus()).count();

            long totalPRs = purchaseRequisitionRepository.findByDistributorId(distId, PageRequest.of(0, 1)).getTotalElements();

            // Top 5 open POs (SENT or PARTIALLY_RECEIVED) with supplier + amount
            List<PurchaseOrder> openPOs = pos.stream()
                    .filter(p -> PoStatus.SENT == p.getStatus() || PoStatus.PARTIALLY_RECEIVED == p.getStatus())
                    .limit(5)
                    .collect(java.util.stream.Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "{ \"tool\": \"ProcurementSummary\", \"distributorId\": \"%s\", " +
                "\"totalPurchaseOrders\": %d, \"draftPOs\": %d, \"sentPOs\": %d, " +
                "\"partiallyReceivedPOs\": %d, \"receivedPOs\": %d, \"cancelledPOs\": %d, " +
                "\"totalPurchaseRequisitions\": %d, ",
                distId, totalPOs, draftPOs, sentPOs, partialPOs, receivedPOs, cancelledPOs, totalPRs));

            sb.append("\"openPurchaseOrders\": [");
            for (int i = 0; i < openPOs.size(); i++) {
                PurchaseOrder po = openPOs.get(i);
                String supplierName = po.getSupplier() != null ? po.getSupplier().getName().replace("\"", "'") : "Unknown";
                String poNum        = po.getPoNumber() != null ? po.getPoNumber() : "N/A";
                String totalAmount  = po.getTotalAmount() != null ? po.getTotalAmount().toPlainString() : "0";
                String deliveryDate = po.getExpectedDeliveryDate() != null ? po.getExpectedDeliveryDate().toString() : "unknown";
                sb.append(String.format(
                        "{ \"supplier\": \"%s\", \"poNumber\": \"%s\", \"totalAmountKES\": \"%s\", \"expectedDelivery\": \"%s\", \"status\": \"%s\" }",
                        supplierName, poNum, totalAmount, deliveryDate, po.getStatus().name()));
                if (i < openPOs.size() - 1) sb.append(", ");
            }
            sb.append("] }");

            return sb.toString();
        } catch (Exception e) {
            log.error("ProcurementTool: error for distributorId '{}': {}", distributorId, e.getMessage());
            return "{ \"error\": \"Failed to retrieve procurement summary: " + e.getMessage() + "\" }";
        }
    }

}
