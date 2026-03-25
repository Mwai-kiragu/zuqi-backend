package com.zuqi.ai.pricing;

import java.util.UUID;

/**
 * Feature record for smart pricing recommendations.
 * Computed from product pricing history, demand forecasts, and order data.
 */
public record PricingFeatures(
        UUID productId,
        UUID distributorId,
        double currentUnitPrice,          // current sell price (KES)
        double costPrice,                  // product cost price (KES)
        double marginPct,                  // (unitPrice - costPrice) / unitPrice × 100
        double priceChangePct30d,          // % price change over last 30 days
        double demandAtCurrentPrice,       // avg units sold/week at current price
        double demandTrend,                // slope of demand over last 90 days
        double inventoryDaysOfSupply,      // current stock / avg daily demand
        int    productAgeDays,             // days since product was added
        double similarProductAvgPrice,     // avg price of same-category products
        int    productCategoryEncoded,     // label-encoded product category
        int    priceTierEncoded            // 0=budget, 1=mid, 2=premium
) {}
