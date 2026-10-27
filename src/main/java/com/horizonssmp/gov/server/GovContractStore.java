package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.records.ContractRecord;

public final class GovContractStore {
    private final GovSavedData owner;
    final Map<String, ContractRecord> contracts = new LinkedHashMap<>();
    transient List<String> contractIdOrder;
    transient Map<UUID, List<String>> contractIdsByParty;
    transient List<String> openContractIds;
    transient int openContractCountCache = -1;
    transient Set<UUID> openContractBuilders;

    public GovContractStore(final GovSavedData owner) {
        this.owner = owner;
    }

    public Map<String, ContractRecord> contracts() { return Collections.unmodifiableMap(contracts); }
    public ContractRecord contract(final String id) { return id == null || id.isBlank() ? null : contracts.get(id); }

    public void putContract(final ContractRecord contract) {
        if (contract == null) throw new IllegalArgumentException("putContract requires a non-null record");
        final ContractRecord previous = contracts.get(contract.id());
        contracts.put(contract.id(), contract);
        owner.setDirty();
        if (openContractCountCache >= 0) {
            final boolean wasOpen = previous != null && !previous.terminal();
            final boolean nowOpen = !contract.terminal();
            if (wasOpen && !nowOpen) {
                openContractCountCache = Math.max(0, openContractCountCache - 1);
            } else if (!wasOpen && nowOpen) {
                openContractCountCache++;
            }
        }
        if (openContractBuilders != null) {
            if (previous != null && !previous.terminal()) openContractBuilders.remove(previous.builder());
            if (!contract.terminal()) openContractBuilders.add(contract.builder());
        }
        if (contractIdOrder != null) {
            if (previous == null) contractIdOrder.add(contract.id());
        }
        if (contractIdsByParty != null) {
            if (previous == null) {
                contractIdsByParty.computeIfAbsent(contract.poster(), key -> new ArrayList<>()).add(contract.id());
                if (!contract.builder().equals(contract.poster())) {
                    contractIdsByParty.computeIfAbsent(contract.builder(), key -> new ArrayList<>()).add(contract.id());
                }
            }
        }
        if (openContractIds != null) {
            openContractIds.remove(contract.id());
            if (!contract.terminal()) openContractIds.add(contract.id());
        }
    }

    void ensureContractIndex() {
        if (contractIdOrder == null || contractIdsByParty == null || openContractIds == null) {
            contractIdOrder = new ArrayList<>();
            contractIdsByParty = new HashMap<>();
            openContractIds = new ArrayList<>();
            for (final Map.Entry<String, ContractRecord> entry : contracts.entrySet()) {
                contractIdOrder.add(entry.getKey());
                final ContractRecord record = entry.getValue();
                contractIdsByParty.computeIfAbsent(record.poster(), key -> new ArrayList<>()).add(record.id());
                if (!record.builder().equals(record.poster())) {
                    contractIdsByParty.computeIfAbsent(record.builder(), key -> new ArrayList<>()).add(record.id());
                }
                if (!record.terminal()) openContractIds.add(record.id());
            }
        }
    }

    public List<String> contractIdOrder() {
        ensureContractIndex();
        return List.copyOf(contractIdOrder);
    }

    public List<String> contractIdsForParty(final UUID party) {
        if (party == null) return List.of();
        ensureContractIndex();
        final List<String> ids = contractIdsByParty.get(party);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    public List<String> openContractIds() {
        ensureContractIndex();
        return List.copyOf(openContractIds);
    }

    public boolean hasOpenContract(final UUID builder) {
        ensureOpenContractIndex();
        return openContractBuilders.contains(builder);
    }

    public int openContractCount() {
        if (openContractCountCache < 0) {
            int count = 0;
            for (final ContractRecord contract : contracts.values()) {
                if (!contract.terminal()) count++;
            }
            openContractCountCache = count;
            ensureOpenContractIndex();
        }
        return openContractCountCache;
    }

    void ensureOpenContractIndex() {
        if (openContractBuilders == null) {
            openContractBuilders = new HashSet<>();
            for (final ContractRecord contract : contracts.values()) {
                if (!contract.terminal()) openContractBuilders.add(contract.builder());
            }
        }
    }
}