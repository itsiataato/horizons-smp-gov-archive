package com.horizonssmp.gov.server;

import java.nio.file.Path;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.server.economy.AccountService;
import com.horizonssmp.gov.server.economy.ContractService;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server lifecycle wiring for the government data.
 *
 * <p>Registered from {@link Gov}'s constructor on the game bus. The work happens on
 * {@code ServerStartedEvent} rather than earlier for one reason: that is the first moment when both
 * things this code needs are ready. The world exists, so the overworld's data storage exists and
 * {@code gov.dat} can be attached and seeded; and the server config has been read, so the values
 * logged here are the values the server will actually enforce.
 *
 * <p>The startup contract check fails closed: a failure throws from the listener so a mod with a
 * broken authority contract refuses to boot instead of running in a half-valid state. The tick
 * listener is the opposite: it must never stop the server tick.
 */
public final class GovServerLifecycle {

    /** How often the contract expiry sweep runs, in server ticks. */
    private static final int CONTRACT_SWEEP_INTERVAL_TICKS = 20;

    /** Counts server ticks for the contract expiry sweep. */
    private static int tickCounter;

    private GovServerLifecycle() {
    }

    /**
     * Resets the per-tick global request counter.
     *
     * <p>Registered on the game bus from {@link Gov}'s constructor. The counter is a backstop
     * against many actors asking in one tick; the per-actor buckets are unaffected. A tick listener
     * that never runs would leave the counter at its cap and fail closed, which is the safe
     * direction.
     */
    public static void onServerTick(final ServerTickEvent.Post event) {
        GovActionDispatcher.rateLimiter().beginTick();

        tickCounter++;
        if (tickCounter % CONTRACT_SWEEP_INTERVAL_TICKS != 0 || !GovConfig.contractEnabled()) {
            return;
        }
        try {
            final GovSavedData data = GovSavedData.get(event.getServer());
            final EconomyResult result = new ContractService(data, new AccountService(data))
                    .expireDue(System.currentTimeMillis());
            if (result.ok()) {
                final String expiredField = result.field("contract.expired");
                if (!expiredField.isEmpty()) {
                    final int expired = Integer.parseInt(expiredField);
                    if (expired > 0) {
                        Gov.LOGGER.info("[gov] expired {} contracts", expired);
                    }
                }
            }
        } catch (final RuntimeException e) {
            Gov.LOGGER.warn("[gov] contract expiry sweep failed: {}", e.toString());
        }
    }

    /**
     * Attaches, seeds and reports the government data once the server is up.
     *
     * @param event the server-started event
     */
    public static void onServerStarted(final ServerStartedEvent event) {
        final MinecraftServer server = event.getServer();
        final GovSavedData data = GovSavedData.get(server);
        data.seedIfNeeded(System.currentTimeMillis());

        Gov.LOGGER.info(
                "[gov] government data ready: {} (schema v{}, {} players, {} jobs of which {} templates,"
                        + " {} businesses, {} vehicles, {} weapons, {} permits, {} area permits,"
                        + " {} fines, {} notes, {} warrants, {} plate scans, {} licences, {} audit entries,"
                        + " {} detentions)",
                dataFilePath(server),
                data.schemaVersion(),
                data.players().size(),
                data.jobs().size(),
                data.templateCount(),
                data.businesses().size(),
                data.vehicles().size(),
                data.weapons().size(),
                data.weaponPermits().size(),
                data.areaPermits().size(),
                data.fines().size(),
                data.notes().size(),
                data.warrants().size(),
                data.plateScans().size(),
                data.licenses().size(),
                data.audit().size(),
                data.detentions().size());

        Gov.LOGGER.info(
                "[gov] config: currency={} paymentPoint={} requireReception={} impoundReleaseFeeGold={}"
                        + " requireLicenseToRegister={} plateRegex={} maxDetentionMinutesNoWarrant={}"
                        + " maxDetentionMinutesWithWarrant={} nativeCuffs={} nativeFallback={}"
                        + " jailPlugin={} jailCellConfigured={} jailCell={} {} {} {} leashRadius={}"
                        + " maxDistanceBlocks={} rateLimitActionsPer10s={} govworkerCanAssign={}"
                        + " govworkerCanWaiveFines={} maxRequestsPerTick={} seasonClearBusinesses={}"
                        + " seasonResetTokenSeconds={}",
                GovConfig.currencyMode(),
                GovConfig.paymentPoint(),
                GovConfig.requireReception(),
                GovConfig.impoundReleaseFeeGold(),
                GovConfig.requireLicenseToRegister(),
                GovConfig.plateRegex(),
                GovConfig.maxDetentionMinutesNoWarrant(),
                GovConfig.maxDetentionMinutesWithWarrant(),
                GovConfig.nativeCuffs(),
                GovConfig.nativeFallback(),
                GovConfig.jailPluginCommand().isBlank() ? "none" : "configured",
                GovConfig.jailCellConfigured(),
                GovConfig.jailCellDimension(),
                GovConfig.jailCellX(),
                GovConfig.jailCellY(),
                GovConfig.jailCellZ(),
                GovConfig.jailCellLeashRadius(),
                GovConfig.maxDistanceBlocks(),
                GovConfig.rateLimitActionsPer10s(),
                GovConfig.govworkerCanAssign(),
                GovConfig.govworkerCanWaiveFines(),
                GovConfig.maxRequestsPerTick(),
                GovConfig.seasonClearBusinesses(),
                GovConfig.seasonResetTokenSeconds());

        Gov.LOGGER.info(
                "[gov] fine config: allowRestitution={} joinReminder={} receptionConfigured={}"
                        + " reception={} {} {} {} radius={} paymentPoint={} requireReception={}",
                GovConfig.allowRestitution(),
                GovConfig.fineJoinReminder(),
                GovConfig.receptionConfigured(),
                GovConfig.receptionDimension(),
                GovConfig.receptionX(),
                GovConfig.receptionY(),
                GovConfig.receptionZ(),
                GovConfig.receptionRadius(),
                GovConfig.paymentPoint(),
                GovConfig.requireReception());

        Gov.LOGGER.info(
                "[gov] weapon config: seasonEnd={} townCores={} enforcementMessageCooldownMs={}"
                        + " enforcementAuditCooldownMs={} classifierVersion={}",
                GovConfig.seasonEndMillis(),
                GovConfig.townCoreRegions().size(),
                GovConfig.enforcementMessageCooldownMs(),
                GovConfig.enforcementAuditCooldownMs(),
                WeaponClassifier.get().version());

        final GovStartupCheck.Result result = GovStartupCheck.run(data, server);
        if (result.ok()) {
            Gov.LOGGER.info("[gov] startup contract check passed: {} assertions", result.checks());
        } else {
            Gov.LOGGER.error(
                    "[gov] startup contract check FAILED: {} of {} assertions, {}",
                    result.failures().size(),
                    result.checks(),
                    String.join(" | ", result.failures()));
            throw new IllegalStateException(
                    "[gov] startup contract check failed: "
                            + String.join(" | ", result.failures()));
        }

        Gov.LOGGER.info(
                "[gov] dispatcher ready: {} action codes mapped to capabilities, {} handlers registered",
                OpCode.values().length,
                GovActionDispatcher.registeredOps().size());
    }

    /** The absolute path of the government data file for the running world. */
    public static Path dataFilePath(final MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(GovSavedData.DATA_NAME + ".dat")
                .toAbsolutePath()
                .normalize();
    }
}
