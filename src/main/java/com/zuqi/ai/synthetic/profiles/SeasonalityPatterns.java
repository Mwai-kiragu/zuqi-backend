package com.zuqi.ai.synthetic.profiles;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Set;

/**
 * Kenya-specific seasonality patterns applied to synthetic order volume generation.
 *
 * Use {@link #getMultiplier(LocalDate, boolean)} to obtain the combined demand
 * multiplier for any date. A multiplier of 1.0 means no seasonal effect.
 *
 * Pattern sources:
 * <ul>
 *   <li>Month patterns    — annual recurring peaks/troughs</li>
 *   <li>Payday effect     — last week of each month (+25%)</li>
 *   <li>Public holidays   — Kenya statutory and national holidays</li>
 *   <li>Ramadan           — food +20%, non-food -10% (approximate lunar dates)</li>
 * </ul>
 */
public final class SeasonalityPatterns {

    private SeasonalityPatterns() {}

    // -------------------------------------------------------------------------
    // Kenya public holidays (fixed calendar)
    // -------------------------------------------------------------------------

    private static final Set<MonthDay> FIXED_PUBLIC_HOLIDAYS = Set.of(
            MonthDay.of(1,  1),   // New Year's Day
            MonthDay.of(5,  1),   // Labour Day
            MonthDay.of(6,  1),   // Madaraka Day
            MonthDay.of(10, 10),  // Huduma Day
            MonthDay.of(10, 20),  // Mashujaa Day
            MonthDay.of(12, 12),  // Jamhuri Day
            MonthDay.of(12, 25),  // Christmas Day
            MonthDay.of(12, 26)   // Boxing Day
    );

    /**
     * Returns true if {@code date} is a Kenya fixed public holiday.
     * Variable holidays (Easter, Eid) are not included — handled separately.
     */
    public static boolean isFixedPublicHoliday(LocalDate date) {
        return FIXED_PUBLIC_HOLIDAYS.contains(MonthDay.from(date));
    }

    // -------------------------------------------------------------------------
    // Payday effect — last 7 days of each month
    // -------------------------------------------------------------------------

    /**
     * Returns true if {@code date} falls in the payday window
     * (last 7 days of the month — government/formal sector paydays in Kenya).
     */
    public static boolean isPaydayWindow(LocalDate date) {
        int lastDay = date.lengthOfMonth();
        return date.getDayOfMonth() >= lastDay - 6;
    }

    // -------------------------------------------------------------------------
    // Ramadan (approximate — lunar calendar shifts ~11 days earlier each year)
    // -------------------------------------------------------------------------

    /**
     * Approximate Ramadan start dates for the synthetic data window (2023–2026).
     * Used for the food/non-food demand split during the holy month.
     */
    private static boolean isRamadan(LocalDate date) {
        int year = date.getYear();
        LocalDate start;
        LocalDate end;
        switch (year) {
            case 2023 -> { start = LocalDate.of(2023, 3, 23); end = LocalDate.of(2023, 4, 21); }
            case 2024 -> { start = LocalDate.of(2024, 3, 11); end = LocalDate.of(2024, 4,  9); }
            case 2025 -> { start = LocalDate.of(2025, 3,  1); end = LocalDate.of(2025, 3, 30); }
            case 2026 -> { start = LocalDate.of(2026, 2, 18); end = LocalDate.of(2026, 3, 19); }
            default   -> { return false; }
        }
        return !date.isBefore(start) && !date.isAfter(end);
    }

    // -------------------------------------------------------------------------
    // Month-based seasonal multipliers
    // -------------------------------------------------------------------------

    /**
     * Base monthly multiplier before payday, holiday, or Ramadan adjustments.
     * Derived from Kenya FMCG distribution seasonal patterns.
     */
    private static double monthBaseMultiplier(int month) {
        return switch (month) {
            case 12, 1  -> 1.30;   // Dec–Jan: school opening + festive season
            case 2      -> 1.10;   // Feb: post-festive recovery
            case 3, 4   -> 0.90;   // Mar–Apr: long rains — logistics disruption
            case 5      -> 1.00;   // May: neutral
            case 6, 7   -> 1.15;   // Jun–Jul: mid-year, school term 2 stocking
            case 8      -> 1.05;   // Aug: mild uptick
            case 9      -> 1.00;   // Sep: neutral
            case 10     -> 1.10;   // Oct: pre-festive stocking begins
            case 11     -> 1.20;   // Nov: strong pre-festive build
            default     -> 1.00;
        };
    }

    // -------------------------------------------------------------------------
    // Combined multiplier (primary API)
    // -------------------------------------------------------------------------

    /**
     * Returns the combined demand multiplier for {@code date}.
     *
     * @param date   The calendar date being evaluated
     * @param isFood TRUE for food/FMCG products; FALSE for non-food items.
     *               Affects Ramadan adjustment direction.
     * @return multiplier &gt; 0 — apply to base order volume/value
     */
    public static double getMultiplier(LocalDate date, boolean isFood) {
        double multiplier = monthBaseMultiplier(date.getMonthValue());

        // Payday window: +25% uplift on top of seasonal base
        if (isPaydayWindow(date)) {
            multiplier *= 1.25;
        }

        // Public holiday: demand dip on the holiday itself (-15%)
        if (isFixedPublicHoliday(date)) {
            multiplier *= 0.85;
        }

        // Ramadan: food orders surge, non-food dip
        if (isRamadan(date)) {
            multiplier *= isFood ? 1.20 : 0.90;
        }

        return multiplier;
    }
}
