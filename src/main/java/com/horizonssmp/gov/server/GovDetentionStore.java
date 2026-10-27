package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.records.DetentionRecord;

public final class GovDetentionStore {
    private final GovSavedData owner;
    final Map<String, DetentionRecord> detentions = new LinkedHashMap<>();
    transient Map<UUID, List<DetentionRecord>> detentionRecordsByTarget;

    public GovDetentionStore(final GovSavedData owner) {
        this.owner = owner;
    }

    public Map<String, DetentionRecord> detentions() { return Collections.unmodifiableMap(detentions); }
    public DetentionRecord detention(final String id) { return id == null ? null : detentions.get(id); }
    public void putDetention(final DetentionRecord detention) {
        detentions.put(detention.id(), detention);
        owner.setDirty();
        if (detentionRecordsByTarget != null) {
            for (final List<DetentionRecord> list : detentionRecordsByTarget.values()) {
                list.removeIf(record -> record.id().equals(detention.id()));
            }
            detentionRecordsByTarget
                    .computeIfAbsent(detention.targetUuid(), key -> new ArrayList<>())
                    .add(detention);
        }
    }

    void ensureDetentionIndex() {
        if (detentionRecordsByTarget == null) {
            detentionRecordsByTarget = new HashMap<>();
            for (final DetentionRecord record : detentions.values()) {
                detentionRecordsByTarget
                        .computeIfAbsent(record.targetUuid(), key -> new ArrayList<>())
                        .add(record);
            }
        }
    }

    public List<DetentionRecord> detentionsForTarget(final UUID targetUuid) {
        if (targetUuid == null) return List.of();
        ensureDetentionIndex();
        final List<DetentionRecord> list = detentionRecordsByTarget.get(targetUuid);
        return list == null ? List.of() : List.copyOf(list);
    }

    public void clearDetentions() {
        detentions.clear();
        detentionRecordsByTarget = null;
        owner.setDirty();
    }
}