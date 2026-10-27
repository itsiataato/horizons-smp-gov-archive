package com.horizonssmp.gov.common.records;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/**
 * The singleton economy counters, nonce store and one-time migration markers.
 *
 * <p>This is deliberately a separate record rather than fields on {@code GovSavedData}: the
 * conservation identity, the ledger and contract sequences and the replay guard all move together
 * and must be readable and writable as one block.
 *
 * <ul>
 *   <li>{@code totalDeposited - totalWithdrawn} is the gold that has ever entered the world; it is
 *       the right-hand side of the conservation invariant and only physical movement changes it.</li>
 *   <li>{@code contractEscrow} is a liability bucket, not an account. It is excluded from the
 *       balance sum and added to it when the invariant is checked.</li>
 *   <li>{@code nextLedgerSeq} starts at {@code 1} and is never reused; {@code nextContractSeq}
 *       allocates contract ids the same way.</li>
 *   <li>{@code nonces} keeps the most recent accepted request nonces per actor. The newest nonce
 *       is the last element of each list, and a list never holds more than
 *       {@link #MAX_NONCES_PER_ACTOR} entries. A repeated nonce is never appended.</li>
 *   <li>{@code migrations} records migration ids that have already run, so a one-time capability
 *       seed is not re-applied after an operator removes it.</li>
 * </ul>
 *
 * <p>The record is immutable and defensively copies its collections. {@link #nonces()} exposes an
 * unmodifiable map of unmodifiable lists and {@link #migrations()} an unmodifiable set; mutating a
 * caller's collection after construction cannot change this record.
 */
