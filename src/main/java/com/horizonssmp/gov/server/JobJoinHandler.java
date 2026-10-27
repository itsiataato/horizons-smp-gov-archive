package com.horizonssmp.gov.server;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Gives a player the default {@code citizen} job the first time their job is ever decided.
 *
 * <p>Every player is expected to hold exactly one primary job; the citizen job is the floor, so a
 * player who has never had a job decision is enrolled as a citizen on login. The
 * {@code jobDecided} marker on the player's file makes that a one-time act: an explicit
 * unassignment sets the marker too, so a player an official deliberately unassigned stays
 * unassigned instead of being re-enrolled on their next login.
 *
 * <p>The assignment goes through {@link JobService}, the same code a {@code JOB_ASSIGN} request
 * runs, so the default obeys the same rules (the citizen job must exist, the change must not strip
 * the last executive). It is recorded in the audit log as a system action, because "why does this
 * player have a job I never gave them" should have an answer in the record.
 *
 * <p>Registered on the game bus from {@link Gov}'s constructor. Only a {@link ServerPlayer} is
 * touched, so this code is safe in singleplayer too and never needs a client-side class.
 */
public final class JobJoinHandler {

    /**
     * The actor stamp for the server's own default assignment: no player, baseline capabilities.
     *
     * <p>WHY: the all-target authority check in {@link JobService#assign} refuses a grant of any
     * authority the actor does not itself hold. A new player's effective set is exactly
     * {@link GovCapabilities#BASELINE}, so the system actor must hold that same baseline to grant
     * the citizen default at all. It still cannot grant executive authority, because the baseline
     * is only the citizen floor and contains none of the executive job's capabilities.
     */
    private static final JobService.Actor SYSTEM_ACTOR =
            new JobService.Actor(null, "server", GovCapabilities.BASELINE);

    private JobJoinHandler() {
    }

    /**
     * Handles a player finishing login.
     *
     * @param event the game-bus login event
     */
    public static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        final MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        try {
            applyDefaultJob(player, GovSavedData.get(server), System.currentTimeMillis());
        } catch (final RuntimeException e) {
            // A failed default must not block a login; the next login retries because nothing was
            // marked decided unless the assignment actually succeeded.
            Gov.LOGGER.error(
                    "[gov] failed to apply the default job to {}",
                    player.getName().getString(), e);
        }
    }

    /** Applies the citizen default when this player's file has no job decision yet. */
    private static void applyDefaultJob(
            final ServerPlayer player, final GovSavedData data, final long now) {
        final String name = player.getName().getString();
        final PlayerRecord record = data.playerOrCreate(player.getUUID(), name, now);
        if (record.jobDecided()) {
            return;
        }
        if (record.employed() || record.employment().employed()) {
            // A file written before the marker existed that already holds a job. Record the
            // decision, change nothing else, so a later unassignment is not undone by this code.
            data.putPlayer(record.withJobDecided(true));
            return;
        }

        final JobRecord citizen = data.job(GovCapabilities.CITIZEN_JOB_ID);
        if (citizen == null) {
            Gov.LOGGER.warn(
                    "[gov] cannot apply the default job to {}: the citizen job is not seeded",
                    name);
            return;
        }
        final JobService.Outcome outcome = JobService.assign(
                data, SYSTEM_ACTOR, player.getUUID(), name, citizen.id(), now);
        if (!outcome.ok()) {
            Gov.LOGGER.warn(
                    "[gov] default job for {} was refused: {}", name, outcome.message());
            return;
        }
        AuditService.appendSystem(
                data, "JOB_DEFAULT", "default citizen job applied on first join");
        player.sendSystemMessage(Component.literal(
                        "You are a Citizen. A government official can assign you another job.")
                .withStyle(ChatFormatting.GRAY));
    }
}
