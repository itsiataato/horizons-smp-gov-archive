package com.horizonssmp.gov.server;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PermitRecord;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;



public final class GovHandlerSupport {

    private GovHandlerSupport() {
    }

    // -------------------------------------------------------------------- helpers

    /** The service-layer actor for a dispatcher context. */
    static JobService.Actor actor(final GovActionDispatcher.ActionContext context) {
        return new JobService.Actor(
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities());
    }

    /** The service-layer actor for a dispatcher context, as the person service takes it. */
    static PersonService.Actor personActor(final GovActionDispatcher.ActionContext context) {
        return new PersonService.Actor(
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities());
    }

    /** The service-layer actor for a dispatcher context, as the scoped-note service takes it. */
    static ScopedNoteService.Actor scopedActor(
            final GovActionDispatcher.ActionContext context) {
        return new ScopedNoteService.Actor(
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities());
    }

    /**
     * An optional person target: whether the payload named one at all, and what it resolved to.
     *
     * <p>The two facts are different. An absent target is a valid request (the business team
     * board, or the whole of a job's notes); a named target that does not resolve is a
     * {@code BAD_TARGET} refusal. Collapsing them into one nullable value would turn a typo into a
     * silently broader read.
     */
    record OptionalTarget(boolean requested, GovTargets.Resolved resolved) {
    }

    /** Resolves the payload's optional target against server-side sources. */
    static OptionalTarget optionalTarget(
            final GovActionDispatcher.ActionContext context) {
        final String raw = context.payload(GovTargets.KEY_TARGET).strip();
        if (raw.isEmpty()) {
            return new OptionalTarget(false, null);
        }
        return new OptionalTarget(true,
                GovTargets.resolve(context.actor().getServer(), context.data(), raw));
    }

    /** The service-layer actor for a dispatcher context, as the fine service takes it. */
    static FineService.Actor fineActor(final GovActionDispatcher.ActionContext context) {
        return new FineService.Actor(
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities());
    }

    /** The service-layer actor for a dispatcher context, as the vehicle service takes it. */
    static VehicleService.Actor vehicleActor(final GovActionDispatcher.ActionContext context) {
        return new VehicleService.Actor(
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities());
    }

    /** The service-layer actor for a dispatcher context, as the weapon service takes it. */
    static WeaponService.Actor weaponActor(final GovActionDispatcher.ActionContext context) {
        return new WeaponService.Actor(
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities());
    }

    /** The service-layer actor for a dispatcher context, as the business service takes it. */
    static BusinessService.Actor businessActor(
            final GovActionDispatcher.ActionContext context) {
        return new BusinessService.Actor(
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities());
    }

    /** The service-layer actor for a dispatcher context, as the employment service takes it. */
    static BusinessEmploymentService.Actor employmentActor(
            final GovActionDispatcher.ActionContext context) {
        return new BusinessEmploymentService.Actor(
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities());
    }

    /**
     * A payload value, or {@code null} when the key was not sent.
     *
     * <p>The employment edit action uses null as "leave unchanged", which is different from an
     * empty value the caller wants stored. Reading the request map directly is the only way to tell
     * the two apart through {@code ActionContext.payload}, which returns an empty string for a
     * missing key.
     */
    static String optionalValue(
            final GovActionDispatcher.ActionContext context, final String key) {
        return context.request().payload().containsKey(key)
                ? context.payload(key) : null;
    }

    /** The service-layer actor for a dispatcher context, as the detention service takes it. */
    static DetentionService.Actor jailActor(
            final GovActionDispatcher.ActionContext context) {
        return new DetentionService.Actor(
                context.actor().getUUID(),
                context.actor().getName().getString(),
                context.capabilities());
    }

    /** A page number from a payload; anything unreadable is page 0, which the service clamps. */
    static int parsePage(final String raw) {
        return com.horizonssmp.gov.common.GovFields.parseInt(raw, 0);
    }

    /** A long from a payload; anything unreadable is the fallback. */
    static long parseLong(final String raw, final long fallback) {
        return com.horizonssmp.gov.common.GovFields.parseLong(raw, fallback);
    }

