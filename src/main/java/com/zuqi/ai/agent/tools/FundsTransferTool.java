package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ft.FundsTransfer;
import com.zuqi.domain.ft.FundsTransferStatus;
import com.zuqi.repository.FundsTransferRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FundsTransferTool {

    private final FundsTransferRepository fundsTransferRepository;

    @Tool("Get funds transfer summary for a distributor. Returns counts by status " +
         "(DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, DISBURSED, CANCELLED), total amount KES, " +
         "and the top 5 transfers pending approval with reference number, amount, and description. " +
         "Use for questions about funds transfers, bank transfers, money movement, interbank payments.")
    @Transactional(readOnly = true)
    public String getFundsTransferSummary(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getFundsTransferSummary distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            List<FundsTransfer> transfers = fundsTransferRepository
                    .findByDistributorIdOrderByCreatedAtDesc(distId, PageRequest.of(0, 500))
                    .getContent();

            long total          = transfers.size();
            long draft          = transfers.stream().filter(t -> FundsTransferStatus.DRAFT            == t.getStatus()).count();
            long pendingApproval= transfers.stream().filter(t -> FundsTransferStatus.PENDING_APPROVAL == t.getStatus()).count();
            long approved       = transfers.stream().filter(t -> FundsTransferStatus.APPROVED         == t.getStatus()).count();
            long rejected       = transfers.stream().filter(t -> FundsTransferStatus.REJECTED         == t.getStatus()).count();
            long disbursed      = transfers.stream().filter(t -> FundsTransferStatus.DISBURSED        == t.getStatus()).count();
            long cancelled      = transfers.stream().filter(t -> FundsTransferStatus.CANCELLED        == t.getStatus()).count();

            BigDecimal totalAmount = transfers.stream()
                    .filter(t -> t.getAmount() != null)
                    .map(FundsTransfer::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Top 5 pending approval transfers
            List<FundsTransfer> pendingDetails = transfers.stream()
                    .filter(t -> FundsTransferStatus.PENDING_APPROVAL == t.getStatus())
                    .limit(5)
                    .collect(java.util.stream.Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "{ \"tool\": \"FundsTransferSummary\", \"distributorId\": \"%s\", " +
                "\"totalTransfers\": %d, \"draft\": %d, \"pendingApproval\": %d, " +
                "\"approved\": %d, \"rejected\": %d, \"disbursed\": %d, \"cancelled\": %d, " +
                "\"totalAmountKES\": \"%s\", ",
                distId, total, draft, pendingApproval, approved, rejected, disbursed, cancelled,
                totalAmount.toPlainString()));

            sb.append("\"pendingApprovalDetails\": [");
            for (int i = 0; i < pendingDetails.size(); i++) {
                FundsTransfer t = pendingDetails.get(i);
                String ref   = t.getReferenceNumber() != null ? t.getReferenceNumber() : "N/A";
                String amt   = t.getAmount() != null ? t.getAmount().toPlainString() : "0";
                String desc  = t.getDescription() != null ? t.getDescription().replace("\"", "'") : "";
                sb.append(String.format(
                        "{ \"referenceNumber\": \"%s\", \"amountKES\": \"%s\", \"description\": \"%s\" }",
                        ref, amt, desc));
                if (i < pendingDetails.size() - 1) sb.append(", ");
            }
            sb.append("] }");

            return sb.toString();
        } catch (Exception e) {
            log.error("FundsTransferTool: error for distributorId '{}': {}", distributorId, e.getMessage());
            return "{ \"error\": \"Failed to retrieve funds transfer summary: " + e.getMessage() + "\" }";
        }
    }
}
