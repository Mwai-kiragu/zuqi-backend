package com.zuqi.ai.routing;

import ai.timefold.solver.core.api.solver.SolverManager;
import com.zuqi.ai.routing.domain.RoutePlan;
import com.zuqi.domain.ai.DeliveryRoute;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.repository.DeliveryRouteRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RouteSolver}.
 *
 * Covers: empty-orders early-return, distributor-not-found exception,
 * reoptimize not-found exception, and all-delivered no-op path.
 *
 * The Timefold SolverManager is mocked — solver execution is not exercised
 * in these tests (solver integration belongs to a separate integration test).
 */
@ExtendWith(MockitoExtension.class)
class RouteSolverTest {

    @Mock private SolverManager<RoutePlan, UUID> solverManager;
    @Mock private DistributorRepository          distributorRepository;
    @Mock private OrderRepository                orderRepository;
    @Mock private UserRepository                 userRepository;
    @Mock private DeliveryRouteRepository        deliveryRouteRepository;
    @Mock private MeterRegistry                  meterRegistry;

    @InjectMocks
    private RouteSolver routeSolver;

    // ── optimize ──────────────────────────────────────────────────────────

    @Test
    void optimize_whenNoConfirmedOrders_returnsEmptyListWithoutCallingSolver() {
        Distributor distributor = mock(Distributor.class);
        when(distributorRepository.findById(any())).thenReturn(Optional.of(distributor));
        when(orderRepository.findByDistributorIdAndStatus(any(), eq(OrderStatus.CONFIRMED), any()))
                .thenReturn(new PageImpl<>(List.of()));

        List<DeliveryRoute> result = routeSolver.optimize(UUID.randomUUID(), LocalDate.now().plusDays(1));

        assertThat(result).isEmpty();
        verifyNoInteractions(solverManager);
    }

    @Test
    void optimize_whenDistributorNotFound_throwsIllegalArgumentException() {
        when(distributorRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeSolver.optimize(UUID.randomUUID(), LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Distributor not found");

        verifyNoInteractions(solverManager);
    }

    // ── reoptimize ────────────────────────────────────────────────────────

    @Test
    void reoptimize_whenRouteNotFound_throwsIllegalArgumentException() {
        when(deliveryRouteRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeSolver.reoptimize(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Route not found");
    }

    @Test
    void reoptimize_whenAllStopsDelivered_returnsRouteUnchangedWithoutSaving() {
        UUID routeId = UUID.randomUUID();

        DeliveryRoute route = mock(DeliveryRoute.class);
        Distributor distributor = mock(Distributor.class);
        when(route.getDistributor()).thenReturn(distributor);
        when(route.getStopSequence()).thenReturn(
                List.of(Map.of("status", "DELIVERED"), Map.of("status", "DELIVERED"))
        );
        when(deliveryRouteRepository.findById(routeId)).thenReturn(Optional.of(route));

        DeliveryRoute result = routeSolver.reoptimize(routeId);

        assertThat(result).isSameAs(route);
        verify(deliveryRouteRepository, never()).save(any());
    }

    @Test
    void reoptimize_whenSomePendingStops_savesUpdatedRoute() {
        UUID routeId = UUID.randomUUID();

        DeliveryRoute route = mock(DeliveryRoute.class);
        Distributor distributor = mock(Distributor.class);
        when(route.getDistributor()).thenReturn(distributor);
        // One delivered, one still pending
        when(route.getStopSequence()).thenReturn(
                List.of(Map.of("status", "DELIVERED"), Map.of("status", "PENDING"))
        );
        when(deliveryRouteRepository.findById(routeId)).thenReturn(Optional.of(route));
        when(deliveryRouteRepository.save(route)).thenReturn(route);

        DeliveryRoute result = routeSolver.reoptimize(routeId);

        assertThat(result).isSameAs(route);
        verify(deliveryRouteRepository).save(route);
    }
}
