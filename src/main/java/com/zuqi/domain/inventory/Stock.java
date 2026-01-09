package com.zuqi.domain.inventory;

import com.zuqi.domain.product.Product;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock", indexes = {
        @Index(name = "idx_stock_warehouse", columnList = "warehouse_id"),
        @Index(name = "idx_stock_product", columnList = "product_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"warehouse_id", "product_id"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "reserved_quantity", nullable = false, precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "reorder_level", precision = 15, scale = 3)
    private BigDecimal reorderLevel;

    @Column(name = "last_stock_check")
    private LocalDateTime lastStockCheck;

    @Version
    private Long version;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public BigDecimal getAvailableQuantity() {
        return quantity.subtract(reservedQuantity);
    }

    public boolean isLowStock() {
        if (reorderLevel == null) {
            return false;
        }
        return quantity.compareTo(reorderLevel) <= 0;
    }
}