public record EconomyMeta(
        long totalDeposited,
        long totalWithdrawn,
        long contractEscrow,
        long nextLedgerSeq,
        long nextContractSeq,
        Map<UUID, List<String>> nonces,
        Set<String> migrations) {

    /** The replay window per actor, as fixed by the economy specification. */
    public static final int MAX_NONCES_PER_ACTOR = 64;

    public EconomyMeta {
        if (totalDeposited < 0L) {
            throw new IllegalArgumentException(
                    "EconomyMeta totalDeposited must not be negative, got " + totalDeposited);
        }
        if (totalWithdrawn < 0L) {
            throw new IllegalArgumentException(
                    "EconomyMeta totalWithdrawn must not be negative, got " + totalWithdrawn);
        }
        if (contractEscrow < 0L) {
            throw new IllegalArgumentException(
                    "EconomyMeta contractEscrow must not be negative, got " + contractEscrow);
        }
        if (nextLedgerSeq < 1L) {
            throw new IllegalArgumentException(
                    "EconomyMeta nextLedgerSeq starts at 1, got " + nextLedgerSeq);
        }
        if (nextContractSeq < 1L) {
            throw new IllegalArgumentException(
                    "EconomyMeta nextContractSeq starts at 1, got " + nextContractSeq);
        }
        nonces = copyNonces(nonces);
        migrations = copyMigrations(migrations);
    }

    /** A fresh meta block for a world that has never had an economy. */
    public static EconomyMeta empty() {
        return new EconomyMeta(0L, 0L, 0L, 1L, 1L, Map.of(), Set.of());
    }

    /** Returns an independent copy. The record is immutable, so the copy is equal by value. */
    public EconomyMeta copy() {
        return new EconomyMeta(
                totalDeposited,
                totalWithdrawn,
                contractEscrow,
                nextLedgerSeq,
                nextContractSeq,
                nonces,
                migrations);
    }

    /** True when this actor has already been credited with this nonce. */
    public boolean hasNonce(final UUID actor, final String nonce) {
        if (actor == null || nonce == null || nonce.isBlank()) {
            return false;
        }
        final List<String> accepted = nonces.get(actor);
        return accepted != null && accepted.contains(nonce);
    }

    /** The nonces retained for an actor, oldest first, or an empty list when none. */
    public List<String> noncesFor(final UUID actor) {
        if (actor == null) {
            return List.of();
        }
        final List<String> accepted = nonces.get(actor);
        return accepted == null ? List.of() : accepted;
    }

    /**
     * Returns this meta with the nonce appended for the actor, or {@code this} unchanged when the
     * actor or nonce is blank or the nonce is already present. The newest nonce is appended last;
     * the constructor keeps at most {@link #MAX_NONCES_PER_ACTOR} per actor.
     */
    public EconomyMeta addNonce(final UUID actor, final String nonce) {
        if (actor == null || nonce == null || nonce.isBlank() || hasNonce(actor, nonce)) {
            return this;
        }
        final List<String> existing = nonces.get(actor);
        final List<String> updated = existing == null
                ? new ArrayList<>()
                : new ArrayList<>(existing);
        updated.add(nonce);
        final Map<UUID, List<String>> copy = new LinkedHashMap<>(nonces);
        copy.put(actor, List.copyOf(updated));
        return new EconomyMeta(
                totalDeposited,
                totalWithdrawn,
                contractEscrow,
                nextLedgerSeq,
                nextContractSeq,
                copy,
                migrations);
    }

    /** True when the named one-time migration has already been recorded. */
    public boolean hasMigration(final String migration) {
        return migration != null && !migration.isBlank() && migrations.contains(migration);
    }

    /**
     * Returns this meta with the named one-time migration recorded, or {@code this} unchanged when
     * the id is blank or already present.
     *
     * <p>The set is copied and kept sorted, matching {@link #toTag()}'s sorted write, so applying
     * the same migration twice is a no-op and a save after a migration stays byte-stable.
     */
    public EconomyMeta withMigration(final String migration) {
        if (migration == null || migration.isBlank() || migrations.contains(migration)) {
            return this;
        }
        final Set<String> next = new TreeSet<>(migrations);
        next.add(migration);
        return new EconomyMeta(
                totalDeposited,
                totalWithdrawn,
                contractEscrow,
                nextLedgerSeq,
                nextContractSeq,
                nonces,
                next);
    }

    /** Serializes this meta block into a fresh tag. */
    public CompoundTag toTag() {
        final CompoundTag tag = new CompoundTag();
        tag.putLong("totalDeposited", totalDeposited);
        tag.putLong("totalWithdrawn", totalWithdrawn);
        tag.putLong("contractEscrow", contractEscrow);
        tag.putLong("nextLedgerSeq", nextLedgerSeq);
        tag.putLong("nextContractSeq", nextContractSeq);
        final List<CompoundTag> nonceEntries = new ArrayList<>(nonces.size());
        for (final Map.Entry<UUID, List<String>> entry : nonces.entrySet()) {
            final CompoundTag actorTag = new CompoundTag();
            NbtSupport.putUuid(actorTag, "actor", entry.getKey());
            NbtSupport.putStringList(actorTag, "nonces", entry.getValue());
            nonceEntries.add(actorTag);
        }
        NbtSupport.putCompoundList(tag, "nonces", nonceEntries);
        // Sorted so a read-write round trip of the file is byte-stable.
        NbtSupport.putStringSet(tag, "migrations", new TreeSet<>(migrations));
        return tag;
    }

    /**
     * Reads the meta block. Missing counters take the empty-world defaults; a negative counter is
     * clamped to zero and a missing or non-positive sequence restarts at one, because a damaged
     * counter must not stop the world loading. The nonce lists are merged per actor when a
     * hand-edited tag repeats one, and the constructor keeps only the newest window.
     */
    public static EconomyMeta fromTag(final CompoundTag tag) {
        final Map<UUID, List<String>> nonces = new LinkedHashMap<>();
        for (final CompoundTag actorTag : NbtSupport.getCompoundList(tag, "nonces")) {
            final UUID actor = NbtSupport.getUuid(actorTag, "actor");
            if (actor == null) {
                continue;
            }
            final List<String> stored = NbtSupport.getStringList(actorTag, "nonces");
            if (stored.isEmpty()) {
                continue;
            }
            nonces.computeIfAbsent(actor, key -> new ArrayList<>()).addAll(stored);
        }
        return new EconomyMeta(
                Math.max(0L, NbtSupport.getLong(tag, "totalDeposited", 0L)),
                Math.max(0L, NbtSupport.getLong(tag, "totalWithdrawn", 0L)),
                Math.max(0L, NbtSupport.getLong(tag, "contractEscrow", 0L)),
                Math.max(1L, NbtSupport.getLong(tag, "nextLedgerSeq", 1L)),
                Math.max(1L, NbtSupport.getLong(tag, "nextContractSeq", 1L)),
                nonces,
                NbtSupport.getStringSet(tag, "migrations"));
    }

    private static Map<UUID, List<String>> copyNonces(
            final Map<UUID, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        final Map<UUID, List<String>> copy = new LinkedHashMap<>();
        for (final Map.Entry<UUID, List<String>> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            final List<String> values = new ArrayList<>(
                    Math.min(entry.getValue().size(), MAX_NONCES_PER_ACTOR));
            for (final String nonce : entry.getValue()) {
                if (nonce != null && !nonce.isBlank()) {
                    values.add(nonce);
                }
            }
            final int size = values.size();
            final List<String> bounded = size <= MAX_NONCES_PER_ACTOR
                    ? values
                    : values.subList(size - MAX_NONCES_PER_ACTOR, size);
            if (!bounded.isEmpty()) {
                copy.put(entry.getKey(), List.copyOf(bounded));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<String> copyMigrations(final Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        final Set<String> copy = new LinkedHashSet<>();
        for (final String migration : source) {
            if (migration != null && !migration.isBlank()) {
                copy.add(migration);
            }
        }
        return Collections.unmodifiableSet(copy);
    }
}
