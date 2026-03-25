package com.zuqi.ai.procurement;

import java.util.UUID;

/**
 * Feature record for supplier risk scoring.
 * Computed from PurchaseOrder history per (supplier, distributor) pair.
 */
public record SupplierFeatures(
        UUID supplierId,
        UUID distributorId,
        double deliveryOnTimePct,       // % of POs received on or before expectedDeliveryDate
        double avgDeliveryDelayDays,    // avg(receivedAt - expectedDeliveryDate) for late POs
        double qualityRejectionPct,     // placeholder: 0.0 until GRN rejection data exists
        double priceConsistencyCv,      // coefficient of variation of PO unit prices
        double avgResponseTimeDays,     // avg(sentAt - createdAt) in days
        int totalPurchaseOrders,        // total POs for this supplier
        double totalValueKes,           // sum of PO totalAmount
        int tenureMonths                // months since first PO
) {}
