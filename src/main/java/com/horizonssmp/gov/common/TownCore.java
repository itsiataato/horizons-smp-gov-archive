package com.horizonssmp.gov.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One configured town-core region: a dimension and an inclusive box.
 *
 * <p>The mod does not know where a town starts. Article 5.14 makes unlicensed carry in a town core
 * a felony, so the server owner defines the cores explicitly in
 * {@code gov.weapon.townCores}; the default is an empty list and the rule is inert until a region
 * is configured. No default coordinate is invented, exactly as with the reception.
 *
 * <p>Config syntax, one entry per region:
 * {@code dimension:minX,minY,minZ:maxX,maxY,maxZ}, for example
 * {@code minecraft:overworld:-200,0,-200:200,320,200}. A malformed entry is ignored with a warning
 * at read time, never treated as "the whole world".
 */
public record TownCore(
        String dimension,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ) {

    /** Most regions one server may configure. Keeps a hand-edited list bounded. */
    public static final int MAX_REGIONS = 64;

    public TownCore {
        dimension = dimension == null ? "" : dimension.trim().toLowerCase(Locale.ROOT);
    }

    /** True when the point is inside this region. */
    public boolean contains(final String rawDimension, final double x, final double y, final double z) {
        return dimension.equalsIgnoreCase(rawDimension == null ? "" : rawDimension.trim())
                && x >= minX && x < maxX + 1.0d
                && y >= minY && y < maxY + 1.0d
                && z >= minZ && z < maxZ + 1.0d;
    }

    /**
     * Parses one configured region. Returns empty for anything malformed; a region is never
     * guessed from a partial string.
     */
    public static Optional<TownCore> parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String text = raw.trim();
        final int first = text.indexOf(':');
        if (first <= 0) {
            return Optional.empty();
        }
        final int second = text.indexOf(':', first + 1);
        if (second <= first + 1) {
            return Optional.empty();
        }
        final int third = text.indexOf(':', second + 1);
        if (third <= second + 1) {
            return Optional.empty();
        }
        final String dimension = text.substring(0, second).trim().toLowerCase(Locale.ROOT);
        if (!dimension.matches("^[a-z0-9_.-]+:[a-z0-9_./-]+$")) {
            return Optional.empty();
        }
        final String[] min = text.substring(second + 1, third).split(",");
        final String[] max = text.substring(third + 1).split(",");
        if (min.length != 3 || max.length != 3) {
            return Optional.empty();
        }
        try {
            final int ax = Integer.parseInt(min[0].trim());
            final int ay = Integer.parseInt(min[1].trim());
            final int az = Integer.parseInt(min[2].trim());
            final int bx = Integer.parseInt(max[0].trim());
            final int by = Integer.parseInt(max[1].trim());
            final int bz = Integer.parseInt(max[2].trim());
            return Optional.of(new TownCore(
                    dimension,
                    Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz),
                    Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz)));
        } catch (final NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Parses every valid entry, dropping malformed ones. */
    public static List<TownCore> parseAll(final List<String> raw) {
        final List<TownCore> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (final String entry : raw) {
            if (result.size() >= MAX_REGIONS) {
                break;
            }
            parse(entry).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    /** True when any configured region covers the point. */
    public static boolean anyContains(
            final List<TownCore> cores, final String dimension,
            final double x, final double y, final double z) {
        if (cores == null) {
            return false;
        }
        for (final TownCore core : cores) {
            if (core != null && core.contains(dimension, x, y, z)) {
                return true;
            }
        }
        return false;
    }
}
