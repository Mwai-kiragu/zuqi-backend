package com.zuqi.ai.event.handler;

import com.zuqi.ai.event.DeliveryCompletedEvent;
import com.zuqi.domain.ai.DeliveryRoute;
import com.zuqi.domain.ai.RouteStatus;
import com.zuqi.repository.DeliveryRouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeliveryRouteEventHandler}.
 *
 * Uses pure Mockito — no Spring context required.
 * All tests run synchronously (the @Async annotation is inactive outside
 * of a Spring container, so handleDeliveryCompleted executes in-thread).
 */
@SuppressWarnings("DataFlowIssue")  // IDE false-positives on Mockito stub args vs @NonNull parameters
@ExtendWith(MockitoExtension.class)
class DeliveryRouteEventHandlerTest {

    @Mock
    private DeliveryRouteRepository deliveryRouteRepository;

    @InjectMocks
    private DeliveryRouteEventHandler handler;

    // Fixed IDs used across tests
    private static final UUID DELIVERY_ID    = UUID.randomUUID();
    private static final UUID ORDER_ID       = UUID.randomUUID();
    private static final UUID MERCHANT_ID    = UUID.randomUUID();
    private static final UUID SALES_REP_ID   = UUID.randomUUID();
    private static final UUID DISTRIBUTOR_ID = UUID.randomUUID();
    private static final UUID ROUTE_ID       = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Lenient stub for save so tests that do call it don't need individual stubs.
        // Tests that assert save is NOT called still work because lenient stubs
        // do not fail on unexpected invocations.
        lenient().when(deliveryRouteRepository.save(any(DeliveryRoute.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Helper builders ────────────────────────────────────────────────────

    /**
     * Builds a minimal DeliveryRoute with a single stop containing the given orderId.
     */
    private DeliveryRoute buildRouteWithStop(UUID orderId, String initialStopStatus) {
        List<Map<String, Object>> stops = new ArrayList<>();
        Map<String, Object> stop = new LinkedHashMap<>();
        stop.put("sequence", 1);
        stop.put("merchantId", MERCHANT_ID.toString());
        stop.put("orderIds", new ArrayList<>(List.of(orderId.toString())));
        stop.put("status", initialStopStatus);
        stops.add(stop);

        return DeliveryRoute.builder()
                .id(ROUTE_ID)
                .routeDate(LocalDate.now())
                .stopSequence(stops)
                .status(RouteStatus.PLANNED)
                .build();
    }

    /**
     * Builds a DeliveryRoute with two stops — each containing a distinct orderId.
     */
    private DeliveryRoute buildRouteWithTwoStops(
            UUID orderId1, String status1,
            UUID orderId2, String status2) {

        List<Map<String, Object>> stops = new ArrayList<>();

        Map<String, Object> stop1 = new LinkedHashMap<>();
        stop1.put("sequence", 1);
        stop1.put("merchantId", MERCHANT_ID.toString());
        stop1.put("orderIds", new ArrayList<>(List.of(orderId1.toString())));
        stop1.put("status", status1);
        stops.add(stop1);

        Map<String, Object> stop2 = new LinkedHashMap<>();
        stop2.put("sequence", 2);
        stop2.put("merchantId", UUID.randomUUID().toString());
        stop2.put("orderIds", new ArrayList<>(List.of(orderId2.toString())));
        stop2.put("status", status2);
        stops.add(stop2);

        return DeliveryRoute.builder()
                .id(ROUTE_ID)
                .routeDate(LocalDate.now())
                .stopSequence(stops)
                .status(RouteStatus.PLANNED)
                .build();
    }

    /**
     * Constructs a {@link DeliveryCompletedEvent} with the given routeId.
     * routeId may be null to exercise the null-guard branch.
     */
    private DeliveryCompletedEvent buildEvent(UUID routeId, UUID orderId, boolean successful) {
        return new DeliveryCompletedEvent(
                DELIVERY_ID,
                orderId,
                MERCHANT_ID,
                SALES_REP_ID,
                DISTRIBUTOR_ID,
                routeId,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now(),
                5,
                successful,
                "test delivery"
        );
    }

    // ── Test: null routeId skips all repository interaction ───────────────

    @Test
    void nullRouteId_skipsProcessing() {
        DeliveryCompletedEvent event = buildEvent(null, ORDER_ID, true);

        handler.handleDeliveryCompleted(event);

        verify(deliveryRouteRepository, never()).findById(any(UUID.class));
        verify(deliveryRouteRepository, never()).save(any(DeliveryRoute.class));
    }

    // ── Test: route not found → save never called ─────────────────────────

    @Test
    void routeNotFound_logsAndReturns() {
        when(deliveryRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.empty());

        DeliveryCompletedEvent event = buildEvent(ROUTE_ID, ORDER_ID, true);
        handler.handleDeliveryCompleted(event);

        verify(deliveryRouteRepository, never()).save(any(DeliveryRoute.class));
    }

    // ── Test: successful delivery marks stop as DELIVERED ─────────────────

    @Test
    void successfulDelivery_marksStopDelivered() {
        DeliveryRoute route = buildRouteWithStop(ORDER_ID, "PENDING");
        when(deliveryRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));

        DeliveryCompletedEvent event = buildEvent(ROUTE_ID, ORDER_ID, true);
        handler.handleDeliveryCompleted(event);

        // Verify stop status was updated to DELIVERED
        String stopStatus = (String) route.getStopSequence().get(0).get("status");
        assertThat(stopStatus).isEqualTo("DELIVERED");

        verify(deliveryRouteRepository).save(route);
    }

    // ── Test: failed delivery marks stop as FAILED ────────────────────────

    @Test
    void failedDelivery_marksStopFailed() {
        DeliveryRoute route = buildRouteWithStop(ORDER_ID, "PENDING");
        when(deliveryRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));

        DeliveryCompletedEvent event = buildEvent(ROUTE_ID, ORDER_ID, false);
        handler.handleDeliveryCompleted(event);

        String stopStatus = (String) route.getStopSequence().get(0).get("status");
        assertThat(stopStatus).isEqualTo("FAILED");

        verify(deliveryRouteRepository).save(route);
    }

    // ── Test: orderId not present in any stop → save never called ─────────

    @Test
    void orderNotInSequence_doesNotSave() {
        UUID unknownOrderId = UUID.randomUUID();
        DeliveryRoute route = buildRouteWithStop(ORDER_ID, "PENDING"); // different orderId
        when(deliveryRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));

        DeliveryCompletedEvent event = buildEvent(ROUTE_ID, unknownOrderId, true);
        handler.handleDeliveryCompleted(event);

        verify(deliveryRouteRepository, never()).save(any(DeliveryRoute.class));
    }

