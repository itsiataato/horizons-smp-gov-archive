package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.enums.WeaponClass;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * The server-side enforcement boundary for Schedule 5.A.
 *
 * <p>This class only cancels interactions. It never removes an item, opens an inventory or searches
 * a container: Article 9.04 requires consent or a warrant for a search, and this build does not
 * implement one. A denial is a chat line (rate-limited) and, at most once per audit window per
 * player, one audit line. A successful registry or permit mutation is audited by the dispatcher,
 * not here.
 *
 * <p>The decision itself lives in {@link WeaponService}, behind {@link WeaponGateway}. This class
 * is the thin NeoForge adapter: it turns each event's stack and position into the service's input,
 * and turns a denial into a cancellation. That split is what lets the startup check assert the
 * whole rule set with no server player.
 *
 * <p>Every event is fired on the server already (the pickup event is server-only; the interaction
 * events are checked for a server player and a server level). A client class is never referenced,
 * so the dedicated-server bytecode gate stays green.
 */
public final class WeaponEnforcement {

    private WeaponEnforcement() {
    }

    // -------------------------------------------------------------------- pickup

    /**
     * Uranium and every explosive other than TNT are illegal to hold for a civilian, so the pickup
     * itself is cancelled. TNT is explicitly never blocked at pickup; its permit and 16-item cap
     * are checked at use and placement.
     */
    public static void onPickup(final ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        final ItemStack stack = event.getItemEntity().getItem();
        final WeaponClassifier.Classification classification = WeaponClassifier.get().classify(stack);
        if (!classification.regulated()) {
            return;
        }
        final WeaponService.Decision decision = WeaponEnforcementHelper.decide(player, classification, WeaponService.Action.PICKUP, null);
        if (!decision.allowed()) {
            event.setCanPickup(TriState.FALSE);
            WeaponEnforcementHelper.deny(player, decision, classification, WeaponService.Action.PICKUP);
        }
    }

    // ------------------------------------------------------------------- use/attack

