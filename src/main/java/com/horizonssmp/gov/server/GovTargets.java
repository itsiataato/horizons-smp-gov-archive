package com.horizonssmp.gov.server;

import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;

/**
 * Server-side resolution of a target named in a payload.
 *
 * <p>This class exists to enforce one rule: <b>a payload never names a target's UUID as its
 * identity</b>. The dashboard may send a name or an id, but the server decides who that is, and it
 * only accepts an identifier it can confirm from a source it controls. A client that sends someone
 * else's UUID gets that person only if the server can confirm the UUID independently, which is the
 * same as having sent the name.
 *
 * <p>The sources, in the order they are tried for a name:
 *
 * <ol>
 *   <li>An online player, matched by exact name, case-insensitively. Online wins over everything
 *       else: if a name is online, that is who the actor means.</li>
 *   <li>A stored player file with that last known name. This is how an offline but known player is
 *       still a valid target for a fine or a note.</li>
 *   <li>The server's profile cache, which covers a player who has joined this server but has no
 *       government file yet.</li>
 * </ol>
 *
 * <p>Matching is exact, never a prefix or a fuzzy match. A partial name that resolves to the wrong
 * person is how someone gets fined for another player's offence.
 */
public final class GovTargets {

    /** The payload key that names a person target. */
    public static final String KEY_TARGET = "target";

    /**
     * A resolved target.
     *
     * @param uuid the server-confirmed UUID
     * @param name the server-confirmed display name
     * @param player the online player, or {@code null} when the target is offline
     * @param record the stored player file, or {@code null} when the player has no file yet
     */
    public record Resolved(UUID uuid, String name, ServerPlayer player, PlayerRecord record) {

        public Resolved {
            name = name == null ? "" : name;
        }

        /** True when the target is online right now. */
        public boolean online() {
            return player != null;
        }
    }

    private GovTargets() {
    }

    /**
     * Resolves a target from a payload value.
     *
     * @param server the running server
     * @param data the government data, for the stored-name fallback
     * @param requested the raw payload value: a name, or a UUID string
     * @return the resolved target, or {@code null} when nothing server-side matches
     */
    public static Resolved resolve(
            final MinecraftServer server, final GovSavedData data, final String requested) {
        if (server == null || requested == null || requested.isBlank()) {
            return null;
        }
        final String trimmed = requested.trim();

        final Optional<UUID> asUuid = parseUuid(trimmed);
        if (asUuid.isPresent()) {
            return resolveUuid(server, data, asUuid.get());
        }
        return resolveName(server, data, trimmed);
    }

    /** Parses a UUID string, returning empty for anything that is not one. */
    public static Optional<UUID> parseUuid(final String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolves a target from local data only: online players, stored player files, and the
     * detention scan. Never touches the profile cache, so a read cannot block the server thread
     * on a Mojang lookup (tip t44).
     *
     * <p>Consolidates the {@code recordTarget} (command) and {@code jailInfo} (server) inline
     * copies. A UUID string resolves via online/stored only; a name tries online, then stored,
     * then the detention scan. Note: {@code jailInfo} previously rejected UUID strings with
     * {@code BAD_TARGET}; it now accepts them like {@code recordTarget} always did.
     *
     * @param server the running server
     * @param data the government data
     * @param requested the raw name or UUID string
     * @return the resolved target, or {@code null} when nothing local matches
     */
    public static Resolved resolveLocal(
            final MinecraftServer server, final GovSavedData data, final String requested) {
        if (server == null || data == null || requested == null || requested.isBlank()) {
            return null;
        }
        final String trimmed = requested.trim();
        final Optional<UUID> asUuid = parseUuid(trimmed);
        if (asUuid.isPresent()) {
            final UUID uuid = asUuid.get();
            final ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            if (online != null) {
                return new Resolved(uuid, online.getName().getString(), online,
                        data.player(uuid));
            }
            final PlayerRecord stored = data.player(uuid);
            if (stored != null) {
                return new Resolved(uuid, stored.lastName(), null, stored);
            }
            return null;
        }
        final ServerPlayer online = server.getPlayerList().getPlayerByName(trimmed);
        if (online != null) {
            return new Resolved(online.getUUID(), online.getName().getString(), online,
                    data.player(online.getUUID()));
        }
        final Optional<PlayerRecord> stored = data.playerByName(trimmed);
        if (stored.isPresent()) {
            final PlayerRecord record = stored.get();
            return new Resolved(record.uuid(), record.lastName(), null, record);
        }
        for (final DetentionRecord candidate : data.detentions().values()) {
            if (candidate.targetName().equalsIgnoreCase(trimmed)) {
                return new Resolved(candidate.targetUuid(), candidate.targetName(), null,
                        data.player(candidate.targetUuid()));
            }
        }
        return null;
    }

    /**
     * Resolves a UUID, but only against a source the server controls: the online player list, the
     * stored government files, or the server's own profile cache. An unrecognised UUID is not a
     * target.
     */
    private static Resolved resolveUuid(
            final MinecraftServer server, final GovSavedData data, final UUID uuid) {
        final ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return new Resolved(uuid, online.getName().getString(), online, data.player(uuid));
        }
        final PlayerRecord stored = data.player(uuid);
        if (stored != null) {
            return new Resolved(uuid, stored.lastName(), null, stored);
        }
        final GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            final Optional<GameProfile> profile = cache.get(uuid);
            if (profile.isPresent()) {
                return new Resolved(uuid, profile.get().getName(), null, stored);
            }
        }
        return null;
    }

    /** Resolves a name against the online player list, then the stored files, then the profile cache. */
    private static Resolved resolveName(
            final MinecraftServer server, final GovSavedData data, final String name) {
        for (final ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.getName().getString().equalsIgnoreCase(name)) {
                return new Resolved(
                        online.getUUID(), online.getName().getString(), online, data.player(online.getUUID()));
            }
        }

        final Optional<PlayerRecord> stored = data.playerByName(name);
        if (stored.isPresent()) {
            final PlayerRecord record = stored.get();
            return new Resolved(record.uuid(), record.lastName(), null, record);
        }

        final GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            final Optional<GameProfile> profile = cache.get(name);
            if (profile.isPresent() && profile.get().getName().equalsIgnoreCase(name)) {
                final UUID uuid = profile.get().getId();
                final PlayerRecord cached = data.player(uuid);
                final String resolvedName = (cached != null && !cached.lastName().isBlank())
                        ? cached.lastName() : profile.get().getName();
                return new Resolved(uuid, resolvedName, null, cached);
            }
        }
        return null;
    }
}
