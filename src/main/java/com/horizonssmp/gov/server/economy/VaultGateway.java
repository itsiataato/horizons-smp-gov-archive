package com.horizonssmp.gov.server.economy;

import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

/**
 * The physical-gold boundary of the vault: what a player really carries and what can really be
 * handed over.
 *
 * <p>This is deliberately the same shape as {@code FineService.Gateway}, with two differences that
 * the economy specification makes explicit. Amounts are {@code int} (a vault operation is bounded
 * by the account and item limits, never by a free-form fine), and every method takes the
 * {@link ServerPlayer} the caller already resolved rather than a UUID, so a service can prove that
 * the inventory belongs to the actor it authorised. {@link #player(UUID)} and {@link #online(UUID)}
 * remain for code that still holds only an id.
 *
 * <h2>What is in scope</h2>
 *
 * <p>The vault is the 36 main inventory slots plus the offhand. Armour slots and the ender chest
 * are NOT part of it and are never counted, moved or touched. Only {@code minecraft:gold_ingot}
 * is gold: a nugget, a block, or any other item is not accepted no matter what it is named or what
 * components it carries.
 *
 * <h2>All-or-nothing</h2>
 *
 * <p>{@link #removeGold} and {@link #addGold} either perform the whole move or leave the physical
 * inventory exactly as it was; they never drop items on the ground and never leave a partial
 * delivery behind. A caller that receives {@code false} has no evidence of any gold movement and
 * must not write a ledger entry.
 *
 * <p>Implementations are server-thread only: they read and mutate a live inventory.
 */
public interface VaultGateway {

    /**
     * The gold ingots the player carries in the main inventory and the offhand.
     *
     * @return a count of at least zero; zero for a null or offline player
     */
    long countGold(ServerPlayer player);

    /**
     * Removes exactly {@code amount} gold ingots, all or nothing.
     *
     * <p>No stack is touched until the full amount has been proved present. An amount of zero is
     * a successful no-op for an online player.
     *
     * @param amount the amount to remove, at least zero
     * @return true only when exactly that many ingots left the inventory
     * @throws IllegalArgumentException when {@code amount} is negative
     */
    boolean removeGold(ServerPlayer player, int amount);

    /**
     * Adds exactly {@code amount} gold ingots, all or nothing.
     *
     * <p>Delivery is stacked at the item's maximum stack size. If the whole amount cannot be
     * placed, any ingots already placed by this call are removed before {@code false} is returned.
     *
     * @param amount the amount to add, at least zero
     * @return true only when exactly that many ingots entered the inventory
     * @throws IllegalArgumentException when {@code amount} is negative
     */
    boolean addGold(ServerPlayer player, int amount);

    /**
     * True when {@link #addGold} would place the whole amount right now.
     *
     * <p>This is a simulation: it must not mutate the inventory. A caller uses it before a debit
     * so a full inventory fails before any account is touched.
     *
     * @param amount the amount to test, at least zero
     * @throws IllegalArgumentException when {@code amount} is negative
     */
    boolean canAccept(ServerPlayer player, int amount);

    /** True when the player is online right now. A null id is offline. */
    boolean online(UUID id);

    /** The online player for the id, or {@code null} when they are offline or the id is null. */
    ServerPlayer player(UUID id);
}
