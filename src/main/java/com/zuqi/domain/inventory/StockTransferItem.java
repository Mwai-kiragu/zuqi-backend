package com.zuqi.domain.inventory;

import com.zuqi.domain.product.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "stock_transfer_items", indexes = {
        @Index(name = "idx_stock_transfer_items_transfer", columnList = "transfer_id"),
        @Index(name = "idx_stock_transfer_items_product", columnList = "product_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private StockTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal requestedQuantity;

    @Column(name = "received_quantity", precision = 15, scale = 3)
    private BigDecimal receivedQuantity;

    @Column(name = "notes")
    private String notes;
}
