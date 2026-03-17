package com.zuqi.ai.assistant.tools;

import com.zuqi.domain.credit.CreditLimit;
import com.zuqi.domain.credit.CreditLimitStatus;
import com.zuqi.repository.CreditLimitRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditSummaryTool {

    private final CreditLimitRepository creditLimitRepository;

    @Tool("Get credit risk summary for a distributor. Returns totalActiveLimits, totalSuspendedLimits, " +
          "totalCreditExposureKes, totalUtilizedKes, merchantsAtRisk (utilization > 80%), " +
          "and the names of up to 5 at-risk merchants with their utilization percentage. " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getCreditSummary(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getCreditSummary distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            long totalActive    = creditLimitRepository.countByDistributorIdAndStatus(distId, CreditLimitStatus.ACTIVE);
            long totalSuspended = creditLimitRepository.countByDistributorIdAndStatus(distId, CreditLimitStatus.SUSPENDED);

            List<CreditLimit> activeLimits = creditLimitRepository
                    .findByDistributorIdAndStatus(distId, CreditLimitStatus.ACTIVE, PageRequest.of(0, 10000))
                    .getContent();

            BigDecimal totalApproved = activeLimits.stream()
                    .filter(l -> l.getApprovedLimit() != null)
                    .map(CreditLimit::getApprovedLimit)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalUtilized = activeLimits.stream()
                    .filter(l -> l.getUtilizedAmount() != null)
                    .map(CreditLimit::getUtilizedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // At-risk: utilization > 80%
            List<CreditLimit> atRiskLimits = activeLimits.stream()
                    .filter(l -> l.getApprovedLimit() != null
                              && l.getUtilizedAmount() != null
                              && l.getApprovedLimit().compareTo(BigDecimal.ZERO) > 0
                              && l.getUtilizedAmount()
                                    .divide(l.getApprovedLimit(), 2, RoundingMode.HALF_UP)
                                    .compareTo(new BigDecimal("0.80")) > 0)
                    .collect(Collectors.toList());

            long atRiskCount = atRiskLimits.size();

            // Top 5 at-risk merchant names with utilization %
            List<CreditLimit> top5AtRisk = atRiskLimits.stream().limit(5).collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                    "{ \"tool\": \"CreditSummary\", \"distributorId\": \"%s\", " +
                    "\"totalActiveLimits\": %d, \"totalSuspendedLimits\": %d, " +
                    "\"totalCreditExposureKes\": \"%s\", \"totalUtilizedKes\": \"%s\", " +
                    "\"merchantsAtRisk\": %d, ",
                    distId, totalActive, totalSuspended,
                    totalApproved.toPlainString(), totalUtilized.toPlainString(),
                    atRiskCount));

            sb.append("\"atRiskMerchants\": [");
            for (int i = 0; i < top5AtRisk.size(); i++) {
                CreditLimit l = top5AtRisk.get(i);
                String name = l.getMerchant() != null ? l.getMerchant().getBusinessName().replace("\"", "'") : "Unknown";
                String utilizationPct = l.getApprovedLimit().compareTo(BigDecimal.ZERO) > 0
                        ? l.getUtilizedAmount().divide(l.getApprovedLimit(), 3, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString()
                        : "0";
                sb.append(String.format("{ \"merchant\": \"%s\", \"utilizationPct\": \"%s%%\", \"approvedKES\": \"%s\", \"utilizedKES\": \"%s\" }",
                        name, utilizationPct,
                        l.getApprovedLimit().toPlainString(),
                        l.getUtilizedAmount().toPlainString()));
                if (i < top5AtRisk.size() - 1) sb.append(", ");
            }
            sb.append("] }");

            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("CreditSummaryTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("CreditSummaryTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve credit summary: " + e.getMessage() + "\" }";
        }
    }
}
