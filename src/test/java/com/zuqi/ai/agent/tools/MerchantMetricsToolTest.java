package com.zuqi.ai.agent.tools;

import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.order.Order;
import com.zuqi.repository.MerchantRepository;
import com.zuqi.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MerchantMetricsTool}.
 */
@ExtendWith(MockitoExtension.class)
class MerchantMetricsToolTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private OrderRepository     orderRepository;

    @InjectMocks
    private MerchantMetricsTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @Test
    void getMerchantMetrics_returnsTotalAndActiveCounts() {
        UUID distributorId = UUID.randomUUID();

        Merchant active   = mockMerchant(true);
        Merchant inactive = mockMerchant(false);

        when(merchantRepository.findByDistributorId(distributorId))
                .thenReturn(List.of(active, inactive));
        when(merchantRepository.countNewMerchantsFromDate(eq(distributorId), any()))
                .thenReturn(1L);

        // One recent order linked to the active merchant
        Order recentOrder = mock(Order.class);
        when(recentOrder.getMerchant()).thenReturn(active);
        when(active.getId()).thenReturn(UUID.randomUUID());

        when(orderRepository.findByDistributorIdAndDateRange(eq(distributorId), any(), any()))
                .thenReturn(List.of(recentOrder));

        String result = tool.getMerchantMetrics(distributorId.toString());

        assertThat(result).contains("\"tool\": \"MerchantMetrics\"");
        assertThat(result).contains("\"totalMerchants\": 2");
        assertThat(result).contains("\"activeFlaggedMerchants\": 1");
        assertThat(result).contains("\"inactiveMerchants\": 1");
        assertThat(result).contains("\"newMerchantsLast30Days\": 1");
    }

    @Test
    void getMerchantMetrics_withNoMerchants_returnsZeros() {
        UUID distributorId = UUID.randomUUID();

        when(merchantRepository.findByDistributorId(distributorId)).thenReturn(List.of());
        when(merchantRepository.countNewMerchantsFromDate(any(), any())).thenReturn(0L);
        when(orderRepository.findByDistributorIdAndDateRange(any(), any(), any())).thenReturn(List.of());

        String result = tool.getMerchantMetrics(distributorId.toString());

        assertThat(result).contains("\"totalMerchants\": 0");
        assertThat(result).doesNotContain("\"error\"");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void getMerchantMetrics_whenInvalidUuid_returnsErrorJson() {
        String result = tool.getMerchantMetrics("invalid");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(merchantRepository, orderRepository);
    }

    @Test
    void getMerchantMetrics_whenRepositoryThrows_returnsErrorJson() {
        when(merchantRepository.findByDistributorId(any()))
                .thenThrow(new RuntimeException("Query timeout"));

        String result = tool.getMerchantMetrics(UUID.randomUUID().toString());

        assertThat(result).contains("\"error\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Merchant mockMerchant(boolean active) {
        Merchant m = mock(Merchant.class);
        when(m.isActive()).thenReturn(active);
        return m;
    }
}
