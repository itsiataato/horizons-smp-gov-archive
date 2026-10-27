package com.horizonssmp.gov.common.records;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;

import net.minecraft.nbt.CompoundTag;

/**
 * A job definition: a named bundle of capabilities.
 *
 * <p>The job is the unit of authority. A handler never asks "is this player a police officer"; it
 * asks whether the actor's effective capability set contains {@code fine.issue}, which came from
 * whatever job the player holds. That indirection is the reason a custom job created in the
 * dashboard is exactly as capable as a built-in one.
 *
 * <p>{@code system} jobs are the three the server always ships: {@code citizen}, {@code police} and
 * {@code govexecutive}. They cannot be deleted, because deleting one would leave players holding a
 * job id that no longer resolves and would silently strip authority. {@code system} jobs can still
 * be edited, so a server can adjust what police may do without a rebuild.
 *
 * @param id the stable job id, lowercase, used as the map key and stored on player records
 * @param displayName the name shown in the dashboard
 * @param color the display colour as {@code #RRGGBB}, or empty for the default
 * @param department the department grouping, for example {@code Police}
 * @param rankLabel the label for the base rank, for example {@code Officer}
 * @param system true for the built-in, non-deletable jobs
 * @param capabilities the capability bundle granted to holders of this job
 * @param createdAt epoch milliseconds the job definition was created
 * @param createdBy the official who created it, or {@code null} for the seeded built-ins
 */
public record JobRecord(
        String id,
        String displayName,
        String color,
        String department,
        String rankLabel,
        boolean system,
        Set<GovCapability> capabilities,
        long createdAt,
        UUID createdBy) {

    public JobRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("JobRecord requires an id");
        }
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        color = color == null ? "" : color;
        department = department == null ? "" : department;
        rankLabel = rankLabel == null ? "" : rankLabel;
        capabilities = capabilities == null
                ? EnumSet.noneOf(GovCapability.class)
                // A caller may pass Set.of(); the shared copy handles empty collections safely.
                : GovCapabilities.copyOf(capabilities);
    }

    /** True when this job may be deleted through {@code JOB_DELETE}. */
    public boolean deletable() {
        return !system;
    }

    /**
     * True for a seeded template such as {@code govworker} or {@code judge}.
     *
     * <p>A template is recognized by its creation stamp rather than by its id: it was written by
     * the seed, so it has no creator, and it is not one of the three protected built-ins. A job
     * created at runtime always records the official who created it, which makes it a custom job.
     * Nothing in the authorization path reads this flag - it is the label the roster shows.
     */
    public boolean template() {
        return !system && createdBy == null;
    }

    /** Serializes this definition into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("displayName", displayName);
        tag.putString("color", color);
        tag.putString("department", department);
        tag.putString("rankLabel", rankLabel);
        tag.putBoolean("system", system);
        NbtSupport.putCapabilities(tag, "capabilities", capabilities);
        tag.putLong("createdAt", createdAt);
        NbtSupport.putUuid(tag, "createdBy", createdBy);
        return tag;
    }

    /** Reads a definition. A tag without an id cannot be keyed, so the caller supplies the key. */
    public static JobRecord read(final CompoundTag tag, final String fallbackId) {
        final String stored = NbtSupport.getString(tag, "id", "");
        final String id = stored.isBlank() ? fallbackId : stored;
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("JobRecord tag has no id and no fallback");
        }
        return new JobRecord(
                id,
                NbtSupport.getString(tag, "displayName", id),
                NbtSupport.getString(tag, "color", ""),
                NbtSupport.getString(tag, "department", ""),
                NbtSupport.getString(tag, "rankLabel", ""),
                NbtSupport.getBoolean(tag, "system", false),
                readCapabilities(tag, id),
                NbtSupport.getLong(tag, "createdAt", 0L),
                NbtSupport.getUuid(tag, "createdBy"));
    }

    /**
     * Reads the capability set, dropping ids this build does not know and logging them.
     *
     * <p>Dropping rather than failing is the tolerant-read rule: a file written by a newer build
     * still loads, and the capability simply has no effect until the build that knows it runs.
     * The log line is what keeps that from being silent, because a dropped capability is a
     * changed authorization set and an operator should be able to find out why.
     */
    private static Set<GovCapability> readCapabilities(final CompoundTag tag, final String jobId) {
        final List<String> raw = NbtSupport.getStringList(tag, "capabilities");
        final Set<GovCapability> known = GovCapabilities.fromIds(raw);

        final Set<String> uniqueRaw = new LinkedHashSet<>(raw);
        if (known.size() < uniqueRaw.size()) {
            final List<String> dropped = new ArrayList<>();
            for (final String capabilityId : uniqueRaw) {
                if (GovCapability.byId(capabilityId).isEmpty()) {
                    dropped.add(capabilityId);
                }
            }
            Gov.LOGGER.warn(
                    "[gov] job '{}' dropped {} capability id(s) this build does not know: {}",
                    jobId, dropped.size(), dropped);
        }
        return known;
    }
}
