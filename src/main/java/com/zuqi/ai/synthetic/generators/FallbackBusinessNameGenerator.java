package com.zuqi.ai.synthetic.generators;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Template-based business name generator used when Ollama is unavailable or in tests.
 *
 * Names follow patterns common to small Kenyan businesses:
 * {@code "<Prefix> <Business suffix>"}, combining owner surnames, aspirational words,
 * and place-based identifiers with category-appropriate suffixes.
 *
 * The combinatorial pool (prefixes × suffixes) is large enough to avoid meaningful
 * collision rates across a typical 500–2,000 merchant generation run.
 */
@Component
public class FallbackBusinessNameGenerator implements BusinessNameGenerator {

    private static final List<String> PREFIXES = List.of(
            // Kikuyu names
            "Kamau", "Mwangi", "Kimani", "Kariuki", "Waithaka", "Muthoni",
            "Wanjiru", "Njeri", "Gichuki", "Githae", "Mbugua", "Macharia",
            "Njenga", "Maina", "Nyambura",
            // Luo names
            "Otieno", "Odhiambo", "Akinyi", "Onyango", "Adhiambo", "Okello",
            // Luhya names
            "Wangari", "Simiyu", "Wafula", "Namukolo",
            // Kalenjin names
            "Kipchoge", "Chepkoech", "Kipkemoi", "Rotich", "Korir",
            // Swahili / Coastal
            "Hassan", "Fatuma", "Aisha", "Juma", "Salama", "Omar", "Bakari",
            // Aspirational / neutral
            "Prime", "Star", "Summit", "Pioneer", "Quality", "Standard",
            "Elite", "Excel", "Savanna", "Safari", "Mlima", "Jua",
            "Sunrise", "Unity", "Grace", "Victory", "Power", "Royal", "Golden"
    );

    private static final List<String> RETAIL_SUFFIXES = List.of(
            "General Stores", "Minimart", "Superette", "Provisions", "Shop",
            "Stores", "Groceries", "Agrovet", "Hardware", "Pharmacy",
            "Convenience Store", "Goods Store", "Depot", "Duka", "Kiosk"
    );

    private static final List<String> WHOLESALE_SUFFIXES = List.of(
            "Wholesale", "Wholesalers", "Trading Co.", "Traders",
            "Bulk Suppliers", "Commodity Traders", "Supply House",
            "Market Wholesale", "Merchant Co.", "Goods Wholesalers"
    );

    private static final List<String> DISTRIBUTOR_SUFFIXES = List.of(
            "Distributors Ltd.", "Agencies", "Enterprises Ltd.",
            "Distribution Co.", "Supplies Ltd.", "Logistics Ltd.",
            "Network Ltd.", "Trading Ltd.", "Holdings Ltd.", "Group Ltd."
    );

    @Override
    public List<String> generateBatch(String businessCategory, int count, long seed) {
        Random rng = new Random(seed);
        List<String> suffixes = getSuffixes(businessCategory);
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String prefix = PREFIXES.get(rng.nextInt(PREFIXES.size()));
            String suffix = suffixes.get(rng.nextInt(suffixes.size()));
            result.add(prefix + " " + suffix);
        }
        return result;
    }

    private List<String> getSuffixes(String category) {
        return switch (category) {
            case "wholesale"   -> WHOLESALE_SUFFIXES;
            case "distributor" -> DISTRIBUTOR_SUFFIXES;
            default            -> RETAIL_SUFFIXES;
        };
    }
}
