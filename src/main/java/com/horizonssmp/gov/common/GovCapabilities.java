package com.horizonssmp.gov.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.config.GovConfig;

import net.minecraft.server.level.ServerPlayer;

/**
 * The capability registry: what each action needs, and what each job grants.
 *
 * <p>This is the only place in the mod that maps an action to an authority. A handler never decides
 * who may call it, and the dispatcher never consults a job name: it asks
 * {@link #requiredFor(OpCode)} for the one capability the action needs, and then asks
 * {@link #effective} whether the actor holds it. That single choke point is what makes the matrix
 * reviewable in one file and what keeps a future tab from inventing its own permission rule.
 *
 * <p>{@link #requiredFor(OpCode)} is an exhaustive switch with no {@code default}. Adding an
 * {@link OpCode} constant therefore fails compilation until its capability is written down. That is
 * deliberate: a new action must not be able to inherit "no capability required" by omission.
 *
 * <p>Large method bodies live in helper classes in this package ({@link GovCapabilityRequirements},
 * {@link GovJobCapabilities}, {@link GovEffectiveCapabilities}) so this file stays readable; the
 * public API is unchanged and every method here delegates to them.
 */
public final class GovCapabilities {

    /** The built-in, non-deletable citizen job. */
    public static final String CITIZEN_JOB_ID = "citizen";
    /** The built-in, non-deletable police job. */
    public static final String POLICE_JOB_ID = "police";
    /** The built-in, non-deletable executive job. */
    public static final String GOVEXECUTIVE_JOB_ID = "govexecutive";
    /** The seeded, deletable government worker job, the receptionist role. */
    public static final String GOVWORKER_JOB_ID = "govworker";
    /** The seeded, deletable judge job. */
    public static final String JUDGE_JOB_ID = "judge";
    /** The seeded, deletable builder job: the only players who may take contracts. */
    public static final String BUILDER_JOB_ID = "builder";

    /**
     * The {@code economyMeta.migrations} marker for the E0a capability seed.
     */
    public static final String ECONOMY_CAPABILITY_MIGRATION_KEY = "capabilities.economy.v1";

    /**
     * Capabilities every player has, job or no job.
     */
    public static final Set<GovCapability> BASELINE = Set.copyOf(EnumSet.of(
            GovCapability.PLAYER_LOOKUP_SELF, GovCapability.LAW_VIEW, GovCapability.JOB_VIEW));

    private GovCapabilities() {
    }

    /** True when the player is a vanilla server operator (ops.json). */
    public static boolean isOperator(final ServerPlayer player) {
        if (!GovConfig.opsGrantAllCapabilities()) {
            return false;
        }
        if (player == null || player.getServer() == null) {
            return false;
        }
        try {
            return player.getServer().getPlayerList().isOp(player.getGameProfile());
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /**
     * The one capability an action requires.
     *
     * @param op the action
     * @return the required capability, never {@code null}
     */
    public static GovCapability requiredFor(final OpCode op) {
        return GovCapabilityRequirements.requiredFor(op);
    }

    /**
     * The capability bundle of a seeded job, exactly as the matrix defines it.
     *
     * @param jobId the job id
     * @return the seeded capability set, or an empty set for an unknown id
     */
    public static Set<GovCapability> builtinJobCapabilities(final String jobId) {
        return GovJobCapabilities.builtinJobCapabilities(jobId);
    }

    /**
     * The E0a economy capabilities each built-in job must hold, keyed by job id.
     *
     * @return job id to seeded economy capabilities, never {@code null}
     */
    public static Map<String, Set<GovCapability>> economyCapabilityMigrationSeed() {
        return GovJobCapabilities.economyCapabilityMigrationSeed();
    }

    /**
     * The economy capabilities a job is still missing, given what it holds now.
     *
     * @param jobId the built-in job id named by the seed map
     * @param current the capabilities the job holds now, or {@code null} for none
     * @return the missing capabilities, never {@code null}
     */
    public static Set<GovCapability> economyCapabilityAdditions(
            final String jobId, final Collection<GovCapability> current) {
        return GovJobCapabilities.economyCapabilityAdditions(jobId, current);
    }

    /**
     * The actor's effective capabilities.
     */
    public static Set<GovCapability> effective(
            final PlayerRecord player, final Function<String, JobRecord> jobs) {
        return GovEffectiveCapabilities.effective(player, jobs);
    }

    /**
     * The actor's effective capabilities with one job definition overridden.
     */
    public static Set<GovCapability> effective(
            final PlayerRecord player, final Function<String, JobRecord> jobs,
            final String overriddenJobId, final JobRecord override) {
        return GovEffectiveCapabilities.effective(player, jobs, overriddenJobId, override);
    }

    /**
     * The one government job a player holds.
     */
    public static String jobIdOf(final PlayerRecord player) {
        return GovEffectiveCapabilities.jobIdOf(player);
    }

    /**
     * True when a player holds a job.
     */
    public static boolean holds(final PlayerRecord player, final String jobId) {
        return GovEffectiveCapabilities.holds(player, jobId);
    }

    /** True when the set contains the capability. */
    public static boolean has(
            final Collection<GovCapability> capabilities, final GovCapability required) {
        return capabilities != null && capabilities.contains(required);
    }

    /** The sorted string ids of a capability collection. */
    public static List<String> ids(final Collection<GovCapability> capabilities) {
        final List<String> ids = new ArrayList<>();
        if (capabilities != null) {
            for (final GovCapability capability : capabilities) {
                if (capability != null) {
                    ids.add(capability.id());
                }
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    /** Parses a collection of stored ids, dropping the ones this build does not know. */
    public static Set<GovCapability> fromIds(final Collection<String> ids) {
        final Set<GovCapability> result = EnumSet.noneOf(GovCapability.class);
        if (ids != null) {
            for (final String id : ids) {
                GovCapability.byId(id).ifPresent(result::add);
            }
        }
        return result;
    }

    /**
     * A tolerant copy of a capability collection.
     */
    public static Set<GovCapability> copyOf(final Collection<GovCapability> capabilities) {
        final Set<GovCapability> result = EnumSet.noneOf(GovCapability.class);
        if (capabilities != null) {
            for (final GovCapability capability : capabilities) {
                if (capability != null) {
                    result.add(capability);
                }
            }
        }
        return result;
    }
}