    // ── Test: all stops terminal → route promoted to COMPLETED ────────────

    @Test
    void allStopsDelivered_setsRouteCompleted() {
        // Single stop becomes DELIVERED → all stops are terminal → route = COMPLETED
        DeliveryRoute route = buildRouteWithStop(ORDER_ID, "PENDING");
        when(deliveryRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));

        DeliveryCompletedEvent event = buildEvent(ROUTE_ID, ORDER_ID, true);
        handler.handleDeliveryCompleted(event);

        assertThat(route.getStatus()).isEqualTo(RouteStatus.COMPLETED);
        verify(deliveryRouteRepository).save(route);
    }

    @Test
    void allStopsDeliveredOrFailed_setsRouteCompleted() {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        // Stop 1 is already FAILED, stop 2 is PENDING — delivering stop 2 makes all terminal
        DeliveryRoute route = buildRouteWithTwoStops(orderId1, "FAILED", orderId2, "PENDING");
        when(deliveryRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));

        // Deliver the second stop
        DeliveryCompletedEvent event = buildEvent(ROUTE_ID, orderId2, true);
        handler.handleDeliveryCompleted(event);

        assertThat(route.getStatus()).isEqualTo(RouteStatus.COMPLETED);
        verify(deliveryRouteRepository).save(route);
    }

    // ── Test: only one of two stops delivered → route stays PLANNED ───────

    @Test
    void partiallyDelivered_routeRemainsPlanned() {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();

        // Both stops start as PENDING; only orderId1 is delivered here
        DeliveryRoute route = buildRouteWithTwoStops(orderId1, "PENDING", orderId2, "PENDING");
        when(deliveryRouteRepository.findById(ROUTE_ID)).thenReturn(Optional.of(route));

        DeliveryCompletedEvent event = buildEvent(ROUTE_ID, orderId1, true);
        handler.handleDeliveryCompleted(event);

        // orderId2 stop is still PENDING → not all terminal → status must NOT be COMPLETED
        assertThat(route.getStatus()).isNotEqualTo(RouteStatus.COMPLETED);
        assertThat(route.getStatus()).isEqualTo(RouteStatus.PLANNED);

        // save IS still called because a stop was updated
        verify(deliveryRouteRepository).save(route);
    }
}