    /**
     * Sorted raw online player names, for list pickers.
     *
     * <p>Consolidates 7 sorted-names copies (vehicle/job/fine/business/weapon/server-jail/
     * command-jail lists). Returns raw names sorted by display string; callers sanitize at use
     * (notably {@code jobList}). {@code personSearch} (needs UUIDs) and {@code businessInfo}
     * UUID sets are intentionally untouched.
     *
     * @param server the running server, may be {@code null}
     * @return sorted online names, never {@code null}
     */
    public static List<String> onlineNames(final MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return List.of();
        }
        final List<ServerPlayer> online =
                new ArrayList<>(server.getPlayerList().getPlayers());
        online.sort(Comparator.comparing(player -> player.getName().getString()));
        final List<String> names = new ArrayList<>(online.size());
        for (final ServerPlayer player : online) {
            names.add(player.getName().getString());
        }
        return names;
    }

    /** The job a payload names, or {@code null} when nothing resolves. */
    public static JobRecord jobById(final GovSavedData data, final String raw) {
        final String id = Sanitizer.id(raw);
        return id.isEmpty() ? null : data.job(id);
    }

    /** Turns a job-service outcome into the action result the dispatcher correlates and audits. */
    static ActionResult toResult(
            final GovActionDispatcher.ActionContext context, final JobService.Outcome outcome) {
        if (outcome.ok()) {
            return ActionResult.success(context.request().reqId(), outcome.message());
        }
        return ActionResult.failure(context.request().reqId(), outcome.code(), outcome.message());
    }

    /** Turns a person-service outcome into the action result the dispatcher correlates and audits. */
    static ActionResult toResult(
            final GovActionDispatcher.ActionContext context, final PersonService.Outcome outcome) {
        if (outcome.ok()) {
            return ActionResult.success(context.request().reqId(), outcome.message());
        }
        return ActionResult.failure(context.request().reqId(), outcome.code(), outcome.message());
    }

    /** Turns a fine-service outcome into the action result the dispatcher correlates and audits. */
    static ActionResult toResult(
            final GovActionDispatcher.ActionContext context, final FineService.Outcome outcome) {
        if (outcome.ok()) {
            return ActionResult.success(context.request().reqId(), outcome.message());
        }
        return ActionResult.failure(context.request().reqId(), outcome.code(), outcome.message());
    }

    /** Turns a vehicle-service outcome into the action result the dispatcher correlates and audits. */
    static ActionResult toResult(
            final GovActionDispatcher.ActionContext context, final VehicleService.Outcome outcome) {
        if (outcome.ok()) {
            return ActionResult.success(context.request().reqId(), outcome.message());
        }
        return ActionResult.failure(context.request().reqId(), outcome.code(), outcome.message());
    }

    /** Turns a weapon-service outcome into the action result the dispatcher correlates and audits. */
    static ActionResult toResult(
            final GovActionDispatcher.ActionContext context, final WeaponService.Outcome outcome) {
        if (outcome.ok()) {
            return ActionResult.success(context.request().reqId(), outcome.message());
        }
        return ActionResult.failure(context.request().reqId(), outcome.code(), outcome.message());
    }

    /** Turns a business-service outcome into the action result the dispatcher correlates and audits. */
    static ActionResult toResult(
            final GovActionDispatcher.ActionContext context, final BusinessService.Outcome outcome) {
        if (outcome.ok()) {
            return ActionResult.success(context.request().reqId(), outcome.message());
        }
        return ActionResult.failure(context.request().reqId(), outcome.code(), outcome.message());
    }

    /** Turns an employment outcome into the action result the dispatcher correlates and audits. */
    static ActionResult toResult(
            final GovActionDispatcher.ActionContext context,
            final BusinessEmploymentService.Outcome outcome) {
        if (outcome.ok()) {
            return ActionResult.success(context.request().reqId(), outcome.message());
        }
        return ActionResult.failure(context.request().reqId(), outcome.code(), outcome.message());
    }

    /** Turns a scoped-note outcome into the action result the dispatcher correlates and audits. */
    static ActionResult toResult(
            final GovActionDispatcher.ActionContext context,
            final ScopedNoteService.Outcome outcome) {
        if (outcome.ok()) {
            return ActionResult.success(context.request().reqId(), outcome.message());
        }
        return ActionResult.failure(context.request().reqId(), outcome.code(), outcome.message());
    }

    /** Turns a detention-service outcome into the action result the dispatcher correlates and audits. */
    static ActionResult toResult(
            final GovActionDispatcher.ActionContext context, final DetentionService.Outcome outcome) {
        if (outcome.ok()) {
            return ActionResult.success(context.request().reqId(), outcome.message());
        }
        return ActionResult.failure(context.request().reqId(), outcome.code(), outcome.message());
    }

}
