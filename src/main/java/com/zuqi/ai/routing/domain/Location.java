package com.zuqi.ai.routing.domain;

/**
 * Immutable GPS coordinate used by the Timefold planning domain
 * and by DistanceMatrixService.
 */
public record Location(double latitude, double longitude) {

    /**
     * Haversine great-circle distance in kilometres between this location and another.
     * Used as fallback when GraphHopper is not available.
     */
    public double haversineDistanceKm(Location other) {
        final double R = 6371.0; // Earth radius in km
        double dLat = Math.toRadians(other.latitude - this.latitude);
        double dLon = Math.toRadians(other.longitude - this.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(this.latitude))
                * Math.cos(Math.toRadians(other.latitude))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
