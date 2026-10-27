package com.horizonssmp.gov.common.records;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/**
 * An explosion-area permit: a bounded three-dimensional area in one dimension, valid for a stated
 * window, issued by the government before a detonation. Article 5.07 requires the permit for the
 * area beforehand; Article 8.12 puts explosion-area permits in the registrar's public registry.
 *
 * <p>The area is stored as a center and radius plus the box the radius produces. The box is stored
 * rather than recomputed so a later change to the geometry rule cannot silently move an area an
 * official already approved. Overlap between permits is allowed: two works may share a site, and
 * the detonation check asks whether <i>any</i> active permit covers the point.
 *
 * <p>The effective state is computed at read time by {@link #stateAt(long)}. A permit before its
 * {@code validFrom} reads {@link #STATUS_PENDING}; one after {@code validUntil} reads
 * {@link #STATUS_EXPIRED}; revocation is permanent and the record stays.
 *
 * @param id the stable permit id, {@code a-} plus eight hex characters
 * @param dimension the dimension id, for example {@code minecraft:overworld}
 * @param centerX the center block X
 * @param centerY the center block Y
 * @param centerZ the center block Z
 * @param radius the radius in blocks, inclusive
 * @param minX the box minimum X
 * @param minY the box minimum Y
 * @param minZ the box minimum Z
 * @param maxX the box maximum X
 * @param maxY the box maximum Y
 * @param maxZ the box maximum Z
 * @param validFrom epoch milliseconds the permit becomes active
 * @param validUntil epoch milliseconds the permit stops being active
 * @param issuedBy the official who issued it, or {@code null} if unknown
 * @param issuedAt epoch milliseconds the permit was issued
 * @param reason the recorded purpose, required
 * @param status the stored status name
 * @param revokedAt epoch milliseconds of the revocation, {@code 0} when never
 * @param revokedBy the official who revoked it, or {@code null}
 * @param revokeReason the recorded revocation reason, or empty
 */
