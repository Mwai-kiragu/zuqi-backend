package com.zuqi.ai.synthetic.generators;

import java.util.List;
import java.util.Random;

/**
 * Kenya county and sub-county geography catalogue for synthetic merchant generation.
 *
 * Counties are weighted by approximate merchant density rather than total population,
 * reflecting where distributor merchants actually operate: Nairobi and large urban
 * centres dominate, with secondary weight given to regional hubs.
 *
 * GPS bounding boxes are approximate squares (~5–10 km) anchored on real
 * sub-county administrative centres, all within Kenya's geographic extent:
 * latitude [-4.7, 4.6], longitude [33.9, 41.9].
 *
 * Weights across all counties sum to 1.0.
 */
public final class KenyaGeography {

    private KenyaGeography() {}

    // -------------------------------------------------------------------------
    // Data model
    // -------------------------------------------------------------------------

    /** A sub-county with an approximate GPS bounding box. */
    public record SubCounty(String name,
                            double minLat, double maxLat,
                            double minLng, double maxLng) {}

    /** A county with a merchant-density weight and its list of sub-counties. */
    public record County(String name, double weight, List<SubCounty> subCounties) {}

    // -------------------------------------------------------------------------
    // County catalogue — weights sum to 1.0
    // -------------------------------------------------------------------------

    public static final List<County> COUNTIES = List.of(

        new County("Nairobi", 0.30, List.of(
            new SubCounty("Westlands",  -1.280, -1.250,  36.790, 36.830),
            new SubCounty("Embakasi",   -1.330, -1.270,  36.850, 36.920),
            new SubCounty("Langata",    -1.380, -1.300,  36.730, 36.820),
            new SubCounty("Starehe",    -1.295, -1.270,  36.820, 36.855),
            new SubCounty("Kasarani",   -1.230, -1.190,  36.870, 36.920)
        )),

        new County("Mombasa", 0.12, List.of(
            new SubCounty("Nyali",      -4.030, -4.000,  39.680, 39.730),
            new SubCounty("Likoni",     -4.100, -4.060,  39.660, 39.700),
            new SubCounty("Kisauni",    -4.010, -3.980,  39.680, 39.720),
            new SubCounty("Mvita",      -4.055, -4.035,  39.660, 39.690)
        )),

        new County("Nakuru", 0.10, List.of(
            new SubCounty("Nakuru Town East", -0.310, -0.270, 36.060, 36.100),
            new SubCounty("Nakuru Town West", -0.320, -0.280, 36.000, 36.070),
            new SubCounty("Naivasha",          -0.730, -0.680, 36.420, 36.480)
        )),

        new County("Kiambu", 0.08, List.of(
            new SubCounty("Kikuyu",      -1.250, -1.200,  36.650, 36.730),
            new SubCounty("Thika Town",  -1.040, -1.000,  37.080, 37.120),
            new SubCounty("Ruiru",       -1.165, -1.130,  36.960, 37.005)
        )),

        new County("Kisumu", 0.08, List.of(
            new SubCounty("Kisumu East",    -0.120, -0.070, 34.740, 34.800),
            new SubCounty("Kisumu West",    -0.090, -0.040, 34.700, 34.760),
            new SubCounty("Kisumu Central", -0.105, -0.075, 34.745, 34.775)
        )),

        new County("Uasin Gishu", 0.07, List.of(
            new SubCounty("Kapseret", 0.480, 0.530, 35.270, 35.350),
            new SubCounty("Soy",      0.550, 0.610, 35.150, 35.250),
            new SubCounty("Ainabkoi", 0.470, 0.520, 35.200, 35.280)
        )),

        new County("Kakamega", 0.06, List.of(
            new SubCounty("Kakamega Central", 0.270, 0.320, 34.740, 34.790),
            new SubCounty("Lugari",           0.410, 0.460, 34.950, 35.020)
        )),

        new County("Machakos", 0.05, List.of(
            new SubCounty("Machakos Town", -1.550, -1.500, 37.260, 37.300),
            new SubCounty("Athi River",    -1.480, -1.430, 37.000, 37.060)
        )),

        new County("Meru", 0.04, List.of(
            new SubCounty("Imenti North", 0.080, 0.130, 37.650, 37.720),
            new SubCounty("Imenti South", 0.000, 0.070, 37.610, 37.680)
        )),

        new County("Nyeri", 0.04, List.of(
            new SubCounty("Nyeri Town", -0.440, -0.400, 36.940, 36.990),
            new SubCounty("Kieni",      -0.350, -0.270, 37.000, 37.150)
        )),

        new County("Kilifi", 0.03, List.of(
            new SubCounty("Kilifi North", -3.640, -3.590, 39.840, 39.900),
            new SubCounty("Malindi",      -3.230, -3.180, 40.100, 40.150)
        )),

        new County("Kajiado", 0.03, List.of(
            new SubCounty("Kajiado Central", -1.850, -1.800, 36.770, 36.830),
            new SubCounty("Ngong",           -1.370, -1.320, 36.640, 36.700)
        ))
    );

    // -------------------------------------------------------------------------
    // Sampling helpers
    // -------------------------------------------------------------------------

    /**
     * Sample a county using weighted random selection proportional to {@code weight}.
     * The last county is returned if floating-point drift causes the cumulative sum
     * to fall short of the rolled value.
     */
    public static County sampleCounty(Random rng) {
        double roll = rng.nextDouble();
        double cumulative = 0.0;
        for (County county : COUNTIES) {
            cumulative += county.weight();
            if (roll < cumulative) return county;
        }
        return COUNTIES.get(COUNTIES.size() - 1);
    }

    /**
     * Sample a sub-county uniformly at random from within the given county.
     */
    public static SubCounty sampleSubCounty(County county, Random rng) {
        List<SubCounty> subs = county.subCounties();
        return subs.get(rng.nextInt(subs.size()));
    }

    /**
     * Sample a GPS latitude uniformly within the sub-county's bounding box.
     */
    public static double sampleLat(SubCounty sc, Random rng) {
        return sc.minLat() + rng.nextDouble() * (sc.maxLat() - sc.minLat());
    }

    /**
     * Sample a GPS longitude uniformly within the sub-county's bounding box.
     */
    public static double sampleLng(SubCounty sc, Random rng) {
        return sc.minLng() + rng.nextDouble() * (sc.maxLng() - sc.minLng());
    }
}
