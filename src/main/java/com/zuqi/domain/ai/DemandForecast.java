package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.product.Product;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Demand forecast entity.
 * Stores pre-computed demand predictions for merchant-SKU combinations.
 *
 * Blueprint: plan.md Section 6.2 - Demand Forecasting Module
 * Table: ai_demand_forecasts (V18__create_ai_demand_forecasts.sql)
 */
@Entity
@Table(name = "ai_demand_forecasts", uniqueConstraints = {
        @UniqueConstraint(name = "uq_demand_forecast", columnNames = {"merchant_id", "sku_id", "forecast_date"})
}, indexes = {
        @Index(name = "idx_demand_forecasts_merchant", columnList = "merchant_id, forecast_date"),
        @Index(name = "idx_demand_forecasts_sku", columnList = "sku_id, forecast_date"),
        @Index(name = "idx_demand_forecasts_date", columnList = "forecast_date DESC"),
        @Index(name = "idx_demand_forecasts_distributor", columnList = "distributor_id, forecast_date")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Customer merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private Product sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    @Column(name = "predicted_qty", nullable = false)
    private Double predictedQty;

    @Column(name = "confidence_lower")
    private Double confidenceLower;

    @Column(name = "confidence_upper")
    private Double confidenceUpper;

    @Column(name = "model_version", nullable = false)
    private Integer modelVersion;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