public record AreaPermitRecord(
        String id,
        String dimension,
        int centerX,
        int centerY,
        int centerZ,
        int radius,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        long validFrom,
        long validUntil,
        UUID issuedBy,
        long issuedAt,
        String reason,
        String status,
        long revokedAt,
        UUID revokedBy,
        String revokeReason) {

    /** The permit is inside its window. */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** The permit is not revoked, but its window has not started. */
    public static final String STATUS_PENDING = "PENDING";
    /** The permit is not revoked, but its window has passed. */
    public static final String STATUS_EXPIRED = "EXPIRED";
    /** The permit was cancelled by the government. */
    public static final String STATUS_REVOKED = "REVOKED";

    /** Prefix of an area permit id. */
    public static final String ID_PREFIX = "a-";

    public AreaPermitRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("AreaPermitRecord requires an id");
        }
        dimension = dimension == null ? "" : dimension;
        reason = reason == null ? "" : reason;
        status = status == null || status.isBlank() ? STATUS_ACTIVE : status;
        revokeReason = revokeReason == null ? "" : revokeReason;
    }

    /** Builds a permit whose box is the cube around the center, clamped to the world height. */
    public static AreaPermitRecord create(
            final String id, final String dimension,
            final int x, final int y, final int z, final int radius,
            final long validFrom, final long validUntil,
            final UUID issuedBy, final long now, final String reason) {
        final int r = Math.max(0, radius);
        return new AreaPermitRecord(
                id, dimension, x, y, z, r,
                x - r, Math.max(-2048, y - r), z - r,
                x + r, Math.min(2048, y + r), z + r,
                validFrom, validUntil, issuedBy, now, reason, STATUS_ACTIVE, 0L, null, "");
    }

    /** The effective status at {@code now}. */
    public String stateAt(final long now) {
        if (STATUS_REVOKED.equals(status)) {
            return STATUS_REVOKED;
        }
        if (validFrom > now) {
            return STATUS_PENDING;
        }
        if (validUntil > 0L && now > validUntil) {
            return STATUS_EXPIRED;
        }
        return STATUS_ACTIVE;
    }

    /** True when the permit is active at {@code now}, inside its window and not revoked. */
    public boolean activeAt(final long now) {
        return STATUS_ACTIVE.equals(stateAt(now));
    }

    /** True when this permit covers the point in the same dimension. */
    public boolean contains(final String rawDimension, final double x, final double y, final double z) {
        return dimension.equalsIgnoreCase(rawDimension == null ? "" : rawDimension.trim())
                && x >= minX && x < maxX + 1.0d
                && y >= minY && y < maxY + 1.0d
                && z >= minZ && z < maxZ + 1.0d;
    }

    /** True when this permit covers the point and is active at {@code now}. */
    public boolean covers(final String rawDimension, final double x, final double y, final double z,
            final long now) {
        return activeAt(now) && contains(rawDimension, x, y, z);
    }

    /** Returns a copy revoked now, with the official and the reason recorded. */
    public AreaPermitRecord revoked(final UUID by, final String why, final long now) {
        return new AreaPermitRecord(
                id, dimension, centerX, centerY, centerZ, radius,
                minX, minY, minZ, maxX, maxY, maxZ,
                validFrom, validUntil, issuedBy, issuedAt, reason,
                STATUS_REVOKED, now, by, why == null ? "" : why);
    }

    /** The center as a display string. */
    public String centerText() {
        return centerX + " " + centerY + " " + centerZ;
    }

    /** The box as a display string. */
    public String boxText() {
        return minX + ".." + maxX + " / " + minY + ".." + maxY + " / " + minZ + ".." + maxZ;
    }

    /** Serializes this permit into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("dimension", dimension);
        tag.putInt("centerX", centerX);
        tag.putInt("centerY", centerY);
        tag.putInt("centerZ", centerZ);
        tag.putInt("radius", radius);
        tag.putInt("minX", minX);
        tag.putInt("minY", minY);
        tag.putInt("minZ", minZ);
        tag.putInt("maxX", maxX);
        tag.putInt("maxY", maxY);
        tag.putInt("maxZ", maxZ);
        tag.putLong("validFrom", validFrom);
        tag.putLong("validUntil", validUntil);
        NbtSupport.putUuid(tag, "issuedBy", issuedBy);
        tag.putLong("issuedAt", issuedAt);
        tag.putString("reason", reason);
        tag.putString("status", status);
        tag.putLong("revokedAt", revokedAt);
        NbtSupport.putUuid(tag, "revokedBy", revokedBy);
        tag.putString("revokeReason", revokeReason);
        return tag;
    }

    /**
     * Reads a permit. A tag without an id or a dimension has no useful geometry and returns
     * {@code null}; the loader skips it and logs one warning.
     */
    public static AreaPermitRecord read(final CompoundTag tag, final String fallbackId) {
        String id = NbtSupport.getString(tag, "id", "");
        if (id.isBlank()) {
            id = fallbackId == null ? "" : fallbackId;
        }
        final String dimension = NbtSupport.getString(tag, "dimension", "");
        if (id.isBlank() || dimension.isBlank()) {
            return null;
        }
        return new AreaPermitRecord(
                id,
                dimension,
                NbtSupport.getInt(tag, "centerX", 0),
                NbtSupport.getInt(tag, "centerY", 64),
                NbtSupport.getInt(tag, "centerZ", 0),
                Math.max(0, NbtSupport.getInt(tag, "radius", 0)),
                NbtSupport.getInt(tag, "minX", 0),
                NbtSupport.getInt(tag, "minY", 0),
                NbtSupport.getInt(tag, "minZ", 0),
                NbtSupport.getInt(tag, "maxX", 0),
                NbtSupport.getInt(tag, "maxY", 0),
                NbtSupport.getInt(tag, "maxZ", 0),
                NbtSupport.getLong(tag, "validFrom", 0L),
                NbtSupport.getLong(tag, "validUntil", 0L),
                NbtSupport.getUuid(tag, "issuedBy"),
                NbtSupport.getLong(tag, "issuedAt", 0L),
                NbtSupport.getString(tag, "reason", ""),
                NbtSupport.getString(tag, "status", STATUS_ACTIVE),
                NbtSupport.getLong(tag, "revokedAt", 0L),
                NbtSupport.getUuid(tag, "revokedBy"),
                NbtSupport.getString(tag, "revokeReason", ""));
    }
}
