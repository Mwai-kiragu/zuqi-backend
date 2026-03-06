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
@Table(name = "stock_take_items", indexes = {
        @Index(name = "idx_stock_take_items_batch", columnList = "batch_id"),
        @Index(name = "idx_stock_take_items_product", columnList = "product_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTakeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private StockTakeBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "system_quantity", precision = 15, scale = 3)
    private BigDecimal systemQuantity;

    @Column(name = "counted_quantity", precision = 15, scale = 3)
    private BigDecimal countedQuantity;

    @Column(name = "variance", precision = 15, scale = 3)
    private BigDecimal variance;

    @Column(name = "notes")
    private String notes;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void recalculateVariance() {
        if (systemQuantity != null && countedQuantity != null) {
            this.variance = countedQuantity.subtract(systemQuantity);
        }
    }
}
