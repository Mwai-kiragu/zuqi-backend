package com.zuqi.ai.synthetic.dto;

import com.zuqi.ai.synthetic.profiles.MerchantArchetype;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * In-memory representation of a synthetic order.
 *
 * Mirrors the fields of {@link com.zuqi.domain.order.Order} relevant for
 * demand forecasting, credit scoring, and sales rep performance features.
 *
 * @param syntheticId       UUID for cross-referencing with items and payments
 * @param merchantRef       References {@link SyntheticMerchant#syntheticId()}
 * @param salesRepRef       References a synthetic sales rep UUID
 * @param orderDate         Timestamp of order placement
 * @param totalAmount       Total order value
 * @param status            Order status: PENDING, CONFIRMED, DELIVERED, CANCELLED
 * @param merchantArchetype Archetype of the placing merchant (denormalised for feature builders)
 */
public record SyntheticOrder(
        UUID syntheticId,
        UUID merchantRef,
        UUID salesRepRef,
        LocalDateTime orderDate,
        BigDecimal totalAmount,
        String status,
        MerchantArchetype merchantArchetype
) {}
