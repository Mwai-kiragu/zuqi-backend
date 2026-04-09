package com.zuqi.ai.agent.tools;

import com.zuqi.domain.credit.CreditLimit;
import com.zuqi.domain.credit.CreditLimitStatus;
import com.zuqi.repository.CreditLimitRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditLimitTool {

    private final CreditLimitRepository creditLimitRepository;

    @Tool("Get credit limit summary for a distributor's merchants. Returns count of merchants with active " +
          "credit limits, total credit exposure (sum of approved limits in KES), average credit limit, " +
          "and the 5 merchants with the highest credit limits. " +
          "Limits are ML-predicted by the credit_limit_adjuster model and require human review for decreases.")
    @Transactional(readOnly = true)
    public String getCreditLimitSummary(
            @P("The distributor UUID") String distributorId,
            @P("Optional: filter to 'ACTIVE', 'SUSPENDED', or 'PENDING_REVIEW'. Leave empty for ACTIVE only.") String statusFilter) {

        log.info("[TOOL CALLED] getCreditLimitSummary distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            CreditLimitStatus status = CreditLimitStatus.ACTIVE;
            if (statusFilter != null && !statusFilter.isBlank()) {
                try {
                    status = CreditLimitStatus.valueOf(statusFilter.trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    // fall back to ACTIVE
                }
            }

            Page<CreditLimit> page = creditLimitRepository.findByDistributorIdAndStatus(
                    distId, status, PageRequest.of(0, 500));
            List<CreditLimit> limits = page.getContent();

            long count = limits.size();
            double totalExposureKes = limits.stream()
                    .mapToDouble(l -> l.getApprovedLimit() != null
                            ? l.getApprovedLimit().doubleValue() : 0.0)
                    .sum();
            double avgLimitKes = count > 0 ? totalExposureKes / count : 0.0;

            List<CreditLimit> top5 = limits.stream()
                    .filter(l -> l.getApprovedLimit() != null)
                    .sorted((a, b) -> b.getApprovedLimit().compareTo(a.getApprovedLimit()))
                    .limit(5)
                    .toList();

            StringBuilder sb = new StringBuilder();
            sb.append("{ \"tool\": \"CreditLimitSummary\", \"distributorId\": \"").append(distId).append("\", ");
            sb.append("\"status\": \"").append(status).append("\", ");
            sb.append("\"merchantsWithCreditLimit\": ").append(count).append(", ");
            sb.append(String.format("\"totalCreditExposureKes\": %.0f, ", totalExposureKes));
            sb.append(String.format("\"avgCreditLimitKes\": %.0f, ", avgLimitKes));
            sb.append("\"top5ByLimit\": [");

            for (int i = 0; i < top5.size(); i++) {
                CreditLimit cl = top5.get(i);
                String merchantId = cl.getMerchant() != null ? cl.getMerchant().getId().toString() : "unknown";
                String merchantName = cl.getMerchant() != null
                        ? (cl.getMerchant().getBusinessName() != null
                                ? cl.getMerchant().getBusinessName().replace("\"", "'")
                                : merchantId)
                        : merchantId;
                sb.append(String.format("{ \"merchant\": \"%s\", \"approvedLimitKes\": %.0f }",
                        merchantName,
                        cl.getApprovedLimit() != null ? cl.getApprovedLimit().doubleValue() : 0.0));
                if (i < top5.size() - 1) sb.append(", ");
            }

            sb.append("] }");
            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("CreditLimitTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("CreditLimitTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve credit limits: " + e.getMessage() + "\" }";
        }
    }
}
