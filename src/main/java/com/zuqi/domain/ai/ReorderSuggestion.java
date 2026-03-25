package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_reorder_suggestions", indexes = {
        @Index(name = "idx_ai_reorder_suggestions_distributor_warehouse", columnList = "distributor_id, warehouse_id"),
        @Index(name = "idx_ai_reorder_suggestions_distributor_product", columnList = "distributor_id, product_id"),
        @Index(name = "idx_ai_reorder_suggestions_status", columnList = "distributor_id, status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "suggested_qty", nullable = false)
    private Double suggestedQty;

    @Column(name = "economic_order_qty")
    private Double economicOrderQty;

    @Column(name = "safety_stock")
    private Double safetyStock;

    @Column(name = "reorder_point")
    private Double reorderPoint;

    @Column(name = "current_stock")
    private Double currentStock;

    @Column(name = "days_of_supply_remaining")
    private Double daysOfSupplyRemaining;

    @Column(name = "avg_daily_demand")
    private Double avgDailyDemand;

    @Column(name = "lead_time_days")
    private Double leadTimeDays;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "data_phase", length = 20)
    private String dataPhase;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "model_version")
    private Integer modelVersion;

    @Column(name = "converted_pr_id")
    private UUID convertedPrId;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
