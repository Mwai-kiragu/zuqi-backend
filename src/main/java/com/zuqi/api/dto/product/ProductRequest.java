package com.zuqi.api.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "SKU is required")
    @Size(max = 100, message = "SKU must not exceed 100 characters")
    private String sku;

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    private String name;

    private String description;

    private Long categoryId;

    private UUID distributorId;

    @Size(max = 50, message = "Unit of measure must not exceed 50 characters")
    private String unitOfMeasure;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Unit price must be greater than 0")
    private BigDecimal unitPrice;

    @DecimalMin(value = "0.0", message = "Cost price must be non-negative")
    private BigDecimal costPrice;

    private String imageUrl;

    @Size(max = 100, message = "Barcode must not exceed 100 characters")
    private String barcode;

    /** Optional GL revenue account override for this product */
    private UUID revenueAccountId;

    /** Optional GL COGS account override for this product */
    private UUID cogsAccountId;

    @Builder.Default
    private boolean allBranches = true;

    @Builder.Default
    private List<ProductBranchPriceRequest> branchPrices = new ArrayList<>();

    /** Optional: warehouse ID for opening stock entry */
    private UUID openingStockWarehouseId;

    /** Optional: opening stock quantity (default 0, creates a stock record to show product in inventory) */
    @DecimalMin(value = "0.0", message = "Opening stock must be non-negative")
    private BigDecimal openingStockQuantity;

    /** Optional floor price — no sale or POS transaction may go below this */
    @DecimalMin(value = "0.0", message = "Floor price must be non-negative")
    private BigDecimal minSalePrice;
}
