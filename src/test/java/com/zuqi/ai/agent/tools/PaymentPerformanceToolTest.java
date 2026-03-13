package com.zuqi.ai.agent.tools;

import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.payment.PaymentStatus;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentPerformanceTool}.
 */
@ExtendWith(MockitoExtension.class)
class PaymentPerformanceToolTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository   orderRepository;

    @InjectMocks
    private PaymentPerformanceTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @Test
    void getPaymentPerformance_returnsJsonWithPaymentCounts() {
        UUID distributorId = UUID.randomUUID();

        Payment completed = mockPayment(PaymentStatus.COMPLETED);
        Payment pending   = mockPayment(PaymentStatus.PENDING);
        Payment failed    = mockPayment(PaymentStatus.FAILED);

        when(paymentRepository.findByDistributorId(eq(distributorId), any()))
                .thenReturn(new PageImpl<>(List.of(completed, pending, failed)));
        when(paymentRepository.countUnreconciledPayments(distributorId)).thenReturn(2L);
        when(orderRepository.findOverdueOrders(any())).thenReturn(List.of());
        when(orderRepository.sumOutstandingAmount(distributorId)).thenReturn(BigDecimal.valueOf(50_000));

        String result = tool.getPaymentPerformance(distributorId.toString());

        assertThat(result).contains("\"tool\": \"PaymentPerformance\"");
        assertThat(result).contains("\"completedPayments\": 1");
        assertThat(result).contains("\"failedPayments\": 1");
        assertThat(result).contains("\"unreconciledPayments\": 2");
        assertThat(result).contains("\"totalOutstandingAmount\": \"50000\"");
    }

    @Test
    void getPaymentPerformance_whenNullOutstandingAmount_defaultsToZero() {
        UUID distributorId = UUID.randomUUID();

        when(paymentRepository.findByDistributorId(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(paymentRepository.countUnreconciledPayments(any())).thenReturn(0L);
        when(orderRepository.findOverdueOrders(any())).thenReturn(List.of());
        when(orderRepository.sumOutstandingAmount(any())).thenReturn(null);  // null from DB

        String result = tool.getPaymentPerformance(distributorId.toString());

        assertThat(result).contains("\"totalOutstandingAmount\": \"0\"");
        assertThat(result).doesNotContain("\"error\"");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void getPaymentPerformance_whenInvalidUuid_returnsErrorJson() {
        String result = tool.getPaymentPerformance("not-a-uuid");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(paymentRepository, orderRepository);
    }

    @Test
    void getPaymentPerformance_whenRepositoryThrows_returnsErrorJson() {
        when(paymentRepository.findByDistributorId(any(), any()))
                .thenThrow(new RuntimeException("DB unavailable"));

        String result = tool.getPaymentPerformance(UUID.randomUUID().toString());

        assertThat(result).contains("\"error\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Payment mockPayment(PaymentStatus status) {
        Payment p = mock(Payment.class);
        when(p.getStatus()).thenReturn(status);
        return p;
    }
}
