package com.horizonssmp.gov.server;

import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.records.LocationRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * The live-world boundary of the detention rules.
 *
 * <p>The service decides whether a detention is legal, who may release it, and when the term ends.
 * It never touches a player directly: presence, position, teleport, cuffs, confinement effects and
 * chat all pass through this interface. That is what lets the whole rule set, including the
 * native fallback and the leash decision, be asserted headlessly on a scratch data set.
 *
 * <p><b>No inventory method exists here.</b> Article 9.04 requires consent or a warrant for a
 * search and this build implements neither. Cuffs are a movement and interaction state; they do
 * not remove, inspect or block access to an item, and the service never asks for a stack.
 *
 * <p>The live adapter only applies state. It does not decide anything, and every method is called
 * on the server thread.
 */
public interface JailGateway {

    /** A position with its dimension. */
    record Position(String dimension, double x, double y, double z) {

        public Position {
            dimension = dimension == null ? "" : dimension;
        }

        public static Position none() {
            return new Position("", 0.0d, 0.0d, 0.0d);
        }

        /** True when a dimension was recorded. */
        public boolean set() {
            return !dimension.isEmpty();
        }
    }

    /** True when the player is online. */
    boolean present(UUID player);

    /** The player's current dimension and position, or {@link Position#none()} when offline. */
    Position position(UUID player);

    /**
     * Teleports a player to a recorded block position.
     *
     * @return true when a teleport was performed
     */
    boolean teleport(UUID player, LocationRecord location);

    /** Adds or removes the native cuff movement modifier. Idempotent. */
    void applyCuffs(UUID player, boolean cuffed);

    /** Adds or removes the native cell effects. Idempotent. */
    void applyConfinement(UUID player, boolean confined);

    /** Sends one detention line to a player, if online. */
    void message(UUID player, String message);

    /**
     * Runs one configured plugin command, if its root command exists.
     *
     * @return true when a command was dispatched
     */
    boolean command(String command);

    /** An empty gateway, used when no player is present and by the headless checks. */
    JailGateway NONE = new JailGateway() {
        @Override
        public boolean present(final UUID player) {
            return false;
        }

        @Override
        public Position position(final UUID player) {
            return Position.none();
        }

        @Override
        public boolean teleport(final UUID player, final LocationRecord location) {
            return false;
        }

        @Override
        public void applyCuffs(final UUID player, final boolean cuffed) {
        }

        @Override
        public void applyConfinement(final UUID player, final boolean confined) {
        }

        @Override
        public void message(final UUID player, final String message) {
        }

        @Override
        public boolean command(final String command) {
            return false;
        }
    };

    /** The live adapter: reads the real player, applies real state, and decides nothing. */
    final class Live implements JailGateway {

        /** The stable id of the cuff movement modifier. */
        public static final ResourceLocation CUFF_MODIFIER =
                ResourceLocation.fromNamespaceAndPath(Gov.MODID, "cuffs");
        /** The stable id of the cell movement modifier. */
        public static final ResourceLocation CONFINEMENT_MODIFIER =
                ResourceLocation.fromNamespaceAndPath(Gov.MODID, "jail_confinement");

        private final MinecraftServer server;

        public Live(final MinecraftServer server) {
            this.server = server;
        }

        private ServerPlayer online(final UUID player) {
            return server == null || player == null
                    ? null : server.getPlayerList().getPlayer(player);
        }

        @Override
        public boolean present(final UUID player) {
            return online(player) != null;
        }

        @Override
        public Position position(final UUID player) {
            final ServerPlayer player2 = online(player);
            if (player2 == null) {
                return Position.none();
            }
            return new Position(
                    player2.level().dimension().location().toString(),
                    player2.getX(), player2.getY(), player2.getZ());
        }

        @Override
        public boolean teleport(final UUID player, final LocationRecord location) {
            final ServerPlayer player2 = online(player);
            if (player2 == null || location == null || !location.isSet()) {
                return false;
            }
            final ResourceLocation id = ResourceLocation.tryParse(location.dimension());
            if (id == null) {
                Gov.LOGGER.warn("[gov] jail cell has an unreadable dimension: {}", location.dimension());
                return false;
            }
            final ServerLevel level = server.getLevel(
                    ResourceKey.create(Registries.DIMENSION, id));
            if (level == null) {
                Gov.LOGGER.warn("[gov] jail cell dimension is not loaded: {}", location.dimension());
                return false;
            }
            player2.teleportTo(level, location.x() + 0.5d, location.y(), location.z() + 0.5d,
                    player2.getYRot(), player2.getXRot());
            return true;
        }

        @Override
        public void applyCuffs(final UUID player, final boolean cuffed) {
            final ServerPlayer player2 = online(player);
            if (player2 == null) {
                return;
            }
            final AttributeInstance speed = player2.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed == null) {
                return;
            }
            if (cuffed) {
                speed.addOrUpdateTransientModifier(new AttributeModifier(
                        CUFF_MODIFIER, -1.0d, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                player2.setSprinting(false);
            } else {
                speed.removeModifier(CUFF_MODIFIER);
            }
        }

        @Override
        public void applyConfinement(final UUID player, final boolean confined) {
            final ServerPlayer player2 = online(player);
            if (player2 == null) {
                return;
            }
            final AttributeInstance speed = player2.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                if (confined) {
                    speed.addOrUpdateTransientModifier(new AttributeModifier(
                            CONFINEMENT_MODIFIER, -0.5d,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                } else {
                    speed.removeModifier(CONFINEMENT_MODIFIER);
                }
            }
            if (confined) {
                // A short, refreshed effect is the visible part of confinement. It is not a kill
                // protection and it does not alter damage; see DetentionService's surrender note.
                player2.addEffect(new MobEffectInstance(
                        MobEffects.DIG_SLOWDOWN, 200, 1, false, false, true));
            } else {
                player2.removeEffect(MobEffects.DIG_SLOWDOWN);
            }
        }

        @Override
        public void message(final UUID player, final String message) {
            final ServerPlayer player2 = online(player);
            if (player2 == null || message == null || message.isBlank()) {
                return;
            }
            player2.sendSystemMessage(Component.literal("[Gov] " + message)
                    .withStyle(ChatFormatting.YELLOW));
        }

        @Override
        public boolean command(final String command) {
            if (server == null || command == null || command.isBlank()) {
                return false;
            }
            final String trimmed = command.trim();
            final String normalized = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
            final int space = normalized.indexOf(' ');
            final String root = space < 0 ? normalized : normalized.substring(0, space);
            if (server.getCommands().getDispatcher().getRoot().getChild(root) == null) {
                return false;
            }
            try {
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack(), normalized);
                return true;
            } catch (final RuntimeException e) {
                Gov.LOGGER.warn("[gov] jail plugin command failed: {}", e.toString());
                return false;
            }
        }
    }
}
