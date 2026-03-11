package com.zuqi.domain.ft;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ft_amount_ranges", indexes = {
        @Index(name = "idx_ft_amount_ranges_dist", columnList = "distributor_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FtAmountRange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "distributor_id", nullable = false)
    private UUID distributorId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "min_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal minAmount = BigDecimal.ZERO;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount; // null = no upper limit

    @Column(name = "required_levels", nullable = false)
    @Builder.Default
    private int requiredLevels = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
