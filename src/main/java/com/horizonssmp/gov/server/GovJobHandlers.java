package com.horizonssmp.gov.server;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.JobKeys;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PermitRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static com.horizonssmp.gov.server.GovHandlerSupport.*;

public final class GovJobHandlers {

    private GovJobHandlers() {
    }

    // ------------------------------------------------------------------ jobs: reads

    /**
     * {@code JOB_LIST}: the roster, one indexed row per job, plus the online names the assign
     * picker offers and the viewer's own capability flags.
     *
     * <p>The viewer flags are computed from the actor's effective set on the server; the client
     * uses them to decide which controls to draw, never to decide what is allowed. Every mutation
     * is checked again on the server.
     */
    static ActionResult jobList(final GovActionDispatcher.ActionContext context) {
        final GovSavedData data = context.data();
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(JobKeys.CAN_MANAGE, Boolean.toString(context.can(GovCapability.JOB_MANAGE)));
        fields.put(JobKeys.CAN_ASSIGN, Boolean.toString(context.can(GovCapability.JOB_ASSIGN)));

        final List<JobRecord> jobs = new ArrayList<>(data.jobs().values());
        jobs.sort(Comparator.comparing(JobRecord::id));
        fields.put(JobKeys.JOB_COUNT, Integer.toString(jobs.size()));
        int row = 0;
        for (final JobRecord job : jobs) {
            if (row >= JobKeys.MAX_LISTED_JOBS) {
                break;
            }
            final String prefix = JobKeys.JOB_ROW_PREFIX + row + ".";
            fields.put(prefix + JobKeys.ROW_ID, job.id());
            fields.put(prefix + JobKeys.ROW_NAME, job.displayName());
            fields.put(prefix + JobKeys.ROW_TYPE, JobService.typeOf(job));
            fields.put(prefix + JobKeys.ROW_MEMBERS,
                    Integer.toString(JobService.holderCount(data, job.id())));
            fields.put(prefix + JobKeys.ROW_CAPS, Integer.toString(job.capabilities().size()));
            row++;
        }

        final List<String> online = onlineNames(context.actor().getServer());
        fields.put(JobKeys.ONLINE_COUNT, Integer.toString(online.size()));
        int onlineRow = 0;
        for (final String rawName : online) {
            if (onlineRow >= JobKeys.MAX_LISTED_ONLINE) {
                break;
            }
            fields.put(JobKeys.ONLINE_PREFIX + onlineRow, Sanitizer.name(rawName));
            onlineRow++;
        }
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    /** {@code JOB_INFO}: one job's definition, its members and the capability registry. */
    static ActionResult jobInfo(final GovActionDispatcher.ActionContext context) {
        final String raw = context.payload(JobKeys.KEY_JOB).strip();
        final JobRecord job = jobById(context.data(), raw);
        if (job == null) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_JOB, JobService.noSuchJobMessage(raw));
        }

        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(JobKeys.CAN_MANAGE, Boolean.toString(context.can(GovCapability.JOB_MANAGE)));
        fields.put(JobKeys.CAN_ASSIGN, Boolean.toString(context.can(GovCapability.JOB_ASSIGN)));
        fields.put(JobKeys.ID, job.id());
        fields.put(JobKeys.NAME, job.displayName());
        fields.put(JobKeys.TYPE, JobService.typeOf(job));
        fields.put(JobKeys.RANK, job.rankLabel());
        fields.put(JobKeys.DEPARTMENT, job.department());

        final List<PlayerRecord> members = JobService.holders(context.data(), job.id());
        fields.put(JobKeys.MEMBER_COUNT, Integer.toString(members.size()));
        int memberRow = 0;
        for (final PlayerRecord member : members) {
            if (memberRow >= JobKeys.MAX_LISTED_MEMBERS) {
                break;
            }
            final String prefix = JobKeys.MEMBER_PREFIX + memberRow + ".";
            fields.put(prefix + JobKeys.MEMBER_NAME, member.lastName());
            fields.put(prefix + JobKeys.MEMBER_UUID, member.uuid().toString());
            fields.put(prefix + JobKeys.MEMBER_ONLINE, Boolean.toString(
                    context.actor().getServer().getPlayerList().getPlayer(member.uuid()) != null));
            memberRow++;
        }

        final List<String> capabilities = GovCapabilities.ids(job.capabilities());
        fields.put(JobKeys.CAP_COUNT, Integer.toString(capabilities.size()));
        for (int i = 0; i < capabilities.size() && i < JobKeys.MAX_LISTED_CAPS; i++) {
            fields.put(JobKeys.CAP_PREFIX + i, capabilities.get(i));
        }

