package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.records.BountyRecord;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.server.economy.AccountService;
import com.horizonssmp.gov.server.economy.LiveVaultGateway;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.server.economy.VaultGateway;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class BountyService {

    private static final Map<String, Long> KILLER_COOLDOWNS = new ConcurrentHashMap<>();

    private BountyService() {
    }

    public static String place(final ServerPlayer poster, final ServerPlayer targetOnline,
            final UUID targetUuid, final String targetName, final long amount) {
        if (poster == null || poster.getServer() == null) {
            return "Bounty placement requires a player.";
        }
        final GovSavedData data = GovSavedData.get(poster.getServer());
        final long now = System.currentTimeMillis();
        if (targetUuid == null || targetUuid.equals(poster.getUUID())) {
            return "You cannot place a bounty on yourself.";
        }
        final int min = GovConfig.BOUNTY_MIN.get();
        final int max = GovConfig.BOUNTY_MAX.get();
        if (amount < min || amount > max) {
            return "Bounty amount must be between " + min + " and " + max + " gold.";
        }
        int activeForTarget = 0;
        int activeForPoster = 0;
        for (final BountyRecord bounty : data.bounties().values()) {
            if (!bounty.activeAt(now)) {
                continue;
            }
            if (targetUuid.equals(bounty.targetUuid())) {
                activeForTarget++;
            }
            if (poster.getUUID().equals(bounty.posterUuid())) {
                activeForPoster++;
            }
        }
        if (activeForTarget >= GovConfig.BOUNTY_PER_TARGET_CAP.get()) {
            return "That target already has the maximum number of active bounties.";
        }
        if (activeForPoster >= GovConfig.BOUNTY_PER_POSTER_CAP.get()) {
            return "You already have the maximum number of active bounties.";
        }

        final VaultGateway vault = LiveVaultGateway.live(poster.getServer());
        if (vault.countGold(poster) < amount) {
            return "You do not have enough gold ingots.";
        }
        if (!vault.removeGold(poster, (int) amount)) {
            return "Could not remove gold from your inventory.";
        }

        final AccountService accounts = new AccountService(data);
        final String govId = GovSavedData.GOVERNMENT_ACCOUNT_ID;
        final EconomyResult credit = accounts.credit(govId, amount, "bounty escrow");
        if (credit.failed()) {
            vault.addGold(poster, (int) amount);
            return "Bounty funding failed: " + credit.message();
        }

        final String id = "bounty-" + UUID.randomUUID();
        final long expiresAt = now + GovConfig.BOUNTY_EXPIRY_DAYS.get() * 86400000L;
        data.putBounty(new BountyRecord(
                id,
                targetUuid,
                targetName == null ? "" : targetName,
                poster.getUUID(),
                poster.getName().getString(),
                amount,
                now,
                expiresAt,
                BountyRecord.STATUS_ACTIVE,
                null,
                "",
                0L));
        return "Bounty of " + amount + " gold placed on " + (targetName == null ? targetUuid : targetName) + ".";
    }

    public static void onLivingDeath(final LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer target)) {
            return;
        }
        final ServerPlayer killer = target.getKillCredit() instanceof ServerPlayer sp ? sp : null;
        if (killer == null || killer.getUUID().equals(target.getUUID())) {
            return;
        }
        if (GovConfig.BOUNTY_SAME_IP_BLOCK.get()) {
            final String targetIp = ipOf(target);
            final String killerIp = ipOf(killer);
            if (targetIp != null && targetIp.equals(killerIp)) {
                return;
            }
        }
        final String cooldownKey = killer.getUUID() + "|" + target.getUUID();
        final long now = System.currentTimeMillis();
        final long window = GovConfig.BOUNTY_KILLER_COOLDOWN_SECONDS.get() * 1000L;
        // Prune expired entries so the map stays pair-bounded on long-running servers.
        KILLER_COOLDOWNS.entrySet().removeIf(entry -> now - entry.getValue() >= window);
        final Long last = KILLER_COOLDOWNS.get(cooldownKey);
        if (last != null && now - last < window) {
            return;
        }

        final GovSavedData data = GovSavedData.get(target.getServer());
        final VaultGateway vault = LiveVaultGateway.live(target.getServer());
        final AccountService accounts = new AccountService(data);
        boolean claimedAny = false;
        for (final BountyRecord bounty : new ArrayList<>(data.bounties().values())) {
            if (!bounty.activeAt(now) || !target.getUUID().equals(bounty.targetUuid())) {
                continue;
            }
            final long amount = bounty.amount();
            final EconomyResult debit = accounts.debit(
                    GovSavedData.GOVERNMENT_ACCOUNT_ID, amount, "bounty payout");
            if (debit.failed()) {
                continue;
            }
            boolean paid = vault.addGold(killer, (int) amount);
            if (!paid) {
                final EconomyResult credit = accounts.credit(
                        AccountService.playerAccountId(killer.getUUID()), amount, "bounty payout");
                paid = !credit.failed();
            }
            if (!paid) {
                final EconomyResult rollback = accounts.credit(
                        GovSavedData.GOVERNMENT_ACCOUNT_ID, amount, "bounty payout rollback");
                if (rollback.failed()) {
                    Gov.LOGGER.warn("[gov] bounty payout rollback failed for {} ({} gold): {}",
                            bounty.id(), amount, rollback.message());
                }
                continue;
            }
            data.putBounty(new BountyRecord(
                    bounty.id(),
                    bounty.targetUuid(),
                    bounty.targetName(),
                    bounty.posterUuid(),
                    bounty.posterName(),
                    bounty.amount(),
                    bounty.createdAt(),
                    bounty.expiresAt(),
                    BountyRecord.STATUS_CLAIMED,
                    killer.getUUID(),
                    killer.getName().getString(),
                    now));
            claimedAny = true;
        }
        if (claimedAny) {
            KILLER_COOLDOWNS.put(cooldownKey, now);
        }
    }

    public static String cancel(final ServerPlayer actor, final String bountyId) {
        if (actor == null || actor.getServer() == null) {
            return "Cancel requires a player.";
        }
        final GovSavedData data = GovSavedData.get(actor.getServer());
        final BountyRecord bounty = data.bounty(bountyId);
        if (bounty == null || !bounty.activeAt(System.currentTimeMillis())) {
            return "That bounty is not active.";
        }
        final boolean owner = actor.getUUID().equals(bounty.posterUuid());
        final boolean manager = false; // could check bank.manage but requires capability; keep owner-only for now
        if (!owner && !manager) {
            return "You can only cancel your own bounties.";
        }
        final AccountService accounts = new AccountService(data);
        final EconomyResult debit = accounts.debit(
                GovSavedData.GOVERNMENT_ACCOUNT_ID, bounty.amount(), "bounty refund");
        if (debit.failed()) {
            return "Bounty refund failed: " + debit.message();
        }
        final EconomyResult credit = accounts.credit(
                AccountService.playerAccountId(bounty.posterUuid()), bounty.amount(), "bounty refund");
        if (credit.failed()) {
            final EconomyResult rollback = accounts.credit(
                    GovSavedData.GOVERNMENT_ACCOUNT_ID, bounty.amount(), "bounty refund rollback");
            if (rollback.failed()) {
                Gov.LOGGER.warn("[gov] bounty refund rollback failed for {} ({} gold): {}",
                        bounty.id(), bounty.amount(), rollback.message());
            }
            return "Bounty refund failed: " + credit.message();
        }
        data.putBounty(new BountyRecord(
                bounty.id(),
                bounty.targetUuid(),
                bounty.targetName(),
                bounty.posterUuid(),
                bounty.posterName(),
                bounty.amount(),
                bounty.createdAt(),
                bounty.expiresAt(),
                BountyRecord.STATUS_CANCELLED,
                bounty.claimedBy(),
                bounty.claimedByName(),
                bounty.claimedAt()));
        return "Bounty cancelled and refunded.";
    }

    public static List<BountyRecord> listActive(final GovSavedData data, final UUID targetFilter) {
        final List<BountyRecord> result = new ArrayList<>();
        final long now = System.currentTimeMillis();
        for (final BountyRecord bounty : data.bounties().values()) {
            if (!bounty.activeAt(now)) {
                continue;
            }
            if (targetFilter != null && !targetFilter.equals(bounty.targetUuid())) {
                continue;
            }
            result.add(bounty);
        }
        return result;
    }

    private static String ipOf(final ServerPlayer player) {
        try {
            final Object conn = player.connection;
            final java.lang.reflect.Method m = conn.getClass().getMethod("getRemoteAddress");
            final Object addr = m.invoke(conn);
            return addr == null ? null : addr.toString();
        } catch (final Exception e) {
            return null;
        }
    }
}
