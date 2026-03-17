package com.zuqi.ai.agent.tools;

import com.zuqi.domain.branch.DistributorBranch;
import com.zuqi.domain.pos.PosSaleStatus;
import com.zuqi.repository.DistributorBranchRepository;
import com.zuqi.repository.PosSaleRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PosSalesTool {

    private final DistributorBranchRepository branchRepository;
    private final PosSaleRepository           posSaleRepository;

    @Tool("Get point-of-sale (POS) summary for a distributor. Returns total POS sales count, " +
         "completed sales count, total revenue KES for last 30 days across all branches. " +
         "Use for questions about POS, point of sale, retail sales, walk-in sales, till sales.")
    @Transactional(readOnly = true)
    public String getPosSummary(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getPosSummary distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            List<DistributorBranch> branches = branchRepository.findByDistributorId(distId);
            if (branches.isEmpty()) {
                return "{ \"tool\": \"PosSummary\", \"distributorId\": \"" + distId + "\", " +
                       "\"branches\": 0, \"totalSales\": 0, \"completedSales\": 0, \"revenueKES\": \"0\" }";
            }

            LocalDateTime from = LocalDateTime.now().minusDays(30);
            LocalDateTime to   = LocalDateTime.now();

            long completedSales = 0;
            BigDecimal revenue  = BigDecimal.ZERO;

            for (DistributorBranch branch : branches) {
                completedSales += posSaleRepository.countByBranchAndStatusAndDateRange(
                        branch.getId(), PosSaleStatus.COMPLETED, from, to);
                revenue = revenue.add(
                        posSaleRepository.sumTotalByBranchAndStatusAndDateRange(
                                branch.getId(), PosSaleStatus.COMPLETED, from, to));
            }

            return String.format(
                "{ \"tool\": \"PosSummary\", \"distributorId\": \"%s\", " +
                "\"branches\": %d, \"totalCompletedSales\": %d, \"last30DaysRevenueKES\": \"%s\" }",
                distId, branches.size(), completedSales, revenue.toPlainString()
            );
        } catch (Exception e) {
            log.error("PosSalesTool: error for distributorId '{}': {}", distributorId, e.getMessage());
            return "{ \"error\": \"Failed to retrieve POS summary: " + e.getMessage() + "\" }";
        }
    }
}
