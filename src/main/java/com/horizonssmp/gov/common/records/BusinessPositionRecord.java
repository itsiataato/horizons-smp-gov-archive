package com.horizonssmp.gov.common.records;

import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.enums.BusinessPermission;

import net.minecraft.nbt.CompoundTag;

/**
 * One position in a business's position tree.
 *
 * <p>A position is a <b>business-internal</b> office: it carries {@link BusinessPermission}s for the
 * employee who holds it and nothing else. It grants no {@link com.horizonssmp.gov.common.GovCapability},
 * so a manager of a shop cannot use a business title to act for the state. The tree is stored as a
 * parent pointer rather than nested lists so moving a subtree is one edit and a cycle can be
 * detected by walking the parent chain.
 *
 * <p>The root is the CEO position, id {@link #CEO_POSITION_ID}. It is the owner's fixed seat:
 * {@link #system()} is true, its parent is absent, and it may not be moved or deleted. Storing the
 * full permission set on it even though the CEO has every permission implicitly keeps a client or
 * an export honest without a special case.
 *
 * <p>Reads are tolerant: a row without an id, with a blank or over-long title, a negative order or
 * a wage outside the registry bound is dropped rather than failing the whole business. A dropped
 * position can be recreated by the owner; a failed world load would lose the business.
 *
 * @param id the stable position id, unique within one business
 * @param title the displayed title, 1 to {@link #MAX_TITLE_LENGTH} characters
 * @param parentId the parent position id, or {@code null} for the CEO root
 * @param permissions the business permissions this position carries, never {@code null}
 * @param wagePerDay the wage in gold ingots per real 24 hours, {@code 0} for unpaid
 * @param order the display order among siblings, {@code 0} or greater
 * @param system true only for the fixed CEO root
 * @param createdAt epoch milliseconds the position was created, {@code 0} when unknown
 * @param createdBy the player or official who created it, or {@code null} when unknown
 */
public record BusinessPositionRecord(
        String id,
        String title,
        String parentId,
        Set<BusinessPermission> permissions,
        long wagePerDay,
        int order,
        boolean system,
        long createdAt,
        UUID createdBy) {

    /** The fixed id of the owner's root position. Never generated and never reused. */
    public static final String CEO_POSITION_ID = "ceo";

    /** The title bound, chosen so a position name fits one dashboard row without wrapping. */
    public static final int MAX_TITLE_LENGTH = 48;

    /** The default CEO title. A business may rename most positions, but not this one. */
    public static final String CEO_TITLE = "CEO";

    public BusinessPositionRecord {
        id = id == null ? "" : id.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("BusinessPositionRecord requires an id");
        }
        title = title == null ? "" : title.trim();
        if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "BusinessPositionRecord title must be 1.." + MAX_TITLE_LENGTH + " characters");
        }
        parentId = parentId == null ? null : parentId.trim();
        if (parentId != null && parentId.isEmpty()) {
            parentId = null;
        }
        if (id.equals(parentId)) {
            // A position cannot be its own parent. This is the smallest possible cycle and would
            // make every tree walk loop; refusing it at construction keeps the stored shape a tree.
            throw new IllegalArgumentException("BusinessPositionRecord cannot parent itself");
        }
        permissions = BusinessPermission.copyOf(permissions);
        if (wagePerDay < 0L || wagePerDay > BusinessKeys.MAX_WAGE_PER_DAY) {
            throw new IllegalArgumentException(
                    "BusinessPositionRecord wage must be 0.." + BusinessKeys.MAX_WAGE_PER_DAY);
        }
        if (order < 0) {
            throw new IllegalArgumentException("BusinessPositionRecord order must be >= 0");
        }
        createdAt = Math.max(0L, createdAt);
    }

    /**
     * The canonical CEO root: id {@code ceo}, no parent, every business permission, system-owned.
     *
     * <p>This is the only position that is created with the business rather than by a manager. The
     * two factories differ only in whether the creation stamp is known; keeping both avoids a
     * magic zero in the registration path.
     */
    public static BusinessPositionRecord ceo() {
        return ceo(0L, null);
    }

    /** The canonical CEO root stamped with its creation time and the owner who founded it. */
    public static BusinessPositionRecord ceo(final long createdAt, final UUID createdBy) {
        return new BusinessPositionRecord(
                CEO_POSITION_ID,
                CEO_TITLE,
                null,
                BusinessPermission.all(),
                0L,
                0,
                true,
                createdAt,
                createdBy);
    }

    /** True when this position carries the permission. The CEO stores all of them explicitly. */
    public boolean has(final BusinessPermission permission) {
        return permission != null && permissions.contains(permission);
    }

    /** True when this position is the fixed root. */
    public boolean isCeo() {
        return CEO_POSITION_ID.equals(id) || system;
    }

    /** Serializes this position into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("title", title);
        tag.putString("parentId", parentId == null ? "" : parentId);
        tag.putString("permissions", BusinessPermission.format(permissions));
        tag.putLong("wagePerDay", wagePerDay);
        tag.putInt("order", order);
        tag.putBoolean("system", system);
        tag.putLong("createdAt", createdAt);
        NbtSupport.putUuid(tag, "createdBy", createdBy);
        return tag;
    }

    /**
     * Reads one position, returning {@code null} when it cannot be used.
     *
     * <p>A malformed row is dropped by the caller. Returning {@code null} rather than throwing is
     * the same shape {@link BusinessEmployee#read} uses, so one damaged position cannot make a
     * whole business unreadable.
     */
    public static BusinessPositionRecord read(final CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        final String id = NbtSupport.getString(tag, "id", "").trim();
        final String title = NbtSupport.getString(tag, "title", "").trim();
        final long wage = NbtSupport.getLong(tag, "wagePerDay", 0L);
        final int order = NbtSupport.getInt(tag, "order", 0);
        if (id.isEmpty() || title.isEmpty() || title.length() > MAX_TITLE_LENGTH
                || wage < 0L || wage > BusinessKeys.MAX_WAGE_PER_DAY || order < 0) {
            return null;
        }
        final String parent = NbtSupport.getString(tag, "parentId", "").trim();
        try {
            return new BusinessPositionRecord(
                    id,
                    title,
                    parent.isEmpty() ? null : parent,
                    BusinessPermission.parseCsv(NbtSupport.getString(tag, "permissions", "")),
                    wage,
                    order,
                    NbtSupport.getBoolean(tag, "system", false),
                    NbtSupport.getLong(tag, "createdAt", 0L),
                    NbtSupport.getUuid(tag, "createdBy"));
        } catch (final IllegalArgumentException e) {
            // A self-parent or a value the compact constructor rejects. Drop the row; the
            // business and every other position still load.
            return null;
        }
    }
}
