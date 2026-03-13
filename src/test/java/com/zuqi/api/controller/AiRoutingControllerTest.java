package com.zuqi.api.controller;

import com.zuqi.ai.routing.RouteSolver;
import com.zuqi.domain.ai.DeliveryRoute;
import com.zuqi.repository.DeliveryRouteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiRoutingController}.
 *
 * Covers: optimize (200/500/null-date default), reoptimize (200/400/500),
 * getRoutesByDate (200/empty), getRouteById (200/404).
 */
@ExtendWith(MockitoExtension.class)
class AiRoutingControllerTest {

    @Mock private RouteSolver             routeSolver;
    @Mock private DeliveryRouteRepository deliveryRouteRepository;

    @InjectMocks
    private AiRoutingController controller;

    // ── POST /optimize ────────────────────────────────────────────────────

    @Test
    void optimize_whenRoutesCreated_returns200() {
        UUID distributorId = UUID.randomUUID();
        LocalDate routeDate = LocalDate.now().plusDays(1);
        DeliveryRoute route = buildRoute();

        when(routeSolver.optimize(distributorId, routeDate)).thenReturn(List.of(route));

        ResponseEntity<?> response = controller.optimize(distributorId, routeDate);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void optimize_whenNoOrders_returns200WithZeroRoutes() {
        when(routeSolver.optimize(any(), any())).thenReturn(List.of());

        ResponseEntity<?> response = controller.optimize(UUID.randomUUID(), LocalDate.now().plusDays(1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void optimize_withNullDate_defaultsTomorrowAndReturns200() {
        UUID distributorId = UUID.randomUUID();
        when(routeSolver.optimize(eq(distributorId), any(LocalDate.class))).thenReturn(List.of());

        ResponseEntity<?> response = controller.optimize(distributorId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Verify the solver was called with a date (tomorrow default)
        verify(routeSolver).optimize(eq(distributorId), any(LocalDate.class));
    }

    @Test
    void optimize_whenServiceThrows_returns500() {
        when(routeSolver.optimize(any(), any()))
                .thenThrow(new RuntimeException("Solver failure"));

        ResponseEntity<?> response = controller.optimize(UUID.randomUUID(), LocalDate.now().plusDays(1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── POST /reoptimize ──────────────────────────────────────────────────

    @Test
    void reoptimize_returns200WithUpdatedRoute() {
        UUID routeId = UUID.randomUUID();
        when(routeSolver.reoptimize(routeId)).thenReturn(buildRoute());

        ResponseEntity<?> response = controller.reoptimize(routeId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void reoptimize_whenRouteNotFound_returns400() {
        when(routeSolver.reoptimize(any()))
                .thenThrow(new IllegalArgumentException("Route not found"));

        ResponseEntity<?> response = controller.reoptimize(UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reoptimize_whenServiceThrows_returns500() {
        when(routeSolver.reoptimize(any()))
                .thenThrow(new RuntimeException("Unexpected failure"));

        ResponseEntity<?> response = controller.reoptimize(UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── GET /routes/{date} ────────────────────────────────────────────────

    @Test
    void getRoutesByDate_returns200WithList() {
        UUID distributorId = UUID.randomUUID();
        LocalDate date = LocalDate.now();
        when(deliveryRouteRepository.findActiveRoutesForDate(distributorId, date))
                .thenReturn(List.of(buildRoute()));

        ResponseEntity<?> response = controller.getRoutesByDate(date, distributorId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getRoutesByDate_whenNoRoutes_returnsEmpty200() {
        when(deliveryRouteRepository.findActiveRoutesForDate(any(), any()))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.getRoutesByDate(LocalDate.now(), UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── GET /routes/{id} ─────────────────────────────────────────────────

    @Test
    void getRouteById_whenFound_returns200() {
        UUID routeId = UUID.randomUUID();
        DeliveryRoute route = buildRoute();
        route.setId(routeId);
        when(deliveryRouteRepository.findById(routeId)).thenReturn(Optional.of(route));

        ResponseEntity<?> response = controller.getRouteById(routeId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getRouteById_whenNotFound_returns404() {
        when(deliveryRouteRepository.findById(any())).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getRouteById(UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private DeliveryRoute buildRoute() {
        DeliveryRoute route = new DeliveryRoute();
        route.setId(UUID.randomUUID());
        route.setStopSequence(List.of());
        route.setTotalDistanceKm(12.5);
        return route;
    }
}
