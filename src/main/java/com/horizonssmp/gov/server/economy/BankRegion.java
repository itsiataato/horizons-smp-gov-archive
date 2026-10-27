package com.horizonssmp.gov.server.economy;

import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * One configured bank or town-hall region: a dimension and an inclusive box.
 *
 * <p>Config syntax, one entry per region:
 * {@code dimension:minX,minY,minZ:maxX,maxY,maxZ}, for example
 * {@code minecraft:overworld:-40,60,-40:40,90,40}. The syntax and the normalisation rules are the
 * ones {@code TownCore} uses: the dimension is lower-cased, the bounds are inclusive and each axis
 * is normalised so {@code min <= max}. The dimension must carry its namespace, which is what makes
 * the entry exactly four colon-separated fields.
 *
 * <p>This record is immutable and headless: it holds no level, no player and no cached match. A
 * region is evidence for a startup check and a predicate for a runtime check; it never decides
 * which feedback string to show, so a bank/town-hall overlap is resolved by the caller.
 *
 * <p>A box larger than {@link #MAX_VOLUME} blocks is not a counter. {@link #volume()} rejects such
 * a box rather than returning an astronomical number, and {@link #parse(String)} therefore refuses
 * to build one; the specification makes an over-wide region a startup failure, not "the whole
 * map is a bank".
 *
 * <p>Parsing is strict. Every failure throws {@link IllegalArgumentException} with a stable,
 * operator-facing message; there is no lenient mode and no partially parsed region. The message
 * prefixes are public so the startup check and tests can assert the exact text.
 */
public record BankRegion(
        ResourceLocation dimension,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ) {

    /** The largest region volume a configured bank or town hall may have, in blocks. */
    public static final long MAX_VOLUME = 1_000_000L;

    /** Prefix of the error thrown for a null or blank entry. */
    public static final String ERROR_BLANK = "Bank region entry is blank.";

    /** Prefix of the error thrown when the entry does not have exactly four colon fields. */
    public static final String ERROR_SHAPE =
            "Bank region must be dimension:minX,minY,minZ:maxX,maxY,maxZ with four colon-separated fields.";

    /** Prefix of the error thrown when the dimension has an empty namespace or path. */
    public static final String ERROR_BLANK_DIMENSION = "Bank region dimension is blank.";

    /** Prefix of the error thrown when the dimension is not a resource location. */
    public static final String ERROR_DIMENSION =
            "Bank region dimension is not a valid resource location: ";

    /** Prefix of the error thrown when a corner is not three comma-separated integers. */
    public static final String ERROR_BOUNDS =
            "Bank region bounds must be three comma-separated integers per corner.";

    /** Prefix of the error thrown when one bound token is not an integer. */
    public static final String ERROR_BOUND = "Bank region bound is not an integer: ";

    /** Prefix of the error thrown when the box is larger than {@link #MAX_VOLUME}. */
    public static final String ERROR_VOLUME =
            "Bank region volume exceeds " + MAX_VOLUME + " blocks.";

    /**
     * A dimension id as accepted by {@code ResourceLocation}: a namespace and a path, both
     * lower-case. This is deliberately the same shape {@code TownCore} accepts.
     */
    private static final Pattern DIMENSION =
            Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    /** An integer coordinate, ASCII digits with an optional sign. */
    private static final Pattern INTEGER = Pattern.compile("^[+-]?[0-9]+$");

    public BankRegion {
        if (dimension == null) {
            throw new IllegalArgumentException(ERROR_BLANK_DIMENSION);
        }
        if (minX > maxX) {
            final int swap = minX;
            minX = maxX;
            maxX = swap;
        }
        if (minY > maxY) {
            final int swap = minY;
            minY = maxY;
            maxY = swap;
        }
        if (minZ > maxZ) {
            final int swap = minZ;
            minZ = maxZ;
            maxZ = swap;
        }
    }

    /**
     * True when the block is inside this region and the dimension ids match.
     *
     * <p>The bounds are inclusive. A null dimension or position never matches, so a caller without
     * a level cannot accidentally satisfy a region check.
     */
    public boolean contains(final ResourceLocation dimension, final BlockPos pos) {
        return dimension != null
                && pos != null
                && this.dimension.equals(dimension)
                && pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    /**
     * The number of blocks in the box, computed with {@code long} arithmetic.
     *
     * <p>An extreme box spans more than {@code Long.MAX_VALUE} blocks if multiplied naively. The
     * computation refuses any single axis longer than {@link #MAX_VOLUME} before multiplying and
     * then checks the product, so it can never overflow and never reports a wrapped value.
     *
     * @throws IllegalArgumentException when the volume exceeds {@link #MAX_VOLUME}
     */
    public long volume() {
        final long width = (long) maxX - (long) minX + 1L;
        final long height = (long) maxY - (long) minY + 1L;
        final long depth = (long) maxZ - (long) minZ + 1L;
        if (width > MAX_VOLUME || height > MAX_VOLUME || depth > MAX_VOLUME) {
            // Every axis is at least one block, so one long axis already proves the volume is
            // over the cap; multiplying first could overflow a long.
            throw new IllegalArgumentException(ERROR_VOLUME);
        }
        final long volume = width * height * depth;
        if (volume > MAX_VOLUME) {
            throw new IllegalArgumentException(ERROR_VOLUME);
        }
        return volume;
    }

    /**
     * Parses one configured region.
     *
     * <p>Exactly four colon-separated fields are required:
     * {@code namespace:path:minX,minY,minZ:maxX,maxY,maxZ}. The dimension is trimmed, lower-cased
     * and validated as a resource location; each corner is exactly three trimmed decimal integers;
     * the bounds are normalised per axis and stay inclusive. The resulting volume must be within
     * {@code 1..MAX_VOLUME}.
     *
     * @throws IllegalArgumentException with one of the {@code ERROR_*} texts when the entry is
     *         malformed; a malformed entry is never dropped or guessed at
     */
    public static BankRegion parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(ERROR_BLANK);
        }
        final String text = raw.trim();
        final String[] fields = text.split(":", -1);
        if (fields.length != 4) {
            throw new IllegalArgumentException(ERROR_SHAPE);
        }
        final String namespace = fields[0].trim();
        final String path = fields[1].trim();
        if (namespace.isEmpty() || path.isEmpty()) {
            throw new IllegalArgumentException(ERROR_BLANK_DIMENSION);
        }
        final String dimensionId = (namespace + ":" + path).toLowerCase(Locale.ROOT);
        if (!DIMENSION.matcher(dimensionId).matches()) {
            throw new IllegalArgumentException(ERROR_DIMENSION + dimensionId);
        }
        final ResourceLocation dimension = ResourceLocation.tryParse(dimensionId);
        if (dimension == null) {
            // The pattern above mirrors ResourceLocation, but the id remains the authority.
            throw new IllegalArgumentException(ERROR_DIMENSION + dimensionId);
        }
        final int[] min = bounds(fields[2]);
        final int[] max = bounds(fields[3]);
        final BankRegion region = new BankRegion(
                dimension, min[0], min[1], min[2], max[0], max[1], max[2]);
        region.volume();
        return region;
    }

    private static int[] bounds(final String field) {
        final String[] parts = field.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException(ERROR_BOUNDS);
        }
        final int[] result = new int[3];
        for (int index = 0; index < 3; index++) {
            final String token = parts[index].trim();
            if (!INTEGER.matcher(token).matches()) {
                throw new IllegalArgumentException(ERROR_BOUND + "'" + token + "'");
            }
            try {
                result[index] = Integer.parseInt(token);
            } catch (final NumberFormatException e) {
                // Out of int range. Keep the token in the message so the operator sees the typo.
                throw new IllegalArgumentException(ERROR_BOUND + "'" + token + "'", e);
            }
        }
        return result;
    }
}
