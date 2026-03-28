package com.zuqi.domain.inventory;

import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.product.Product;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Product batch entity.
 * Tracks individual batches of products in a warehouse, including manufacture
 * and expiry dates for FEFO (First Expired, First Out) inventory management.
 *
 * Table: product_batches
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "product_batches", uniqueConstraints = {
        @UniqueConstraint(name = "uq_product_batch", columnNames = {"warehouse_id", "product_id", "batch_number"})
}, indexes = {
        @Index(name = "idx_product_batch_warehouse_expiry", columnList = "warehouse_id, expiry_date"),
        @Index(name = "idx_product_batch_product_expiry", columnList = "product_id, expiry_date"),
        @Index(name = "idx_product_batch_distributor_status", columnList = "distributor_id, status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatch {

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

    @Column(name = "batch_number", nullable = false, length = 100)
    private String batchNumber;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "initial_quantity", nullable = false)
    private Double initialQuantity;

    @Column(name = "current_quantity", nullable = false)
    private Double currentQuantity;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
