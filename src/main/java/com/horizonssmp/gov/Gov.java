package com.horizonssmp.gov;

import com.horizonssmp.gov.command.GovCommand;
import com.horizonssmp.gov.config.GovConfig;
import com.horizonssmp.gov.event.GovHandshakeHandler;
import com.horizonssmp.gov.event.LawJoinHandler;
import com.horizonssmp.gov.server.GovHandlers;
import com.horizonssmp.gov.server.BountyService;
import com.horizonssmp.gov.server.GovServerLifecycle;
import com.horizonssmp.gov.server.FineJoinHandler;
import com.horizonssmp.gov.server.JobJoinHandler;
import com.horizonssmp.gov.server.DetentionEnforcement;
import com.horizonssmp.gov.server.WeaponClassifier;
import com.horizonssmp.gov.server.WeaponEnforcement;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Gov: the Horizons SMP government mod for NeoForge 1.21.1.
 *
 * <p>The mod is required on both sides. That is enforced by a non-optional versioned network
 * channel, not by the toml: see {@code com.horizonssmp.gov.network.GovNetwork} for why
 * {@code displayTest} cannot do it on this loader version. The server owns the government data
 * and every action; the client owns the dashboard screen and asks the server for everything it
 * shows.
 *
 * <p>This class deliberately contains no client references. The dashboard lives in
 * {@code com.horizonssmp.gov.client} and is opened by the K keybind or by the server through the
 * {@code gov:open_screen} payload. The law is delivered as a vanilla written book on first join
 * and through {@code /gov law}; a vanilla client can still read that book if it is ever given one.
 *
 * <p>The law data is loaded during construction so a missing or malformed generated JSON fails
 * server startup immediately, with the resource name in the exception, rather than at the first
 * player join.
 *
 * <p>The constructor takes the {@link ModContainer} because that is the only supported way to
 * register a config: {@code FMLModContainer} injects exactly {@code IEventBus}, {@code ModContainer}
 * and {@code Dist}, and a mod class may have only one public constructor. The container is also
 * where the mod version is read from, which is the version the network channel negotiates.
 */
@Mod(Gov.MODID)
public final class Gov {

    /** Mod and resource id. Must match {@code modId} in {@code neoforge.mods.toml}. */
    public static final String MODID = "gov";

    /** Shared logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** The mod version as the loader sees it, or {@code "dev"} before construction. */
    private static volatile String version = "dev";

    public Gov(final ModContainer container) {
        // The version the network channel negotiates. Read here because this is the one place the
        // container is available, and the payload-registration event fires later, after mod
        // construction. In a dev run ModDevGradle reports "0.0NONE"; in a built jar this is the
        // jar version the toml and the README talk about.
        try {
            version = container.getModInfo() != null && container.getModInfo().getVersion() != null
                    ? container.getModInfo().getVersion().toString()
                    : "dev";
        } catch (final RuntimeException e) {
            LOGGER.warn("[gov] could not read the mod version: {}", e.toString());
        }

        // Server-side rules (payment point, fees, limits). Registered first so the spec exists
        // before any listener that reads a value runs.
        container.registerConfig(ModConfig.Type.SERVER, GovConfig.SPEC);

        // NOTE: Do NOT auto-save the config on load. NeoForge already fills missing keys
        // with their defaults on load and preserves the user's existing values. Forcing a
        // save() here caused NeoForge to back up the file to *.bak and regenerate it from
        // defaults, which wiped user edits (e.g. gov.bank.enabled) and disabled the economy.
        // Config migration, if ever needed, must be done without rewriting the whole file.

        // Load and validate the bundled law before registering anything that could use it. A
        // broken JSON resource therefore stops startup with a clear message.
        LawData.get();

        // The weapon classifier is data, not code. Loading it here means a missing or malformed
        // mapping stops startup with the resource name, instead of silently classifying everything
        // as NONE and disabling the carry rule for the first player who joins.
        WeaponClassifier.get();

        // The action handlers this build ships. Registering here means the dispatcher is never
        // reachable in a state where a shipped action silently falls back to UNSUPPORTED.
        GovHandlers.registerAll();

        // Game-bus listeners. Registration is kept here so the complete event surface is visible
        // in one place; the handlers live in their own packages.
        NeoForge.EVENT_BUS.addListener(GovCommand::register);
        NeoForge.EVENT_BUS.addListener(LawJoinHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(BountyService::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(JobJoinHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(FineJoinHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(GovHandshakeHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(GovServerLifecycle::onServerStarted);
        NeoForge.EVENT_BUS.addListener(GovServerLifecycle::onServerTick);

        // M6 enforcement. Each listener is a thin adapter over WeaponService; the service is
        // assertable headlessly and never references a client class.
        NeoForge.EVENT_BUS.addListener(WeaponEnforcement::onPickup);
        NeoForge.EVENT_BUS.addListener(WeaponEnforcement::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(WeaponEnforcement::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(WeaponEnforcement::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(WeaponEnforcement::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(WeaponEnforcement::onEntityPlace);
        NeoForge.EVENT_BUS.addListener(WeaponEnforcement::onExplosionStart);

        // M8 detention. The sweep is coarse (every 20 ticks) and every listener fails safe: a
        // missing data file or a damaged record must not take the server down or change combat.
        NeoForge.EVENT_BUS.addListener(DetentionEnforcement::onServerTick);
        NeoForge.EVENT_BUS.addListener(DetentionEnforcement::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(DetentionEnforcement::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(DetentionEnforcement::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(DetentionEnforcement::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(DetentionEnforcement::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(DetentionEnforcement::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(DetentionEnforcement::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(DetentionEnforcement::onEntityInteractSpecific);

        LOGGER.info("[gov] side={} modVersion={}", FMLEnvironment.dist, version);
    }

    /** The mod version, or {@code "dev"} when it could not be read. */
    public static String version() {
        return version;
    }
}