        final List<String> registry = GovCapabilities.ids(EnumSet.allOf(GovCapability.class));
        fields.put(JobKeys.REGISTRY_COUNT, Integer.toString(registry.size()));
        for (int i = 0; i < registry.size() && i < JobKeys.MAX_LISTED_CAPS; i++) {
            fields.put(JobKeys.REGISTRY_PREFIX + i, registry.get(i));
        }
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    // -------------------------------------------------------------- jobs: mutations

    /**
     * {@code JOB_CREATE}: creates a custom job from a display name.
     *
     * <p>The new job starts with an empty capability set; capabilities are added only by an
     * explicit {@code JOB_EDIT}, which is what "additive only through explicit sets" means.
     */
    static ActionResult jobCreate(final GovActionDispatcher.ActionContext context) {
        final JobService.Outcome outcome = JobService.create(
                context.data(),
                actor(context),
                context.payload(JobKeys.KEY_NAME),
                context.now());
        return toResult(context, outcome);
    }

    /**
     * {@code JOB_EDIT}: renames a non-built-in job or adds/removes one of its capabilities.
     *
     * <p>One change per request. A request carrying both a display name and a capability is
     * refused rather than half-applied, because a partial change would be audited as one action.
     */
    static ActionResult jobEdit(final GovActionDispatcher.ActionContext context) {
        final String jobId = Sanitizer.id(context.payload(JobKeys.KEY_JOB));
        final String displayName = context.payload(JobKeys.KEY_DISPLAY_NAME).strip();
        final String capability = context.payload(JobKeys.KEY_CAPABILITY).strip();
        final String mode = context.payload(JobKeys.KEY_MODE).strip().toLowerCase(Locale.ROOT);

        if (displayName.isEmpty() && capability.isEmpty()) {
            return ActionResult.failure(
                    context.request().reqId(),
                    ErrorCode.BAD_PAYLOAD,
                    "Nothing to change: send a displayName or a capability.");
        }
        if (!displayName.isEmpty() && !capability.isEmpty()) {
            return ActionResult.failure(
                    context.request().reqId(),
                    ErrorCode.BAD_PAYLOAD,
                    "One change per request: a rename or a capability edit, not both.");
        }

        final JobService.Outcome outcome;
        if (!displayName.isEmpty()) {
            outcome = JobService.rename(context.data(), jobId, displayName);
        } else {
            final boolean add = JobKeys.MODE_ADD.equals(mode);
            final boolean remove = JobKeys.MODE_REMOVE.equals(mode);
            if (!add && !remove) {
                return ActionResult.failure(
                        context.request().reqId(),
                        ErrorCode.BAD_PAYLOAD,
                        "Mode must be '" + JobKeys.MODE_ADD + "' or '" + JobKeys.MODE_REMOVE + "'.");
            }
            outcome = JobService.editCapability(context.data(), actor(context), jobId, capability, add);
        }
        return toResult(context, outcome);
    }

    /** {@code JOB_DELETE}: deletes a deletable job that nobody holds. */
    static ActionResult jobDelete(final GovActionDispatcher.ActionContext context) {
        final String jobId = Sanitizer.id(context.payload(JobKeys.KEY_JOB));
        final JobService.Outcome outcome = JobService.delete(context.data(), jobId);
        return toResult(context, outcome);
    }

    /**
     * {@code JOB_ASSIGN}: assigns a job to the resolved person, replacing their primary job; an
     * empty job id clears it instead.
     *
     * <p>The affected player is told in chat when they are online. That is a plain chat line, not
     * a {@code GovNotify} push: the notification channel is its own milestone, and a message the
     * player reads now is worth more than a wire format nobody drains yet.
     */
    static ActionResult jobAssign(final GovActionDispatcher.ActionContext context) {
        final GovTargets.Resolved target = context.target();
        if (target == null) {
            // The dispatcher resolves a person for JOB_ASSIGN before calling a handler; a handler
            // reached without one would be a pipeline bug, and this is a real refusal for it.
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_TARGET);
        }
        final String jobId = Sanitizer.id(context.payload(JobKeys.KEY_JOB));
        final JobService.Outcome outcome = JobService.assign(
                context.data(), actor(context), target.uuid(), target.name(), jobId, context.now());
        if (!outcome.ok()) {
            return ActionResult.failure(
                    context.request().reqId(), outcome.code(), outcome.message());
        }

        if (target.online()) {
            final String actorName = Sanitizer.name(context.actor().getName().getString());
            final String message;
            if (jobId.isEmpty()) {
                message = actorName + " cleared your government job.";
            } else {
                final JobRecord job = context.data().job(jobId);
                message = actorName + " assigned you the job "
                        + (job == null ? jobId : job.displayName() + " (" + job.id() + ")")
                        + ".";
            }
            target.player().sendSystemMessage(
                    Component.literal(message).withStyle(ChatFormatting.GRAY));
        }
        return ActionResult.success(context.request().reqId(), outcome.message());
    }


}
