package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.JobKeys;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

/**
 * The job rules: every change a job mutation makes to the data, in one place.
 *
 * <p>This is the layer between the action handlers and {@link GovSavedData}. It decides
 * <i>whether</i> a change is legal for the data - the job exists, a built-in is protected, a job in
 * use is not deleted, a capability id is real, an assignment would not strip the last executive -
 * and performs it. It does not decide authority: the one capability an action requires was already
 * checked by {@link GovActionDispatcher}, and nothing here looks at a job name or an operator flag.
 *
 * <p>It takes an {@link Actor} record rather than a {@code ServerPlayer} on purpose. The same
 * service is exercised by the startup contract check with a synthetic actor, so the rules are
 * covered on every boot; the dispatcher's player-facing plumbing (target resolution, audit actor,
 * live capability set) still runs only on a real request.
 *
 * <p>Two guards exist on top of the capability check:
 *
 * <ul>
 *   <li><b>Self-promotion.</b> An actor may not assign themselves a job that would grant authority
 *       they do not already hold. Assigning yourself a job you could already do everything of is
 *       allowed; granting yourself new authority is not.</li>
 *   <li><b>The last executive.</b> An action that would leave no player holding {@code job.manage}
 *       is refused. Without it a lone executive could unassign themselves, or strip the capability
 *       from the executive job, and no one on the server could manage jobs again.</li>
 * </ul>
 *
 * <p>Both are capability-based: they count holders of {@code job.manage}, never holders of a job
 * named "govexecutive".
 */
public final class JobService {

    /** The roster label for a protected built-in job. Wire value, defined with the other keys. */
    public static final String TYPE_BUILTIN = JobKeys.TYPE_BUILTIN;
    /** The roster label for a seeded, deletable template job. Wire value. */
    public static final String TYPE_TEMPLATE = JobKeys.TYPE_TEMPLATE;
    /** The roster label for a job created at runtime. Wire value. */
    public static final String TYPE_CUSTOM = JobKeys.TYPE_CUSTOM;

    /**
     * Who is acting, and what they can do.
     *
     * @param uuid the actor's UUID, used for the assignment stamp and the self-promotion guard
     * @param name the actor's display name, for messages
     * @param capabilities the actor's effective capability set, as the dispatcher computed it
     */
    public record Actor(UUID uuid, String name, Set<GovCapability> capabilities) {

        public Actor {
            name = name == null ? "" : name;
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        }
    }

    /**
     * The outcome of a service call: accepted with a summary, or refused with a code and a message.
     *
     * <p>The handler turns this into an {@link com.horizonssmp.gov.common.ActionResult}; the
     * startup check asserts on it directly.
     */
    public record Outcome(boolean ok, ErrorCode code, String message) {

        public Outcome {
            message = message == null ? "" : message;
        }

        /** An accepted change, with a short already-readable summary. */
        public static Outcome ok(final String message) {
            return new Outcome(true, null, message);
        }

        /** A refused change. */
        public static Outcome refused(final ErrorCode code, final String message) {
            return new Outcome(false, code, message);
        }
    }

    private JobService() {
    }

    // -------------------------------------------------------------- classification

    /** The roster label of a job: built-in, template or custom. */
    public static String typeOf(final JobRecord job) {
        return JobClassification.typeOf(job);
    }

    /**
     * Every player who holds the job: the primary job id or the employment block names it.
     *
     * <p>The same rule {@link GovCapabilities#effective} uses, so the roster's member count is the
     * number of players the job actually grants capabilities to.
     */
    public static List<PlayerRecord> holders(final GovSavedData data, final String jobId) {
        return JobClassification.holders(data, jobId);
    }

    /** The number of players who hold the job. */
    public static int holderCount(final GovSavedData data, final String jobId) {
        return JobClassification.holderCount(data, jobId);
    }

    /** The message for a job id nothing resolves to. One wording for every caller. */
    public static String noSuchJobMessage(final String jobId) {
        return JobClassification.noSuchJobMessage(jobId);
    }

    /** Parses a capability id from a payload or a command. Empty for an id this build does not know. */
    public static Optional<GovCapability> parseCapability(final String raw) {
        return JobClassification.parseCapability(raw);
    }

    // ------------------------------------------------------------------ assignment

    /**
     * Assigns a job to a player, replacing whatever primary job they held; an empty job id clears
     * the job instead.
     *
     * <p>Replacing, never stacking: the primary id, the rank, the assignment stamp and the
     * employment block are all written from the one job, so the record can never hold two.
     *
     * @param data the government data
     * @param actor the acting official
     * @param targetUuid the player to assign to
     * @param targetName the player's name, for the record and the messages
     * @param jobId the job id, or empty to clear the current job
     * @param now epoch milliseconds of the change
     * @return the outcome
     */
    public static Outcome assign(
            final GovSavedData data, final Actor actor, final UUID targetUuid,
            final String targetName, final String jobId, final long now) {
        return JobAssignment.assign(data, actor, targetUuid, targetName, jobId, now);
    }

    // ------------------------------------------------------------ create/delete/edit

    /**
     * Creates a custom job from a display name.
     *
     * <p>The id is derived from the name through {@link Sanitizer#id} and is the stable key from
     * then on; the display name can be edited later, the id never. The capability set starts
     * empty: capabilities are added only through an explicit edit, never inherited.
     *
     * @param data the government data
     * @param actor the creating official, recorded as {@code createdBy}
     * @param name the display name
     * @param now epoch milliseconds of creation
     * @return the outcome
     */
    public static Outcome create(
            final GovSavedData data, final Actor actor, final String name, final long now) {
        return JobMutations.create(data, actor, name, now);
    }

    /**
     * The refusal a delete of this job would get, or {@code null} when it is deletable.
     *
     * <p>Exposed so the command layer can give a console the same specific message a player would
     * get, before it has to insist on a player actor for the dispatch.
     */
    public static Outcome deletePrecheck(final GovSavedData data, final String jobId) {
        return JobMutations.deletePrecheck(data, jobId);
    }

    /**
     * Deletes a deletable job that nobody holds.
     *
     * <p>The in-use guard is what makes a delete safe: a player pointing at a deleted job would
     * silently lose the authority the job granted, with no record saying why.
     */
    public static Outcome delete(final GovSavedData data, final String jobId) {
        return JobMutations.delete(data, jobId);
    }

    /**
     * Renames a job's display name. The id never changes; a built-in can never be renamed.
     */
    public static Outcome rename(
            final GovSavedData data, final String jobId, final String displayName) {
        return JobMutations.rename(data, jobId, displayName);
    }

    /**
     * Adds or removes one capability of a job's set.
     *
     * <p>The capability id is validated against the registry here, at write time: an id this build
     * does not know is refused, never stored. Adding a capability the actor does not hold is
     * refused, mirroring the assignment guard, so a {@code job.manage} holder cannot grant
     * unheld authority through an edit. Removing {@code job.manage} is additionally guarded
     * so the last holder of it cannot be stripped by an edit.
     *
     * @param data the government data
     * @param actor the editing official, whose held capabilities bound an add
     * @param jobId the job to edit
     * @param capabilityId the capability id as sent
     * @param add true to add, false to remove
     * @return the outcome
     */
    public static Outcome editCapability(
            final GovSavedData data, final Actor actor, final String jobId, final String capabilityId,
            final boolean add) {
        return JobMutations.editCapability(data, actor, jobId, capabilityId, add);
    }
}
