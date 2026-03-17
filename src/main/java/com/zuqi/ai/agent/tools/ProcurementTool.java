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
         "(DRAFT, SENT, PARTIALLY_RECEIVED, RECEIVED, CANCELLED) and purchase requisition counts. " +
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

            return String.format(
                "{ \"tool\": \"ProcurementSummary\", \"distributorId\": \"%s\", " +
                "\"totalPurchaseOrders\": %d, \"draftPOs\": %d, \"sentPOs\": %d, " +
                "\"partiallyReceivedPOs\": %d, \"receivedPOs\": %d, \"cancelledPOs\": %d, " +
                "\"totalPurchaseRequisitions\": %d }",
                distId, totalPOs, draftPOs, sentPOs, partialPOs, receivedPOs, cancelledPOs, totalPRs
            );
        } catch (Exception e) {
            log.error("ProcurementTool: error for distributorId '{}': {}", distributorId, e.getMessage());
            return "{ \"error\": \"Failed to retrieve procurement summary: " + e.getMessage() + "\" }";
        }
    }

}
