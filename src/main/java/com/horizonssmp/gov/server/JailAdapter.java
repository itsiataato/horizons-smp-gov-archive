package com.horizonssmp.gov.server;

import java.util.concurrent.atomic.AtomicBoolean;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.records.DetentionRecord;
import com.horizonssmp.gov.common.records.LocationRecord;

/**
 * The confinement boundary: a plugin jail when one is configured, otherwise a native cell.
 *
 * <p><b>Selection is deliberate and one-way.</b> A configured plugin command always wins, because
 * Article 9.12 names the plugin jail as the normal place of confinement. The native cell is a
 * fallback for a server that has no jail plugin: it teleports to the administrator's configured
 * cell, applies a visible slowdown and leashes the player to that cell. When neither is available
 * the record still exists and still gates release and expiry; nothing physical happens, and that is
 * a valid, honest state rather than an invented jail.
 *
 * <p>The adapter never deletes, clears, searches or moves an inventory. Cuffs and confinement are
 * movement and interaction state only.
 */
public interface JailAdapter {

    /** The three confinement outcomes. */
    enum Kind {
        /** A configured command hook for an external jail plugin. */
        PLUGIN("plugin"),
        /** The mod's own configured cell and leash. */
        NATIVE("native"),
        /** No confinement; the record still gates release and expiry. */
        NONE("none");

        private final String id;

        Kind(final String id) {
            this.id = id;
        }

        /** The stable id stored on a detention record and sent to the client. */
        public String id() {
            return id;
        }
    }

    /** The stable adapter id: {@code plugin}, {@code native} or {@code none}. */
    default String id() {
        return kind().id();
    }

    /** The selected mode. */
    Kind kind();

    /** True when this adapter will attempt physical confinement. */
    boolean confining();

    /** Places the player in the configured confinement, if the adapter can. */
    void confine(DetentionRecord record, JailGateway gateway, long now);

    /** Removes confinement and returns a native-held player to the recorded origin. */
    void release(DetentionRecord record, JailGateway gateway, long now);

    /** Tells the confinement backend that an official extended the term. */
    default void extend(final DetentionRecord record, final JailGateway gateway, final long now) {
    }

    /** Pulls a native-held player back into the cell. No-op for a plugin or absent jail. */
    default void leash(final DetentionRecord record, final JailGateway gateway) {
    }

    /** The no-confinement adapter. */
    JailAdapter NONE = new JailAdapter() {
        @Override
        public Kind kind() {
            return Kind.NONE;
        }

        @Override
        public boolean confining() {
            return false;
        }

        @Override
        public void confine(final DetentionRecord record, final JailGateway gateway, final long now) {
        }

        @Override
        public void release(final DetentionRecord record, final JailGateway gateway, final long now) {
        }
    };

    /**
     * Chooses the adapter from configuration alone, so the decision is assertable without a server.
     *
     * @param pluginCommand the configured command template, or blank
     * @param nativeFallback whether the native fallback is enabled
     * @param cellConfigured whether a cell position is configured
     */
    static Kind select(
            final String pluginCommand, final boolean nativeFallback, final boolean cellConfigured) {
        if (pluginCommand != null && !pluginCommand.isBlank()) {
            return Kind.PLUGIN;
        }
        if (nativeFallback && cellConfigured) {
            return Kind.NATIVE;
        }
        return Kind.NONE;
    }

    /** Builds the adapter for the selected mode. */
    static JailAdapter create(
            final String pluginCommand, final boolean nativeFallback, final boolean cellConfigured,
            final double leashRadius) {
        return switch (select(pluginCommand, nativeFallback, cellConfigured)) {
            case PLUGIN -> new PluginAdapter(pluginCommand);
            case NATIVE -> new NativeAdapter(leashRadius);
            case NONE -> NONE;
        };
    }

    /**
     * The configured command hook.
     *
     * <p>The template is administrator-written and is never assembled from player input without
     * sanitization. Placeholders: {@code {action}}, {@code {player}}, {@code {uuid}},
     * {@code {minutes}}, {@code {reason}} and {@code {id}}.
     */
    final class PluginAdapter implements JailAdapter {

        private static final AtomicBoolean MISSING_LOGGED = new AtomicBoolean();

        private final String template;

        PluginAdapter(final String template) {
            this.template = template == null ? "" : template.strip();
        }

        @Override
        public Kind kind() {
            return Kind.PLUGIN;
        }

        @Override
        public boolean confining() {
            return !template.isBlank();
        }

