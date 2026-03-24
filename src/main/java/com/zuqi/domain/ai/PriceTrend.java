package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.supplier.Supplier;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Price trend entity.
 * Captures the AI-computed directional trend, slope and recent percentage
 * change for a supplier-product price, enabling procurement timing decisions.
 *
 * Table: ai_price_trends
 */
@Entity
@Table(name = "ai_price_trends", uniqueConstraints = {
        @UniqueConstraint(name = "uq_price_trend",
                columnNames = {"distributor_id", "supplier_id", "product_id"})
}, indexes = {
        @Index(name = "idx_price_trend_distributor_direction", columnList = "distributor_id, trend_direction"),
        @Index(name = "idx_price_trend_supplier_product", columnList = "supplier_id, product_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceTrend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "trend_direction", length = 20)
    private String trendDirection;

    @Column(name = "trend_slope")
    private Double trendSlope;

    @Column(name = "pct_change_3m")
    private Double pctChange3m;

    @Column(name = "current_unit_price")
    private Double currentUnitPrice;

    @Column(name = "market_avg_price")
    private Double marketAvgPrice;

    @Column(name = "price_volatility")
    private Double priceVolatility;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;
}
