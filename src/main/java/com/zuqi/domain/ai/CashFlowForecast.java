package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cash flow forecast entity.
 * Stores AI-generated predictions of distributor cash inflows, outflows and
 * net position for a given forecast date, including confidence bounds.
 *
 * Table: ai_cash_flow_forecasts
 */
@Entity
@Table(name = "ai_cash_flow_forecasts", uniqueConstraints = {
        @UniqueConstraint(name = "uq_cash_flow_forecast", columnNames = {"distributor_id", "forecast_date"})
}, indexes = {
        @Index(name = "idx_cash_flow_forecast_distributor_date", columnList = "distributor_id, forecast_date")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    @Column(name = "predicted_inflow")
    private Double predictedInflow;

    @Column(name = "predicted_outflow")
    private Double predictedOutflow;

    @Column(name = "predicted_net")
    private Double predictedNet;

    @Column(name = "lower_bound_net")
    private Double lowerBoundNet;

    @Column(name = "upper_bound_net")
    private Double upperBoundNet;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "data_phase", length = 20)
    private String dataPhase;

    @Column(name = "model_version")
    private Integer modelVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
