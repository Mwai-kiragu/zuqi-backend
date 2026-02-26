package com.zuqi.ai.synthetic.profiles;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonalityPatternsTest {

    // -------------------------------------------------------------------------
    // Month-based multipliers
    // -------------------------------------------------------------------------

    @Test
    void december_shouldHaveHighMultiplier() {
        LocalDate dec15 = LocalDate.of(2024, 12, 15);
        assertThat(SeasonalityPatterns.getMultiplier(dec15, true)).isGreaterThan(1.20);
    }

    @Test
    void april_shouldHaveLowMultiplier_duringLongRains() {
        // April 15 — mid long rains, not payday window
        LocalDate apr15 = LocalDate.of(2024, 4, 15);
        double multiplier = SeasonalityPatterns.getMultiplier(apr15, true);
        assertThat(multiplier).isLessThan(1.0);
    }

    @Test
    void june_shouldHaveAboveBaselineMultiplier() {
        LocalDate jun15 = LocalDate.of(2024, 6, 15);
        assertThat(SeasonalityPatterns.getMultiplier(jun15, true)).isGreaterThan(1.0);
    }

    // -------------------------------------------------------------------------
    // Payday window
    // -------------------------------------------------------------------------

    @Test
    void lastDayOfMonth_shouldBeInPaydayWindow() {
        LocalDate lastDay = LocalDate.of(2024, 5, 31);
        assertThat(SeasonalityPatterns.isPaydayWindow(lastDay)).isTrue();
    }

    @Test
    void firstDayOfMonth_shouldNotBeInPaydayWindow() {
        LocalDate firstDay = LocalDate.of(2024, 5, 1);
        assertThat(SeasonalityPatterns.isPaydayWindow(firstDay)).isFalse();
    }

    @Test
    void paydayWindow_shouldBoostMultiplier() {
        // Jan 28 is in payday window; Jan 10 is not
        LocalDate payday    = LocalDate.of(2024, 1, 28);
        LocalDate nonPayday = LocalDate.of(2024, 1, 10);
        assertThat(SeasonalityPatterns.getMultiplier(payday, true))
                .isGreaterThan(SeasonalityPatterns.getMultiplier(nonPayday, true));
    }

    // -------------------------------------------------------------------------
    // Public holidays
    // -------------------------------------------------------------------------

    @Test
    void newYearsDay_shouldBePublicHoliday() {
        assertThat(SeasonalityPatterns.isFixedPublicHoliday(LocalDate.of(2025, 1, 1))).isTrue();
    }

    @Test
    void madarakaDay_shouldBePublicHoliday() {
        assertThat(SeasonalityPatterns.isFixedPublicHoliday(LocalDate.of(2025, 6, 1))).isTrue();
    }

    @Test
    void jamhuriDay_shouldBePublicHoliday() {
        assertThat(SeasonalityPatterns.isFixedPublicHoliday(LocalDate.of(2025, 12, 12))).isTrue();
    }

    @Test
    void regularDay_shouldNotBePublicHoliday() {
        assertThat(SeasonalityPatterns.isFixedPublicHoliday(LocalDate.of(2025, 3, 15))).isFalse();
    }

    @Test
    void publicHoliday_shouldReduceMultiplier() {
        // Madaraka Day (Jun 1) vs a normal mid-June day — neither is in payday window
        LocalDate madaraka  = LocalDate.of(2024, 6, 1);
        LocalDate normalDay = LocalDate.of(2024, 6, 15);
        assertThat(SeasonalityPatterns.getMultiplier(madaraka, true))
                .isLessThan(SeasonalityPatterns.getMultiplier(normalDay, true));
    }

    // -------------------------------------------------------------------------
    // Ramadan
    // -------------------------------------------------------------------------

    @Test
    void ramadan2024_food_shouldHaveHigherMultiplierThanNonFood() {
        LocalDate ramadanDay = LocalDate.of(2024, 3, 20); // within Ramadan 2024
        double food    = SeasonalityPatterns.getMultiplier(ramadanDay, true);
        double nonFood = SeasonalityPatterns.getMultiplier(ramadanDay, false);
        assertThat(food).isGreaterThan(nonFood);
    }

    @Test
    void ramadan2025_shouldBeDetected() {
        // Ramadan 2025: Mar 1 - Mar 30
        LocalDate inRamadan  = LocalDate.of(2025, 3, 15);
        LocalDate postRamadan = LocalDate.of(2025, 4, 5);
        // Food multiplier during Ramadan should be higher than after
        assertThat(SeasonalityPatterns.getMultiplier(inRamadan, true))
                .isGreaterThan(SeasonalityPatterns.getMultiplier(postRamadan, true));
    }

    // -------------------------------------------------------------------------
    // Multiplier bounds
    // -------------------------------------------------------------------------

    @Test
    void multiplier_shouldAlwaysBePositive() {
        // Scan every day of 2024 — multiplier must always be > 0
        LocalDate date = LocalDate.of(2024, 1, 1);
        LocalDate end  = LocalDate.of(2024, 12, 31);
        while (!date.isAfter(end)) {
            assertThat(SeasonalityPatterns.getMultiplier(date, true)).isPositive();
            assertThat(SeasonalityPatterns.getMultiplier(date, false)).isPositive();
            date = date.plusDays(1);
        }
    }
}