        @Override
        public void confine(final DetentionRecord record, final JailGateway gateway, final long now) {
            if (record == null || gateway == null || !gateway.present(record.targetUuid())) {
                return;
            }
            final int minutes = Math.max(1,
                    (int) Math.ceil(record.remainingMs(now) / 60_000.0d));
            run(gateway, record, "detain", minutes, record.reason());
        }

        @Override
        public void release(final DetentionRecord record, final JailGateway gateway, final long now) {
            if (record == null || gateway == null) {
                return;
            }
            run(gateway, record, "release", 0, record.releaseReason());
        }

        @Override
        public void extend(final DetentionRecord record, final JailGateway gateway, final long now) {
            if (record == null || gateway == null) {
                return;
            }
            run(gateway, record, "extend", record.maxDurationMinutes(), record.extensionReason());
        }

        private void run(
                final JailGateway gateway, final DetentionRecord record, final String action,
                final int minutes, final String reason) {
            final String command = template
                    .replace("{action}", action)
                    .replace("{player}", token(record.targetName()))
                    .replace("{uuid}", record.targetUuid().toString())
                    .replace("{minutes}", Integer.toString(minutes))
                    .replace("{reason}", token(reason))
                    .replace("{id}", token(record.id()));
            if (!gateway.command(command) && MISSING_LOGGED.compareAndSet(false, true)) {
                Gov.LOGGER.info(
                        "[gov] jail plugin command is configured but its root command is not present;"
                                + " the detention record still gates release and expiry");
            }
        }

        private static String token(final String raw) {
            return Sanitizer.singleLine(raw == null ? "" : raw, 96)
                    .replace('\\', ' ')
                    .replace('"', '\'');
        }
    }

    /** The native cell and leash fallback. */
    final class NativeAdapter implements JailAdapter {

        private final double leashRadius;

        NativeAdapter(final double leashRadius) {
            this.leashRadius = Math.max(1.0d, leashRadius);
        }

        @Override
        public Kind kind() {
            return Kind.NATIVE;
        }

        @Override
        public boolean confining() {
            return true;
        }

        @Override
        public void confine(final DetentionRecord record, final JailGateway gateway, final long now) {
            if (record == null || gateway == null || !record.cell().isSet()) {
                return;
            }
            gateway.teleport(record.targetUuid(), record.cell());
            gateway.applyConfinement(record.targetUuid(), true);
        }

        @Override
        public void release(final DetentionRecord record, final JailGateway gateway, final long now) {
            if (record == null || gateway == null) {
                return;
            }
            if (record.from().isSet()) {
                gateway.teleport(record.targetUuid(), record.from());
            }
            gateway.applyConfinement(record.targetUuid(), false);
            gateway.applyCuffs(record.targetUuid(), false);
        }

        @Override
        public void leash(final DetentionRecord record, final JailGateway gateway) {
            if (record == null || gateway == null || !gateway.present(record.targetUuid())) {
                return;
            }
            if (!record.cell().isSet()) {
                // Escort phase: keep the detainee within 8 blocks of the officer.
                final JailGateway.Position officer = gateway.position(record.officerUuid());
                final JailGateway.Position detainee = gateway.position(record.targetUuid());
                if (!officer.set() || !detainee.set()
                        || !officer.dimension().equals(detainee.dimension())) {
                    return;
                }
                final double dx = detainee.x() - officer.x();
                final double dy = detainee.y() - officer.y();
                final double dz = detainee.z() - officer.z();
                if (dx * dx + dy * dy + dz * dz <= 64.0d) {
                    return;
                }
                final LocationRecord target = new LocationRecord(
                        officer.dimension(),
                        (int) Math.floor(officer.x()),
                        (int) Math.floor(officer.y()),
                        (int) Math.floor(officer.z()));
                if (gateway.teleport(record.targetUuid(), target)) {
                    gateway.message(record.targetUuid(),
                            "You must stay within 8 blocks of the officer.");
                }
                return;
            }
            final JailGateway.Position position = gateway.position(record.targetUuid());
            if (!position.set()) {
                return;
            }
            gateway.applyConfinement(record.targetUuid(), true);
            final LocationRecord cell = record.cell();
            final boolean wrongDimension = !cell.dimension().equals(position.dimension());
            final double dx = position.x() - cell.x();
            final double dy = position.y() - cell.y();
            final double dz = position.z() - cell.z();
            final boolean outside = dx * dx + dy * dy + dz * dz
                    > this.leashRadius * this.leashRadius;
            if (wrongDimension || outside) {
                if (gateway.teleport(record.targetUuid(), cell)) {
                    gateway.message(record.targetUuid(),
                            "You are confined to the cell until released or the term ends.");
                }
            }
        }
    }
}
