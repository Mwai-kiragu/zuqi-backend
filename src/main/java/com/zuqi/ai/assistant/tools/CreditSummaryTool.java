package com.zuqi.ai.assistant.tools;

import com.zuqi.domain.credit.CreditLimitStatus;
import com.zuqi.repository.CreditLimitRepository;
import com.zuqi.repository.CreditScoreRepository;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * LangChain4j tool that retrieves credit risk summary data for a distributor.
 * Used by AssistantAgent to answer credit-related questions and build CREDIT_RISK reports.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreditSummaryTool {

    private final CreditLimitRepository creditLimitRepository;
    private final CreditScoreRepository creditScoreRepository;

    @Tool("Get credit risk summary for a distributor. Returns: totalActiveLimits (count of merchants " +
          "with an active credit limit), totalCreditExposureKes (sum of all approved limits), " +
          "totalUtilizedKes (sum of utilized amounts), merchantsAtRisk (limits with utilization > 80%), " +
          "suspendedLimits (count of SUSPENDED limits). " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getCreditSummary(String distributorId) {
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            long totalActive = creditLimitRepository.countByDistributorIdAndStatus(
                    distId, CreditLimitStatus.ACTIVE);

            long totalSuspended = creditLimitRepository.countByDistributorIdAndStatus(
                    distId, CreditLimitStatus.SUSPENDED);

            // Sum approved limits and utilized amounts for active limits
            var activeLimits = creditLimitRepository
                    .findByDistributorIdAndStatus(distId, CreditLimitStatus.ACTIVE, PageRequest.of(0, 10000))
                    .getContent();

            BigDecimal totalApproved = activeLimits.stream()
                    .filter(l -> l.getApprovedLimit() != null)
                    .map(l -> l.getApprovedLimit())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalUtilized = activeLimits.stream()
                    .filter(l -> l.getUtilizedAmount() != null)
                    .map(l -> l.getUtilizedAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long atRisk = activeLimits.stream()
                    .filter(l -> l.getApprovedLimit() != null
                              && l.getUtilizedAmount() != null
                              && l.getApprovedLimit().compareTo(BigDecimal.ZERO) > 0
                              && l.getUtilizedAmount().divide(l.getApprovedLimit(), 2,
                                    java.math.RoundingMode.HALF_UP)
                                    .compareTo(new BigDecimal("0.80")) > 0)
                    .count();

            return String.format(
                    "{ \"tool\": \"CreditSummary\", \"distributorId\": \"%s\", " +
                    "\"totalActiveLimits\": %d, \"totalSuspendedLimits\": %d, " +
                    "\"totalCreditExposureKes\": \"%s\", \"totalUtilizedKes\": \"%s\", " +
                    "\"merchantsAtRisk\": %d }",
                    distId, totalActive, totalSuspended,
                    totalApproved.toPlainString(), totalUtilized.toPlainString(),
                    atRisk);

        } catch (IllegalArgumentException e) {
            log.error("CreditSummaryTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("CreditSummaryTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve credit summary: " + e.getMessage() + "\" }";
        }
    }
}
