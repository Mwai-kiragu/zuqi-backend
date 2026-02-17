package com.zuqi.ai.demand;

import com.zuqi.ai.feature.DemandFeatures;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Generates synthetic order sequences for demand forecasting model training.
 *
 * Creates realistic merchant-SKU order patterns with:
 * - Seasonality (holidays, paydays, weekends)
 * - Trends (growing/declining/stable merchants)
 * - Variance (realistic fluctuations)
 * - Sparsity (not all merchants order all SKUs every week)
 *
 * Blueprint: plan.md Section 6.2 - Demand Forecasting Module
 */
@Service
@Slf4j
public class SyntheticOrderDataGenerator {

    private final Random random = new Random(42); // Fixed seed for reproducibility

    // Product categories and typical order quantities
    private static final Map<String, QuantityRange> PRODUCT_CATEGORY_RANGES = Map.of(
            "Beverages", new QuantityRange(20, 200, 80),
            "Snacks", new QuantityRange(30, 150, 60),
            "Household", new QuantityRange(10, 100, 40),
            "Personal Care", new QuantityRange(15, 80, 35),
            "Cleaning", new QuantityRange(10, 60, 25),
            "Hardware", new QuantityRange(5, 50, 20),
            "Stationery", new QuantityRange(20, 100, 40),
            "Groceries", new QuantityRange(30, 200, 90),
            "Dairy", new QuantityRange(20, 150, 60),
            "Frozen Foods", new QuantityRange(10, 80, 30)
    );

    // Kenya public holidays (2025-2026)
    private static final Set<LocalDate> KENYA_HOLIDAYS = Set.of(
            LocalDate.of(2025, 1, 1),   // New Year
            LocalDate.of(2025, 4, 18),  // Good Friday
            LocalDate.of(2025, 4, 21),  // Easter Monday
            LocalDate.of(2025, 5, 1),   // Labour Day
            LocalDate.of(2025, 6, 1),   // Madaraka Day
            LocalDate.of(2025, 10, 20), // Mashujaa Day
            LocalDate.of(2025, 12, 12), // Jamhuri Day
            LocalDate.of(2025, 12, 25), // Christmas
            LocalDate.of(2025, 12, 26), // Boxing Day
            LocalDate.of(2026, 1, 1),   // New Year
            LocalDate.of(2026, 4, 3),   // Good Friday
            LocalDate.of(2026, 4, 6)    // Easter Monday
    );

