package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.records.NoteRecord;

public final class GovNoteStore {
    private final GovSavedData owner;
    final Map<String, NoteRecord> notes = new LinkedHashMap<>();

    public GovNoteStore(final GovSavedData owner) {
        this.owner = owner;
    }

    public Map<String, NoteRecord> notes() { return Collections.unmodifiableMap(notes); }
    public NoteRecord note(final String id) { return id == null ? null : notes.get(id); }
    public void putNote(final NoteRecord note) { notes.put(note.id(), note); owner.setDirty(); }
    public boolean removeNote(final String id) {
        if (id == null || notes.remove(id) == null) return false;
        owner.setDirty();
        return true;
    }

    public List<NoteRecord> notesFor(final UUID targetUuid) {
        final List<NoteRecord> result = new ArrayList<>();
        if (targetUuid == null) return result;
        for (final NoteRecord note : notes.values()) {
            if (targetUuid.equals(note.targetUuid()) && isScope(note, NoteScope.GLOBAL)) {
                result.add(note);
            }
        }
        return result;
    }

    public List<NoteRecord> globalNotes() {
        final List<NoteRecord> result = new ArrayList<>();
        for (final NoteRecord note : notes.values()) {
            if (isScope(note, NoteScope.GLOBAL) && note.targetUuid() != null) {
                result.add(note);
            }
        }
        return result;
    }

    public List<NoteRecord> notesInScope(final NoteScope scope, final String scopeId) {
        final List<NoteRecord> result = new ArrayList<>();
        if (scope == null) return result;
        if (scope == NoteScope.GLOBAL) return globalNotes();
        if (scopeId == null || scopeId.isBlank()) return result;
        for (final NoteRecord note : notes.values()) {
            if (scope == NoteScope.JOB && note.targetUuid() == null) continue;
            if (isScope(note, scope) && scopeId.equals(note.scopeId())) {
                result.add(note);
            }
        }
        return result;
    }

    public List<NoteRecord> notesInScopeFor(final NoteScope scope, final String scopeId, final UUID targetUuid) {
        final List<NoteRecord> result = new ArrayList<>();
        if (scope == null || scope == NoteScope.GLOBAL) {
            return targetUuid == null ? result : notesFor(targetUuid);
        }
        if (scopeId == null || scopeId.isBlank()) return result;
        if (scope == NoteScope.JOB && targetUuid == null) return result;
        for (final NoteRecord note : notes.values()) {
            if (isScope(note, scope) && scopeId.equals(note.scopeId())
                    && (targetUuid == null ? note.targetUuid() == null : targetUuid.equals(note.targetUuid()))) {
                result.add(note);
            }
        }
        return result;
    }

    public int scopedNoteCount() {
        int count = 0;
        for (final NoteRecord note : notes.values()) {
            final NoteScope scope = NoteScope.fromStored(note.scope()).orElse(null);
            if (scope == NoteScope.JOB || scope == NoteScope.BUSINESS) count++;
        }
        return count;
    }

    public int storedScopedNoteCount() {
        int count = 0;
        for (final NoteRecord note : notes.values()) {
            if (NoteScope.fromStored(note.scope()).orElse(null) != NoteScope.GLOBAL) count++;
        }
        return count;
    }

    public static boolean isScope(final NoteRecord note, final NoteScope scope) {
        return note != null && scope != null
                && NoteScope.fromStored(note.scope()).orElse(null) == scope;
    }
}