package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_delivery_routes", indexes = {
        @Index(name = "idx_delivery_routes_distributor", columnList = "distributor_id"),
        @Index(name = "idx_delivery_routes_date",        columnList = "route_date"),
        @Index(name = "idx_delivery_routes_driver",      columnList = "driver_id"),
        @Index(name = "idx_delivery_routes_status",      columnList = "status"),
        @Index(name = "idx_delivery_routes_created",     columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @Column(name = "route_date", nullable = false)
    private LocalDate routeDate;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vehicle_info", columnDefinition = "jsonb")
    private Map<String, Object> vehicleInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stop_sequence", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> stopSequence;

    @Column(name = "total_distance_km")
    private Double totalDistanceKm;

    @Column(name = "total_duration_min")
    private Double totalDurationMin;

    @Column(name = "load_utilization_pct")
    private Double loadUtilizationPct;

    @Column(name = "solver_time_ms")
    private Integer solverTimeMs;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RouteStatus status = RouteStatus.PLANNED;

    @Column(name = "actual_distance_km")
    private Double actualDistanceKm;

    @Column(name = "actual_duration_min")
    private Double actualDurationMin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = RouteStatus.PLANNED;
        }
    }
}
