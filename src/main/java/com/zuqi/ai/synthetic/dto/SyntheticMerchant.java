package com.zuqi.ai.synthetic.dto;

import com.zuqi.ai.synthetic.profiles.MerchantArchetype;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * In-memory representation of a synthetic merchant.
 *
 * Mirrors the fields of {@link com.zuqi.domain.merchant.Merchant} that are
 * relevant for feature computation. Never persisted — exists only during a
 * generation run and is consumed by feature builders.
 *
 * @param syntheticId        UUID used for in-memory cross-referencing with orders, payments, etc.
 * @param businessName       Generated business name
 * @param businessCategory   Category: retail, wholesale, or distributor
 * @param county             Kenya county (e.g. Nairobi, Mombasa)
 * @param subCounty          Kenya sub-county for route assignment
 * @param gpsLat             Latitude within the selected sub-county
 * @param gpsLng             Longitude within the selected sub-county
 * @param registrationDate   Date merchant "joined" — controls history window length
 * @param initialCreditLimit Starting credit limit derived from archetype order value × 4
 * @param merchantArchetype  Behavioural archetype driving all downstream generation
 */
public record SyntheticMerchant(
        UUID syntheticId,
        String businessName,
        String businessCategory,
        String county,
        String subCounty,
        double gpsLat,
        double gpsLng,
        LocalDate registrationDate,
        BigDecimal initialCreditLimit,
        MerchantArchetype merchantArchetype
) {}
