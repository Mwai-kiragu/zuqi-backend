package com.zuqi.ai.synthetic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * In-memory representation of a synthetic sales rep visit/activity.
 *
 * Used for sales rep underperformance detection feature computation.
 *
 * @param syntheticId          UUID for cross-referencing
 * @param salesRepId           Sales rep UUID (real user from the distributor's team)
 * @param merchantRef          References {@link SyntheticMerchant#syntheticId()}
 * @param visitDate            Date of the merchant visit
 * @param orderPlaced          TRUE if the visit resulted in an order
 * @param orderValue           Value of order placed; zero if orderPlaced=false
 * @param visitDurationMinutes Approximate visit duration in minutes
 * @param isUnderperforming    TRUE if this activity was generated under an underperforming pattern
 */
public record SyntheticRepActivity(
        UUID syntheticId,
        UUID salesRepId,
        UUID merchantRef,
        LocalDate visitDate,
        boolean orderPlaced,
        BigDecimal orderValue,
        int visitDurationMinutes,
        boolean isUnderperforming
) {}
