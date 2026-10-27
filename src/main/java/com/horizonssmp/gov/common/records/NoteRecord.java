package com.horizonssmp.gov.common.records;

import java.util.Optional;
import java.util.UUID;

import com.horizonssmp.gov.common.NoteScope;

import net.minecraft.nbt.CompoundTag;

/**
 * A note on a person's file, or - since M11 - a note in a job or business scope.
 *
 * <p>A note records what an official knew at a moment: who wrote it, when, and the text. The
 * original author and the creation time never change. An edit replaces the text in place and stamps
 * {@link #editedAt}, because M3 makes a note editable by any holder of {@code note.write} while the
 * record still has to show that the text was changed and when. The history of every accepted edit
 * lives in the audit log, which records each mutation with its actor; the record keeps only the
 * current text plus the two stamps.
 *
 * <h2>Scopes and schema version</h2>
 *
 * <p>M11 adds {@link #scope} and {@link #scopeId}. This is an <b>additive, tolerant</b> change
 * inside schema version 1, not a migration, for the same reason the later M5-M8 sections were:
 * every existing note is a {@code GLOBAL} note, a missing key reads as {@code GLOBAL}, and an
 * unknown value is preserved verbatim while every list and count refuses to show it. There is no
 * old meaning to translate, so bumping the schema would force a rewrite of every world file for a
 * field that already has a safe default.
 *
 * <p>A note id is unique across every scope. The storage map is keyed by id, so a job note and a
 * business note can never collide; {@code GLOBAL} is simply the scope that the existing note
 * actions read and write.
 *
 * <p>The record is read tolerantly: a tag that lacks the newer keys loads with defaults (empty
 * author name, {@code editedAt} 0 meaning "never edited", {@code GLOBAL} scope), and keys this
 * build does not know are ignored. Nothing about a note may fail a world load. The scope and
 * target shape is enforced by the services that write and list notes, not by the reader: a damaged
 * record is kept, but it is never visible through a scope that cannot account for it.
 *
 * @param id the stable short note id, e.g. {@code n-4f3a9c2b}
 * @param targetUuid the person the note is about; {@code null} only for a business team-board note
 * @param authorUuid the official who wrote it, or {@code null} if unknown
 * @param authorName the author's display name at write time, so the note still reads correctly
 *     after the author is renamed or leaves
 * @param text the note text, already sanitized by the service that wrote it
 * @param createdAt epoch milliseconds the note was written
 * @param editedAt epoch milliseconds of the most recent edit, or {@code 0} when never edited
 * @param scope the stored scope id: {@code GLOBAL}, {@code JOB}, {@code BUSINESS}, or an unknown
 *     value from a newer build, preserved verbatim
 * @param scopeId the job or business id for a scoped note, empty for {@code GLOBAL}
 */
public record NoteRecord(
        String id,
        UUID targetUuid,
        UUID authorUuid,
        String authorName,
        String text,
        long createdAt,
        long editedAt,
        String scope,
        String scopeId) {

    public NoteRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("NoteRecord requires an id");
        }
        authorName = authorName == null ? "" : authorName;
        text = text == null ? "" : text;
        // A blank or absent scope is the version-1 case: an ordinary person-file note. Any other
        // value is kept exactly as it was stored, including one this build does not recognise; the
        // fail-closed rule lives in NoteScope.fromStored and in every lookup, not here.
        scope = scope == null || scope.isBlank() ? NoteScope.GLOBAL.id() : scope;
        scopeId = scopeId == null ? "" : scopeId;
        if (NoteScope.fromStored(scope).orElse(null) == NoteScope.GLOBAL) {
            // GLOBAL has no sub-scope. Clearing a stray value keeps the on-disk shape canonical
            // without touching an unknown scope, whose scopeId is the only identity it has.
            scopeId = "";
        }
    }

    /**
     * The version-1 constructor: an ordinary person-file note.
     *
     * <p>Kept so every existing call site and test compiles unchanged, and so writing a
     * {@code GLOBAL} note stays the default rather than becoming a special case. It is exactly
     * {@code new NoteRecord(id, targetUuid, authorUuid, authorName, text, createdAt, editedAt,
     * NoteScope.GLOBAL.id(), "")}.
     */
    public NoteRecord(
            final String id,
            final UUID targetUuid,
            final UUID authorUuid,
            final String authorName,
            final String text,
            final long createdAt,
            final long editedAt) {
        this(id, targetUuid, authorUuid, authorName, text, createdAt, editedAt,
                NoteScope.GLOBAL.id(), "");
    }

    /** True when the text has been changed since it was written. */
    public boolean edited() {
        return editedAt > 0L;
    }

    /** The recognised scope, or empty for a stored value this build does not know. */
    public Optional<NoteScope> knownScope() {
        return NoteScope.fromStored(scope);
    }

    /** True when this is an ordinary person-file note (including a version-1 record). */
    public boolean global() {
        return knownScope().orElse(null) == NoteScope.GLOBAL;
    }

    /**
     * Returns a copy with new text, keeping the original author, creation time and scope.
     *
     * <p>The author is kept on purpose: an edit corrects or updates the note, it does not reassign
     * it. The audit log records who made the edit.
     *
     * @param newText the replacement text, already sanitized by the caller
     * @param at epoch milliseconds of the edit
     */
    public NoteRecord withText(final String newText, final long at) {
        return new NoteRecord(
                id, targetUuid, authorUuid, authorName, newText, createdAt, at, scope, scopeId);
    }

    /** Serializes this note into a fresh tag. */
    public CompoundTag write() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        NbtSupport.putUuid(tag, "targetUuid", targetUuid);
        NbtSupport.putUuid(tag, "authorUuid", authorUuid);
        tag.putString("authorName", authorName);
        tag.putString("text", text);
        tag.putLong("createdAt", createdAt);
        tag.putLong("editedAt", editedAt);
        // Additive M11 keys. A version-1 reader ignores them; a version-1 tag read by this build
        // defaults to GLOBAL. Writing them on every note makes the shape self-describing.
        tag.putString("scope", scope);
        tag.putString("scopeId", scopeId);
        return tag;
    }

    /** Reads a note. A tag without an id cannot be keyed, so the caller supplies the key. */
    public static NoteRecord read(final CompoundTag tag, final String fallbackId) {
        final String stored = NbtSupport.getString(tag, "id", "");
        final String id = stored.isBlank() ? fallbackId : stored;
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("NoteRecord tag has no id and no fallback");
        }
        return new NoteRecord(
                id,
                NbtSupport.getUuid(tag, "targetUuid"),
                NbtSupport.getUuid(tag, "authorUuid"),
                NbtSupport.getString(tag, "authorName", ""),
                NbtSupport.getString(tag, "text", ""),
                NbtSupport.getLong(tag, "createdAt", 0L),
                NbtSupport.getLong(tag, "editedAt", 0L),
                NbtSupport.getString(tag, "scope", ""),
                NbtSupport.getString(tag, "scopeId", ""));
    }
}
