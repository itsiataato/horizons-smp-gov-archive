package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.records.LocationRecord;

/**
 * Small shared helpers for vehicle display text.
 */
public final class VehicleSupport {

    private VehicleSupport() {
    }

    public static String sourceLabel(final VehicleService.VehicleAdapter adapter, final String plate) {
        final String base = adapter.source() == null || adapter.source().isBlank()
                ? "registry" : adapter.source();
        try {
            return adapter.presence(plate).isPresent() ? base + "+entity" : base;
        } catch (final RuntimeException e) {
            return base;
        }
    }

    public static String locationText(final LocationRecord location) {
        if (location == null || !location.isSet()) {
            return "";
        }
        return location.dimension() + " " + location.x() + " " + location.y() + " " + location.z();
    }

    public static String clip(final String text, final int max) {
        final String value = text == null ? "" : text;
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }
}