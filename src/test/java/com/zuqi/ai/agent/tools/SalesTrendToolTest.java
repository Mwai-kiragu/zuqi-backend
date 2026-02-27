package com.zuqi.ai.agent.tools;

import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SalesTrendTool}.
 *
 * Covers: happy path with orders, period-days parsing (valid, invalid, negative,
 * null), invalid UUID, and repository exception.
 */
@ExtendWith(MockitoExtension.class)
class SalesTrendToolTest {

    @Mock private OrderRepository orderRepository;

    @InjectMocks
    private SalesTrendTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @Test
    void getSalesTrend_returnsJsonWithOrderCounts() {
        UUID distributorId = UUID.randomUUID();

        Order delivered = mockOrder(OrderStatus.DELIVERED, BigDecimal.valueOf(5_000));
        Order pending   = mockOrder(OrderStatus.PENDING,   BigDecimal.valueOf(2_000));

        when(orderRepository.findByDistributorIdAndDateRange(eq(distributorId), any(), any()))
                .thenReturn(List.of(delivered, pending));

        String result = tool.getSalesTrend(distributorId.toString(), "30");

        assertThat(result).contains("\"tool\": \"SalesTrend\"");
        assertThat(result).contains("\"totalOrders\": 2");
        assertThat(result).contains("\"delivered\": 1");
        assertThat(result).contains("\"pending\": 1");
        assertThat(result).contains("\"totalRevenue\": \"7000\"");
    }

    @Test
    void getSalesTrend_withCustomPeriodDays_usesThatPeriod() {
        UUID distributorId = UUID.randomUUID();
        when(orderRepository.findByDistributorIdAndDateRange(any(), any(), any()))
                .thenReturn(List.of());

        String result = tool.getSalesTrend(distributorId.toString(), "7");

        assertThat(result).contains("\"periodDays\": 7");
    }

    @Test
    void getSalesTrend_withNullPeriodDays_defaultsTo30() {
        UUID distributorId = UUID.randomUUID();
        when(orderRepository.findByDistributorIdAndDateRange(any(), any(), any()))
                .thenReturn(List.of());

        String result = tool.getSalesTrend(distributorId.toString(), null);

        assertThat(result).contains("\"periodDays\": 30");
    }

    @Test
    void getSalesTrend_withNegativePeriodDays_defaultsTo30() {
        UUID distributorId = UUID.randomUUID();
        when(orderRepository.findByDistributorIdAndDateRange(any(), any(), any()))
                .thenReturn(List.of());

        String result = tool.getSalesTrend(distributorId.toString(), "-5");

        assertThat(result).contains("\"periodDays\": 30");
    }

    @Test
    void getSalesTrend_withNonNumericPeriodDays_defaultsTo30() {
        UUID distributorId = UUID.randomUUID();
        when(orderRepository.findByDistributorIdAndDateRange(any(), any(), any()))
                .thenReturn(List.of());

        String result = tool.getSalesTrend(distributorId.toString(), "quarterly");

        assertThat(result).contains("\"periodDays\": 30");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void getSalesTrend_whenInvalidUuid_returnsErrorJson() {
        String result = tool.getSalesTrend("not-uuid", "30");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(orderRepository);
    }

    @Test
    void getSalesTrend_whenRepositoryThrows_returnsErrorJson() {
        when(orderRepository.findByDistributorIdAndDateRange(any(), any(), any()))
                .thenThrow(new RuntimeException("Timeout"));

        String result = tool.getSalesTrend(UUID.randomUUID().toString(), "30");

        assertThat(result).contains("\"error\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Order mockOrder(OrderStatus status, BigDecimal amount) {
        Order order = mock(Order.class);
        when(order.getStatus()).thenReturn(status);
        when(order.getTotalAmount()).thenReturn(amount);
        return order;
    }
}
