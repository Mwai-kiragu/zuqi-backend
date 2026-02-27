package com.zuqi.ai.synthetic.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * In-memory representation of a synthetic order line item.
 *
 * Mirrors {@link com.zuqi.domain.order.OrderItem} fields needed for
 * demand forecasting feature computation (per-SKU quantities and values).
 *
 * @param orderRef   References {@link SyntheticOrder#syntheticId()}
 * @param skuId      Product/SKU UUID (drawn from real products in the distributor's catalogue)
 * @param quantity   Units ordered
 * @param unitPrice  Price per unit at time of order
 * @param lineTotal  quantity × unitPrice (pre-computed for feature builder efficiency)
 */
public record SyntheticOrderItem(
        UUID orderRef,
        UUID skuId,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {}
