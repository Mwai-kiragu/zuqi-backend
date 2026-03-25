package com.zuqi.domain.pricing;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.product.Product;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "promotions", indexes = {
        @Index(name = "idx_promotions_distributor", columnList = "distributor_id"),
        @Index(name = "idx_promotions_product",    columnList = "product_id"),
        @Index(name = "idx_promotions_dates",      columnList = "valid_from, valid_to")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "promotion_type", nullable = false, length = 30)
    private String promotionType; // PERCENTAGE, FIXED_AMOUNT, BUY_X_GET_Y

    @Column(name = "discount_value", precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", precision = 15, scale = 2)
    private BigDecimal minOrderAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "approval_status", length = 30)
    @Builder.Default
    private String approvalStatus = "APPROVED";

    @Column(name = "created_by_id")
    private UUID createdById;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
