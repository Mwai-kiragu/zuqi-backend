package com.zuqi.ai.agent.tools;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.order.Order;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.OrderRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MerchantMetricsTool {

    private final CustomerRepository customerRepository;
    private final OrderRepository     orderRepository;

    @Tool("Get merchant metrics for a distributor. Returns totalMerchants, activeFlaggedMerchants, " +
          "inactiveMerchants, newMerchantsLast30Days, merchantsWithOrdersLast30Days, " +
          "up to 5 inactive merchant names, and up to 5 dormant merchant names " +
          "(active merchants with no orders in last 30 days). " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getMerchantMetrics(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getMerchantMetrics distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            List<Customer> allMerchants = customerRepository.findByDistributorId(distId);
            long totalMerchants         = allMerchants.size();
            long activeFlaggedMerchants = allMerchants.stream().filter(Customer::isActive).count();
            long inactiveMerchants      = totalMerchants - activeFlaggedMerchants;

            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            long newMerchantsLast30Days = customerRepository.countNewCustomersFromDate(distId, thirtyDaysAgo);

            List<Order> recentOrders = orderRepository.findByDistributorIdAndDateRange(
                    distId, thirtyDaysAgo, LocalDateTime.now());
            Set<UUID> activeMerchantIds = recentOrders.stream()
                    .filter(o -> o.getMerchant() != null)
                    .map(o -> o.getMerchant().getId())
                    .collect(Collectors.toSet());
            long merchantsWithOrdersLast30Days = activeMerchantIds.size();

            // Names of inactive merchants (up to 5)
            List<String> inactiveNames = allMerchants.stream()
                    .filter(c -> !c.isActive())
                    .limit(5)
                    .map(Customer::getBusinessName)
                    .collect(Collectors.toList());

            // Active merchants with no orders in last 30 days (up to 5)
            List<String> dormantNames = allMerchants.stream()
                    .filter(c -> c.isActive() && !activeMerchantIds.contains(c.getId()))
                    .limit(5)
                    .map(Customer::getBusinessName)
                    .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                    "{ \"tool\": \"MerchantMetrics\", \"distributorId\": \"%s\", " +
                    "\"totalMerchants\": %d, \"activeFlaggedMerchants\": %d, " +
                    "\"inactiveMerchants\": %d, \"newMerchantsLast30Days\": %d, " +
                    "\"merchantsWithOrdersLast30Days\": %d, ",
                    distId, totalMerchants, activeFlaggedMerchants,
                    inactiveMerchants, newMerchantsLast30Days, merchantsWithOrdersLast30Days));

            sb.append("\"inactiveMerchantNames\": [");
            for (int i = 0; i < inactiveNames.size(); i++) {
                sb.append("\"").append(inactiveNames.get(i).replace("\"", "'")).append("\"");
                if (i < inactiveNames.size() - 1) sb.append(", ");
            }
            sb.append("], ");

            sb.append("\"dormantMerchantNames\": [");
            for (int i = 0; i < dormantNames.size(); i++) {
                sb.append("\"").append(dormantNames.get(i).replace("\"", "'")).append("\"");
                if (i < dormantNames.size() - 1) sb.append(", ");
            }
            sb.append("] }");

            return sb.toString();

        } catch (IllegalArgumentException e) {
            log.error("MerchantMetricsTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("MerchantMetricsTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve merchant metrics: " + e.getMessage() + "\" }";
        }
    }
}
