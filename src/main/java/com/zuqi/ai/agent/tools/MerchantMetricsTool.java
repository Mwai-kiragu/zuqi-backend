package com.zuqi.ai.agent.tools;

import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.order.Order;
import com.zuqi.repository.MerchantRepository;
import com.zuqi.repository.OrderRepository;
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

    private final MerchantRepository merchantRepository;
    private final OrderRepository     orderRepository;

    @Tool("Get merchant metrics for a distributor. Returns totalMerchants, activeMerchants (those with " +
          "at least one order in the last 30 days), inactiveMerchants, newMerchantsLast30Days " +
          "(merchants registered in the last 30 days), and distinctActiveMerchantCount (unique merchants " +
          "who placed at least one order recently). " +
          "Parameter: distributorId (UUID string).")
    @Transactional(readOnly = true)
    public String getMerchantMetrics(String distributorId) {
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            // Total merchants for distributor
            List<Merchant> allMerchants = merchantRepository.findByDistributorId(distId);
            long totalMerchants = allMerchants.size();

            // Active merchants (active flag = true)
            long activeFlaggedMerchants = allMerchants.stream()
                    .filter(Merchant::isActive)
                    .count();
            long inactiveMerchants = totalMerchants - activeFlaggedMerchants;

            // New merchants registered in the last 30 days
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            long newMerchantsLast30Days = merchantRepository.countNewMerchantsFromDate(distId, thirtyDaysAgo);

            // Merchants with at least one order in the last 30 days (distinct merchant IDs)
            List<Order> recentOrders = orderRepository.findByDistributorIdAndDateRange(
                    distId, thirtyDaysAgo, LocalDateTime.now());
            Set<UUID> activeMerchantIds = recentOrders.stream()
                    .filter(o -> o.getMerchant() != null)
                    .map(o -> o.getMerchant().getId())
                    .collect(Collectors.toSet());
            long activeMerchantsLast30Days = activeMerchantIds.size();

            return String.format(
                    "{ \"tool\": \"MerchantMetrics\", \"distributorId\": \"%s\", " +
                    "\"totalMerchants\": %d, \"activeFlaggedMerchants\": %d, " +
                    "\"inactiveMerchants\": %d, \"newMerchantsLast30Days\": %d, " +
                    "\"merchantsWithOrdersLast30Days\": %d }",
                    distId,
                    totalMerchants, activeFlaggedMerchants,
                    inactiveMerchants, newMerchantsLast30Days,
                    activeMerchantsLast30Days
            );

        } catch (IllegalArgumentException e) {
            log.error("MerchantMetricsTool: invalid distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Invalid distributorId format: " + distributorId + "\" }";
        } catch (Exception e) {
            log.error("MerchantMetricsTool: unexpected error for distributorId '{}'", distributorId, e);
            return "{ \"error\": \"Failed to retrieve merchant metrics: " + e.getMessage() + "\" }";
        }
    }
}
