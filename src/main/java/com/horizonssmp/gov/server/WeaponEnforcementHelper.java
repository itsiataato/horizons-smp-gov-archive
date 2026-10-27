package com.horizonssmp.gov.server;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.TownCore;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.config.GovConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Package-private helper holding the throttling state and denial machinery for
 * {@link WeaponEnforcement}. Extracted to keep {@code WeaponEnforcement} under
 * the file-size limit; behavior is unchanged.
 */
final class WeaponEnforcementHelper {

    /** Safety cap for the per-player throttle maps. */
    static final int MAX_THROTTLE_ENTRIES = 1024;

    static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
    static final Map<UUID, Long> LAST_AUDIT = new ConcurrentHashMap<>();
    static volatile long lastSystemAudit;

    private WeaponEnforcementHelper() {
    }

    static boolean isIgnitionItem(final ItemStack stack) {
        return WeaponClassifier.get().isIgnitionItem(WeaponClassifier.itemId(stack));
    }

    static PrimedTnt findTnt(final Entity... candidates) {
        for (final Entity candidate : candidates) {
            if (candidate instanceof PrimedTnt tnt) {
                return tnt;
            }
            if (candidate != null && candidate.getVehicle() instanceof PrimedTnt tnt) {
                return tnt;
            }
        }
        return null;
    }

    static ServerPlayer ownerOf(final PrimedTnt tnt) {
        if (tnt == null) {
            return null;
        }
        return tnt.getOwner() instanceof ServerPlayer player ? player : null;
    }

    static WeaponGateway.Position position(final Level level, final BlockPos pos) {
        return new WeaponGateway.Position(
                level.dimension().location().toString(),
                pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d);
    }

    static WeaponService.Decision decide(
            final ServerPlayer player,
            final WeaponClassifier.Classification classification,
            final WeaponService.Action action,
            final WeaponGateway.Position override) {
        return decideAt(player, classification, action, override);
    }

    static WeaponService.Decision decideAt(
            final ServerPlayer player,
            final WeaponClassifier.Classification classification,
            final WeaponService.Action action,
            final WeaponGateway.Position override) {
        final GovSavedData data = GovSavedData.get(player.getServer());
        final PlayerRecord record = data.playerOrCreate(
                player.getUUID(), player.getName().getString(), System.currentTimeMillis());
        final Set<GovCapability> capabilities = GovCapabilities.effective(record, data::job);
        final WeaponService.Subject subject = new WeaponService.Subject(
                player.getUUID(), player.getName().getString(), capabilities, record);
        final WeaponGateway gateway = new WeaponGateway.Live(player.getServer());
        final List<TownCore> townCores = GovConfig.townCoreRegions();
        final long now = System.currentTimeMillis();
        if (override == null) {
            return WeaponService.evaluate(
                    data, subject, classification, action, gateway, townCores, now);
        }
        return WeaponService.evaluateAt(
                data, subject, classification, action, gateway, override, townCores, now);
    }

    /** Sends the denial, throttled, and records at most one audit line per window. */
    static void deny(
            final ServerPlayer player,
            final WeaponService.Decision decision,
            final WeaponClassifier.Classification classification,
            final WeaponService.Action action) {
        if (player == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        final int messageCooldown = Math.max(0, GovConfig.enforcementMessageCooldownMs());
        final Long lastMessage = LAST_MESSAGE.get(player.getUUID());
        if (lastMessage == null || now - lastMessage >= messageCooldown) {
            LAST_MESSAGE.put(player.getUUID(), now);
            prune(LAST_MESSAGE, now, Math.max(60_000L, messageCooldown * 10L));
            player.sendSystemMessage(Component.literal("[Gov] " + decision.message())
                    .withStyle(ChatFormatting.RED));
        }

        final int auditCooldown = Math.max(0, GovConfig.enforcementAuditCooldownMs());
        final Long lastAudit = LAST_AUDIT.get(player.getUUID());
        if (lastAudit != null && now - lastAudit < auditCooldown) {
            return;
        }
        LAST_AUDIT.put(player.getUUID(), now);
        prune(LAST_AUDIT, now, Math.max(600_000L, auditCooldown * 10L));
        try {
            AuditService.append(
                    GovSavedData.get(player.getServer()),
                    player,
                    "WPN_ENFORCE_DENY",
                    null,
                    Map.of(
                            "action", action.name(),
                            "item", classification.itemId(),
                            "class", classification.enforcedClass().id()),
                    decision.auditReason());
        } catch (final RuntimeException e) {
            Gov.LOGGER.warn("[gov] could not audit a weapon denial: {}", e.toString());
        }
    }

    /** One system audit line for an unattributed illegal detonation, at most once per ten seconds. */
    static void boundedSystemAudit(
            final ServerLevel level, final WeaponService.Decision decision,
            final String dimension, final double x, final double y, final double z) {
        final long now = System.currentTimeMillis();
        if (now - lastSystemAudit < 10_000L) {
            return;
        }
        lastSystemAudit = now;
        try {
            AuditService.append(
                    GovSavedData.get(level.getServer()),
                    null, "", "", "WPN_ENFORCE_DENY", null, "",
                    GovFields.encode(Map.of(
                            "action", "EXPLODE",
                            "dimension", dimension,
                            "x", Double.toString(x),
                            "y", Double.toString(y),
                            "z", Double.toString(z))),
                    decision.auditReason());
        } catch (final RuntimeException e) {
            Gov.LOGGER.warn("[gov] could not audit an unattributed detonation denial: {}",
                    e.toString());
        }
    }

    static void prune(
            final Map<UUID, Long> map, final long now, final long maxAgeMillis) {
        if (map.size() <= MAX_THROTTLE_ENTRIES) {
            return;
        }
        map.entrySet().removeIf(entry -> now - entry.getValue() > maxAgeMillis);
        if (map.size() > MAX_THROTTLE_ENTRIES) {
            map.clear();
        }
    }
}