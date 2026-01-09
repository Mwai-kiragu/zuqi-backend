package com.zuqi.domain.product;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_categories", indexes = {
        @Index(name = "idx_product_categories_distributor", columnList = "distributor_id"),
        @Index(name = "idx_product_categories_active", columnList = "active")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ProductCategory parent;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id")
    private Distributor distributor;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "deactivation_reason", length = 500)
    private String deactivationReason;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deactivated_by")
    private User deactivatedBy;
}
