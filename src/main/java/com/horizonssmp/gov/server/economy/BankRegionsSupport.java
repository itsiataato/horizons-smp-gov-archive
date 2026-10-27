package com.horizonssmp.gov.server.economy;

import com.horizonssmp.gov.config.GovConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsing and default-region helpers for {@link BankRegions}.
 *
 * <p>Package-private implementation detail. {@link BankRegions} keeps the public API and the
 * snapshot lifecycle; this class only holds the pure parsing helpers that have no instance state.
 */
final class BankRegionsSupport {

    private BankRegionsSupport() {
    }

    /** Synthesises a bank region around the government reception point. */
    static String defaultBankRegion() {
        final String dimension = GovConfig.receptionDimension();
        final int radius = Math.max(1, (int) Math.ceil(GovConfig.receptionRadius()));
        final int x = GovConfig.receptionX();
        final int y = GovConfig.receptionY();
        final int z = GovConfig.receptionZ();
        return dimension + ":" + (x - radius) + "," + (y - radius) + "," + (z - radius)
                + ":" + (x + radius) + "," + (y + radius) + "," + (z + radius);
    }

    /**
     * Parses one list, collecting every error and retaining the first {@code max} entries.
     *
     * <p>Over-max entries are still parsed and validated: the operator needs to see every typo,
     * not only the first sixteen. They are simply not used for matching, and the over-max error
     * already makes the configuration a startup failure.
     */
    static List<BankRegion> parseList(
            final String label,
            final List<String> raw,
            final int max,
            final List<String> errors) {
        final List<BankRegion> parsed = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > max) {
            errors.add(label + " has " + raw.size() + " entries; the maximum is " + max + ".");
        }
        final Map<String, Integer> seen = new LinkedHashMap<>();
        for (int index = 0; index < raw.size(); index++) {
            final String entry = raw.get(index);
            final String where = label + " entry " + (index + 1);
            if (entry == null || entry.isBlank()) {
                errors.add(where + " is blank.");
                continue;
            }
            final Integer first = seen.putIfAbsent(entry, index);
            if (first != null) {
                errors.add(where + " is byte-identical to entry " + (first + 1) + ".");
            }
            final BankRegion region;
            try {
                region = BankRegion.parse(entry);
            } catch (final IllegalArgumentException e) {
                errors.add(where + " ('" + entry.trim() + "'): " + e.getMessage());
                continue;
            }
            if (index < max) {
                parsed.add(region);
            }
        }
        return List.copyOf(parsed);
    }
}