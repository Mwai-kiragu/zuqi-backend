package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.DeliveryRoute;
import com.zuqi.domain.ai.RouteStatus;
import com.zuqi.repository.DeliveryRouteRepository;
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
 * Unit tests for {@link DeliveryMetricsTool}.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryMetricsToolTest {

    @Mock private DeliveryRouteRepository deliveryRouteRepository;

    @InjectMocks
    private DeliveryMetricsTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @Test
    void getDeliveryMetrics_withMixedStatuses_returnsCorrectCounts() {
        UUID distributorId = UUID.randomUUID();

        DeliveryRoute planned   = routeWith(12.5, null, null);
        DeliveryRoute completed = routeWith(15.0, 80.0, 45.0);

        when(deliveryRouteRepository.findByDistributorIdAndStatus(distributorId, RouteStatus.PLANNED))
                .thenReturn(List.of(planned));
        when(deliveryRouteRepository.findByDistributorIdAndStatus(distributorId, RouteStatus.IN_PROGRESS))
                .thenReturn(List.of());
        when(deliveryRouteRepository.findByDistributorIdAndStatus(distributorId, RouteStatus.COMPLETED))
                .thenReturn(List.of(completed));
        when(deliveryRouteRepository.findByDistributorIdAndStatus(distributorId, RouteStatus.CANCELLED))
                .thenReturn(List.of());

        String result = tool.getDeliveryMetrics(distributorId.toString());

        assertThat(result).contains("\"tool\": \"DeliveryMetrics\"");
        assertThat(result).contains("\"totalRoutes\": 2");
        assertThat(result).contains("\"planned\": 1");
        assertThat(result).contains("\"completed\": 1");
        assertThat(result).contains("\"avgDistanceKm\": \"15.00\"");
    }

    @Test
    void getDeliveryMetrics_withNoRoutes_returnsZeroCounts() {
        UUID distributorId = UUID.randomUUID();

        when(deliveryRouteRepository.findByDistributorIdAndStatus(any(), any()))
                .thenReturn(List.of());

        String result = tool.getDeliveryMetrics(distributorId.toString());

        assertThat(result).contains("\"totalRoutes\": 0");
        assertThat(result).contains("\"avgDistanceKm\": \"N/A\"");
        assertThat(result).doesNotContain("\"error\"");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void getDeliveryMetrics_whenInvalidUuid_returnsErrorJson() {
        String result = tool.getDeliveryMetrics("not-a-uuid");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(deliveryRouteRepository);
    }

    @Test
    void getDeliveryMetrics_whenRepositoryThrows_returnsErrorJson() {
        when(deliveryRouteRepository.findByDistributorIdAndStatus(any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        String result = tool.getDeliveryMetrics(UUID.randomUUID().toString());

        assertThat(result).contains("\"error\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private DeliveryRoute routeWith(Double distanceKm, Double loadPct, Double durationMin) {
        DeliveryRoute route = new DeliveryRoute();
        route.setId(UUID.randomUUID());
        route.setTotalDistanceKm(distanceKm);
        route.setLoadUtilizationPct(loadPct);
        route.setTotalDurationMin(durationMin);
        return route;
    }
}