    /** Right-click use: TACZ firing, an explosive item, or a nuclear item. */
    public static void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        final WeaponClassifier.Classification classification =
                WeaponClassifier.get().classify(event.getItemStack());
        if (!classification.regulated()) {
            return;
        }
        // TNT is placed, not used. The place event checks it at the exact block position.
        if (classification.enforcedClass() == WeaponClass.BLASTING) {
            return;
        }
        final WeaponService.Decision decision = WeaponEnforcementHelper.decide(player, classification, WeaponService.Action.USE, null);
        if (!decision.allowed()) {
            event.setCanceled(true);
            WeaponEnforcementHelper.deny(player, decision, classification, WeaponService.Action.USE);
        }
    }

    /** Right-click on a block: flint on TNT is ignition; otherwise the held item's use. */
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        final ItemStack held = event.getItemStack();
        final BlockState clicked = player.level().getBlockState(event.getPos());
        if (clicked.is(Blocks.TNT) && WeaponEnforcementHelper.isIgnitionItem(held)) {
            final WeaponClassifier.Classification tnt =
                    WeaponClassifier.get().tntClassification();
            final WeaponService.Decision decision = WeaponEnforcementHelper.decide(player, tnt, WeaponService.Action.IGNITE,
                    WeaponEnforcementHelper.position(player.level(), event.getPos()));
            if (!decision.allowed()) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                WeaponEnforcementHelper.deny(player, decision, tnt, WeaponService.Action.IGNITE);
            }
            return;
        }
        final WeaponClassifier.Classification classification = WeaponClassifier.get().classify(held);
        if (!classification.regulated()) {
            return;
        }
        if (classification.enforcedClass() == WeaponClass.BLASTING) {
            // Placement is checked by EntityPlaceEvent at the destination position.
            return;
        }
        final WeaponService.Decision decision = WeaponEnforcementHelper.decide(player, classification, WeaponService.Action.USE,
                WeaponEnforcementHelper.position(player.level(), event.getPos()));
        if (!decision.allowed()) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            WeaponEnforcementHelper.deny(player, decision, classification, WeaponService.Action.USE);
        }
    }

    /** Left-clicking a block with a regulated item is an attack with it. */
    public static void onLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        final WeaponClassifier.Classification classification =
                WeaponClassifier.get().classify(event.getItemStack());
        if (!classification.regulated()) {
            return;
        }
        final WeaponService.Decision decision = WeaponEnforcementHelper.decide(player, classification, WeaponService.Action.ATTACK,
                WeaponEnforcementHelper.position(player.level(), event.getPos()));
        if (!decision.allowed()) {
            event.setCanceled(true);
            WeaponEnforcementHelper.deny(player, decision, classification, WeaponService.Action.ATTACK);
        }
    }

    /** Attacking an entity with a regulated item. */
    public static void onAttackEntity(final AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        final WeaponClassifier.Classification classification =
                WeaponClassifier.get().classify(player.getMainHandItem());
        if (!classification.regulated()) {
            return;
        }
        final WeaponService.Decision decision = WeaponEnforcementHelper.decide(player, classification, WeaponService.Action.ATTACK, null);
        if (!decision.allowed()) {
            event.setCanceled(true);
            WeaponEnforcementHelper.deny(player, decision, classification, WeaponService.Action.ATTACK);
        }
    }

    // ------------------------------------------------------------------ placement

    /**
     * Block placement. TNT is checked for the Blasting permit, the 16-item cap and an active area
     * permit at the destination; uranium and other explosive blocks are checked as ordnance.
     */
    public static void onEntityPlace(final BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        final BlockState state = event.getPlacedBlock();
        final String itemId = WeaponClassifier.itemId(new ItemStack(state.getBlock().asItem()));
        if (itemId.isBlank()) {
            return;
        }
        final WeaponClassifier.Classification classification = WeaponClassifier.get().classifyId(itemId);
        if (!classification.regulated()) {
            return;
        }
        final WeaponService.Decision decision = WeaponEnforcementHelper.decide(player, classification, WeaponService.Action.PLACE,
                WeaponEnforcementHelper.position(player.level(), event.getPos()));
        if (!decision.allowed()) {
            event.setCanceled(true);
            WeaponEnforcementHelper.deny(player, decision, classification, WeaponService.Action.PLACE);
        }
    }

    // ------------------------------------------------------------------ explosion

    /**
     * The detonation check. A TNT explosion is cancelled when no active explosion-area permit
     * covers its center, whoever primed it; a player-caused explosion is also checked for the
     * Blasting permit and the cap when the owner is known. A creeper, bed or other non-player
     * explosion is left to vanilla, because the law is addressed to citizens and the mod has no
     * lawful actor to attribute it to.
     *
     * <p>Article 5.11's self-defense exception is a court finding, not a mechanic. The denial
     * message says so; the mod does not pretend to adjudicate an imminent threat.
     */
    public static void onExplosionStart(final ExplosionEvent.Start event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.isClientSide()) {
            return;
        }
        final net.minecraft.world.level.Explosion explosion = event.getExplosion();
        final PrimedTnt tnt = WeaponEnforcementHelper.findTnt(explosion.getDirectSourceEntity(),
                explosion.getIndirectSourceEntity());
        final ServerPlayer owner = WeaponEnforcementHelper.ownerOf(tnt);
        final String dimension = level.dimension().location().toString();
        final double x = explosion.center().x;
        final double y = explosion.center().y;
        final double z = explosion.center().z;
        final long now = System.currentTimeMillis();

        if (tnt != null) {
            final WeaponClassifier.Classification classification =
                    WeaponClassifier.get().tntClassification();
            final WeaponService.Decision decision;
            if (owner != null) {
                decision = WeaponEnforcementHelper.decideAt(owner, classification, WeaponService.Action.EXPLODE,
                        new WeaponGateway.Position(dimension, x, y, z));
            } else if (WeaponService.activeAreaAt(
                    GovSavedData.get(level.getServer()), dimension, x, y, z, now) == null) {
                decision = WeaponService.Decision.deny(
                        com.horizonssmp.gov.common.ErrorCode.FORBIDDEN,
                        "No active explosion-area permit covers this detonation (Article "
                                + WeaponService.ARTICLE_EXPLOSIVES + ").",
                        "TNT detonation without an active explosion-area permit");
            } else {
                decision = WeaponService.Decision.allow();
            }
            if (!decision.allowed()) {
                event.setCanceled(true);
                if (owner != null) {
                    WeaponEnforcementHelper.deny(owner, decision, classification, WeaponService.Action.EXPLODE);
                } else {
                    WeaponEnforcementHelper.boundedSystemAudit(level, decision, dimension, x, y, z);
                }
            }
            return;
        }

        final Entity direct = explosion.getDirectSourceEntity();
        final Entity indirect = explosion.getIndirectSourceEntity();
        final ServerPlayer source = direct instanceof ServerPlayer directPlayer
                ? directPlayer
                : indirect instanceof ServerPlayer indirectPlayer ? indirectPlayer : null;
        if (source == null) {
            return;
        }
        final GovSavedData data = GovSavedData.get(level.getServer());
        if (WeaponService.activeAreaAt(data, dimension, x, y, z, now) == null) {
            event.setCanceled(true);
            final WeaponService.Decision decision = WeaponService.Decision.deny(
                    com.horizonssmp.gov.common.ErrorCode.FORBIDDEN,
                    "No active explosion-area permit covers this detonation (Article "
                            + WeaponService.ARTICLE_EXPLOSIVES + ").",
                    "player-caused detonation without an active explosion-area permit");
            WeaponEnforcementHelper.deny(source, decision, new WeaponClassifier.Classification(
                    "explosion", WeaponClass.EXPLOSIVE, "explosion", true),
                    WeaponService.Action.EXPLODE);
        }
    }
}