    /**
     * Generate synthetic order sequences for training.
     *
     * @param numMerchants Number of unique merchants
     * @param numProducts Number of unique products
     * @param numWeeks Number of weeks of historical data
     * @return List of order sequences
     */
    public List<SyntheticOrderSequence> generateOrderSequences(
            int numMerchants,
            int numProducts,
            int numWeeks) {

        log.info("Generating synthetic order sequences: {} merchants × {} products × {} weeks",
                numMerchants, numProducts, numWeeks);
        long startTime = System.currentTimeMillis();

        List<SyntheticOrderSequence> sequences = new ArrayList<>();
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusWeeks(numWeeks);

        // Generate merchant archetypes
        List<MerchantArchetype> merchants = generateMerchantArchetypes(numMerchants);

        // Generate product profiles
        List<ProductProfile> products = generateProductProfiles(numProducts);

        // Generate order sequences
        int totalCombinations = 0;
        int activeCombinations = 0;

        for (MerchantArchetype merchant : merchants) {
            for (ProductProfile product : products) {
                totalCombinations++;

                // Not all merchants order all products (sparsity)
                if (!shouldMerchantOrderProduct(merchant, product)) {
                    continue;
                }

                activeCombinations++;

                // Generate weekly order sequence
                List<WeeklyOrder> weeklyOrders = generateWeeklySequence(
                        merchant, product, startDate, endDate);

                sequences.add(SyntheticOrderSequence.builder()
                        .merchantId(merchant.id())
                        .productId(product.id())
                        .merchantCategory(merchant.category())
                        .productCategory(product.category())
                        .merchantSizeTier(merchant.sizeTier())
                        .priceTier(product.priceTier())
                        .weeklyOrders(weeklyOrders)
                        .build());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        double sparsityPct = (1.0 - (double) activeCombinations / totalCombinations) * 100;

        log.info("Generated {} order sequences ({} active / {} total combinations, {:.1f}% sparsity) in {}ms",
                sequences.size(), activeCombinations, totalCombinations, sparsityPct, duration);

        return sequences;
    }

    /**
     * Generate merchant archetypes with different ordering patterns.
     */
    private List<MerchantArchetype> generateMerchantArchetypes(int count) {
        List<MerchantArchetype> merchants = new ArrayList<>();
        List<String> categories = List.of(
                "Hardware Store", "General Store", "Supermarket", "Kiosk",
                "Grocery", "Building Materials", "Pharmacy", "Electronics",
                "Clothing", "Restaurant"
        );

        for (int i = 0; i < count; i++) {
            String category = categories.get(random.nextInt(categories.size()));
            String sizeTier = selectSizeTier();
            String creditStatus = selectCreditStatus();
            String trendType = selectTrendType();
            int tenureDays = 30 + random.nextInt(365 * 3); // 30 days to 3 years

            merchants.add(new MerchantArchetype(
                    UUID.randomUUID(),
                    category,
                    sizeTier,
                    creditStatus,
                    trendType,
                    tenureDays
            ));
        }

        return merchants;
    }

    /**
     * Generate product profiles.
     */
    private List<ProductProfile> generateProductProfiles(int count) {
        List<ProductProfile> products = new ArrayList<>();
        List<String> categories = new ArrayList<>(PRODUCT_CATEGORY_RANGES.keySet());

        for (int i = 0; i < count; i++) {
            String category = categories.get(random.nextInt(categories.size()));
            String priceTier = selectPriceTier();
            int shelfLifeDays = selectShelfLife(category);
            boolean seasonal = random.nextDouble() < 0.2; // 20% seasonal products

            products.add(new ProductProfile(
                    UUID.randomUUID(),
                    category,
                    priceTier,
                    shelfLifeDays,
                    seasonal
            ));
        }

        return products;
    }

    /**
     * Determine if merchant should order this product (sparsity model).
     */
    private boolean shouldMerchantOrderProduct(MerchantArchetype merchant, ProductProfile product) {
        // Supermarkets and General Stores order most categories
        if ("Supermarket".equals(merchant.category()) || "General Store".equals(merchant.category())) {
            return random.nextDouble() < 0.8; // 80% order rate
        }

        // Specialized merchants order specific categories
        Map<String, List<String>> merchantProductMapping = Map.of(
                "Hardware Store", List.of("Hardware", "Household", "Cleaning"),
                "Pharmacy", List.of("Personal Care", "Household", "Beverages"),
                "Kiosk", List.of("Beverages", "Snacks", "Groceries"),
                "Restaurant", List.of("Groceries", "Beverages", "Frozen Foods"),
                "Grocery", List.of("Groceries", "Beverages", "Snacks", "Dairy")
        );

        List<String> allowedCategories = merchantProductMapping.getOrDefault(
                merchant.category(), List.of(product.category()));

        if (allowedCategories.contains(product.category())) {
            return random.nextDouble() < 0.6; // 60% order rate for matching categories
        }

        return random.nextDouble() < 0.1; // 10% order rate for mismatched categories
    }

    /**
     * Generate weekly order sequence with trends and seasonality.
     */
    private List<WeeklyOrder> generateWeeklySequence(
            MerchantArchetype merchant,
            ProductProfile product,
            LocalDate startDate,
            LocalDate endDate) {

        List<WeeklyOrder> weeklyOrders = new ArrayList<>();
        QuantityRange range = PRODUCT_CATEGORY_RANGES.get(product.category());

        // Base quantity (varies by merchant size)
        double baseQty = switch (merchant.sizeTier()) {
            case "LARGE" -> range.typical() * 1.5;
            case "MEDIUM" -> range.typical();
            case "SMALL" -> range.typical() * 0.6;
            default -> range.typical();
        };

        // Trend parameters
        double trendSlope = getTrendSlope(merchant.trendType());
        int weekCount = 0;

        LocalDate currentWeek = startDate;
        while (!currentWeek.isAfter(endDate)) {
            weekCount++;

            // Apply trend
            double trendedQty = baseQty + (trendSlope * weekCount);

            // Apply seasonality
            double seasonalMultiplier = getSeasonalMultiplier(currentWeek, product.seasonal());
            double seasonalQty = trendedQty * seasonalMultiplier;

            // Apply randomness (±20%)
            double variance = 0.8 + (random.nextDouble() * 0.4); // 0.8 to 1.2
            double finalQty = seasonalQty * variance;

            // Round and constrain
            int quantity = (int) Math.round(finalQty);
            quantity = Math.max(range.min(), Math.min(range.max(), quantity));

            // Some weeks have no orders (sparsity within sequence)
            boolean ordered = shouldOrderThisWeek(currentWeek, merchant.sizeTier());

            if (ordered && quantity > 0) {
                weeklyOrders.add(new WeeklyOrder(currentWeek, BigDecimal.valueOf(quantity)));
            } else {
                weeklyOrders.add(new WeeklyOrder(currentWeek, BigDecimal.ZERO));
            }

            currentWeek = currentWeek.plusWeeks(1);
        }

        return weeklyOrders;
    }

    /**
     * Determine if merchant orders this week (introduces sparsity).
     */
    private boolean shouldOrderThisWeek(LocalDate weekStart, String sizeTier) {
        // Large merchants order almost every week
        if ("LARGE".equals(sizeTier)) {
            return random.nextDouble() < 0.95;
        }

        // Medium merchants order 70% of weeks
        if ("MEDIUM".equals(sizeTier)) {
            return random.nextDouble() < 0.70;
        }

        // Small merchants order 50% of weeks
        return random.nextDouble() < 0.50;
    }

    /**
     * Get trend slope based on trend type.
     */
    private double getTrendSlope(String trendType) {
        return switch (trendType) {
            case "INCREASING" -> 0.5 + (random.nextDouble() * 1.0); // +0.5 to +1.5 per week
            case "DECREASING" -> -0.5 - (random.nextDouble() * 0.5); // -0.5 to -1.0 per week
            case "STABLE" -> -0.1 + (random.nextDouble() * 0.2); // -0.1 to +0.1 per week
            default -> 0.0;
        };
    }

    /**
     * Get seasonal multiplier for a given week.
     */
    private double getSeasonalMultiplier(LocalDate weekStart, boolean seasonal) {
        if (!seasonal) {
            return 1.0; // No seasonal effect
        }

        // Holiday weeks (Kenya)
        if (isHolidayWeek(weekStart)) {
            return 1.4; // 40% increase during holidays
        }

        // Payday weeks (28th-5th of month)
        if (isPaydayWeek(weekStart)) {
            return 1.2; // 20% increase during payday weeks
        }

        // Christmas season (Nov-Dec)
        if (weekStart.getMonthValue() >= 11) {
            return 1.3; // 30% increase in festive season
        }

        // Ramadan (varies, approximate)
        if (isRamadanPeriod(weekStart)) {
            return 0.85; // 15% decrease during Ramadan
        }

        // Weekend effect (slight decrease for most products)
        if (weekStart.getDayOfWeek() == DayOfWeek.SATURDAY ||
            weekStart.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return 0.95;
        }

        return 1.0;
    }

    /**
     * Check if week contains a Kenya public holiday.
     */
    private boolean isHolidayWeek(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        return KENYA_HOLIDAYS.stream()
                .anyMatch(holiday -> !holiday.isBefore(weekStart) && !holiday.isAfter(weekEnd));
    }

    /**
     * Check if week contains payday period (28th-5th).
     */
    private boolean isPaydayWeek(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        return IntStream.rangeClosed(0, 6)
                .mapToObj(weekStart::plusDays)
                .anyMatch(date -> {
                    int day = date.getDayOfMonth();
                    return day >= 28 || day <= 5;
                });
    }

    /**
     * Check if date falls during Ramadan (approximate).
     */
    private boolean isRamadanPeriod(LocalDate date) {
        // Ramadan 2025: March 1 - March 30 (approximate)
        // Ramadan 2026: February 18 - March 19 (approximate)
        int year = date.getYear();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        if (year == 2025) {
            return month == 3; // March
        } else if (year == 2026) {
            return (month == 2 && day >= 18) || (month == 3 && day <= 19);
        }

        return false;
    }

    private String selectSizeTier() {
        double rand = random.nextDouble();
        if (rand < 0.5) return "SMALL";   // 50%
        if (rand < 0.8) return "MEDIUM";  // 30%
        return "LARGE";                    // 20%
    }

    private String selectCreditStatus() {
        double rand = random.nextDouble();
        if (rand < 0.6) return "GOOD";      // 60%
        if (rand < 0.9) return "MODERATE";  // 30%
        return "POOR";                       // 10%
    }

    private String selectTrendType() {
        double rand = random.nextDouble();
        if (rand < 0.3) return "INCREASING";  // 30%
        if (rand < 0.6) return "STABLE";      // 30%
        return "DECREASING";                  // 40%
    }

    private String selectPriceTier() {
        double rand = random.nextDouble();
        if (rand < 0.33) return "LOW";
        if (rand < 0.67) return "MEDIUM";
        return "HIGH";
    }

    private int selectShelfLife(String category) {
        return switch (category) {
            case "Dairy" -> 7;
            case "Frozen Foods" -> 30;
            case "Beverages" -> 180;
            case "Snacks" -> 90;
            case "Groceries" -> 365;
            default -> 365;
        };
    }

    /**
     * Merchant archetype for order generation.
     */
    private record MerchantArchetype(
            UUID id,
            String category,
            String sizeTier,
            String creditStatus,
            String trendType,
            int tenureDays
    ) {}

    /**
     * Product profile for order generation.
     */
    private record ProductProfile(
            UUID id,
            String category,
            String priceTier,
            int shelfLifeDays,
            boolean seasonal
    ) {}

    /**
     * Quantity range for a product category.
     */
    private record QuantityRange(int min, int max, int typical) {}

    /**
     * Weekly order record.
     */
    public record WeeklyOrder(LocalDate weekStart, BigDecimal quantity) {}

    /**
     * Synthetic order sequence for a merchant-SKU combination.
     */
    @Builder
    public record SyntheticOrderSequence(
            UUID merchantId,
            UUID productId,
            String merchantCategory,
            String productCategory,
            String merchantSizeTier,
            String priceTier,
            List<WeeklyOrder> weeklyOrders
    ) {}
}
