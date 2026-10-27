package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.records.AuditEntry;
import com.horizonssmp.gov.common.records.HistoryEvent;

public final class GovAuditHistoryStore {
    private final GovSavedData owner;
    final List<AuditEntry> audit = new ArrayList<>();
    long auditSeq;
    final Map<UUID, List<HistoryEvent>> history = new LinkedHashMap<>();
    int historyEventCount;

    public GovAuditHistoryStore(final GovSavedData owner) {
        this.owner = owner;
    }

    public List<AuditEntry> audit() { return Collections.unmodifiableList(audit); }

    public long auditSeq() { return auditSeq; }

    public long nextAuditSeq() {
        if (auditSeq == Long.MAX_VALUE) {
            Gov.LOGGER.warn("[gov] audit sequence reached Long.MAX_VALUE; restarting it at 1");
        }
        auditSeq = nextAuditSequence(auditSeq);
        owner.setDirty();
        return auditSeq;
    }

    public static long nextAuditSequence(final long current) {
        return current < 1L || current == Long.MAX_VALUE ? 1L : current + 1L;
    }

    public void addAudit(final AuditEntry entry) {
        audit.add(entry);
        if (audit.size() > GovSavedData.MAX_AUDIT_ENTRIES) {
            final int drop = audit.size() - GovSavedData.MAX_AUDIT_ENTRIES;
            audit.subList(0, drop).clear();
            Gov.LOGGER.warn("[gov] audit log exceeded {} entries; dropped the {} oldest",
                    GovSavedData.MAX_AUDIT_ENTRIES, drop);
        }
        owner.setDirty();
    }

    int trimAuditTo(final int maximum) {
        final int max = Math.max(0, maximum);
        final int drop = audit.size() - max;
        if (drop <= 0) return 0;
        audit.subList(0, drop).clear();
        owner.setDirty();
        return drop;
    }

    public List<HistoryEvent> historyFor(final UUID playerUuid) {
        if (playerUuid == null) return List.of();
        final List<HistoryEvent> events = history.get(playerUuid);
        return events == null ? List.of() : List.copyOf(events);
    }

    public void appendHistory(final UUID playerUuid, final HistoryEvent event) {
        if (playerUuid == null || event == null) return;
        List<HistoryEvent> events = history.get(playerUuid);
        if (events == null) {
            events = new ArrayList<>();
            history.put(playerUuid, events);
        }
        events.add(0, event);
        historyEventCount++;
        while (events.size() > BusinessKeys.MAX_HISTORY_PER_PLAYER) {
            events.remove(events.size() - 1);
            historyEventCount--;
        }
        trimHistory();
        owner.setDirty();
    }

    void trimHistory() {
        while (historyEventCount > GovSavedData.MAX_HISTORY_ENTRIES) {
            UUID oldestPlayer = null;
            long oldestAt = Long.MAX_VALUE;
            for (final Map.Entry<UUID, List<HistoryEvent>> entry : history.entrySet()) {
                final List<HistoryEvent> events = entry.getValue();
                if (events.isEmpty()) continue;
                final long at = events.get(events.size() - 1).at();
                if (oldestPlayer == null || at < oldestAt) {
                    oldestPlayer = entry.getKey();
                    oldestAt = at;
                }
            }
            if (oldestPlayer == null) {
                historyEventCount = 0;
                break;
            }
            final List<HistoryEvent> events = history.get(oldestPlayer);
            events.remove(events.size() - 1);
            historyEventCount--;
            if (events.isEmpty()) history.remove(oldestPlayer);
        }
        while (history.size() > GovSavedData.MAX_HISTORY_ENTRIES) {
            final java.util.Iterator<Map.Entry<UUID, List<HistoryEvent>>> it = history.entrySet().iterator();
            if (!it.hasNext()) break;
            final Map.Entry<UUID, List<HistoryEvent>> entry = it.next();
            historyEventCount -= entry.getValue().size();
            it.remove();
        }
        if (historyEventCount < 0) historyEventCount = 0;
    }

    public void clearHistory() {
        history.clear();
        historyEventCount = 0;
        owner.setDirty();
    }
}