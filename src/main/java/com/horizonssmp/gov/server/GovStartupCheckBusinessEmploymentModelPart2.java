package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.enums.EmploymentStatus;
import com.horizonssmp.gov.common.enums.HistoryKind;
import com.horizonssmp.gov.common.records.BusinessComplaintRecord;
import com.horizonssmp.gov.common.records.BusinessPositionRecord;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.EmploymentContractRecord;
import com.horizonssmp.gov.common.records.HistoryEvent;
import com.horizonssmp.gov.common.records.NbtSupport;
import com.horizonssmp.gov.common.records.PayrollInfo;
import net.minecraft.nbt.CompoundTag;

final class GovStartupCheckBusinessEmploymentModelPart2 {

    private GovStartupCheckBusinessEmploymentModelPart2() {
    }

    static void run(final GovStartupCheckBusinessEmploymentModelContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final UUID owner = ctx.owner;
        final UUID worker = ctx.worker;
        final UUID stranger = ctx.stranger;
        final BusinessPositionRecord ceo = ctx.ceo;
        final BusinessPositionRecord manager = ctx.manager;
        final EmploymentContractRecord contract = ctx.contract;
        final BusinessComplaintRecord complaint = ctx.complaint;

        // Old constructor source compatibility and old-tag defaults.
        ctx.checks++;
        final BusinessRecord legacy = new BusinessRecord(
                "b-m12-legacy", "StartupCheck M12 Legacy", owner, "shop",
                BusinessRecord.STATUS_ACTIVE, now, owner, "", List.of(),
                PayrollInfo.NONE, "", "", 0L, null);
        if (!legacy.positions().isEmpty() || !legacy.contracts().isEmpty()
                || !legacy.complaints().isEmpty() || legacy.pendingClose()
                || legacy.closeRequestedAt() != 0L) {
            failures.add("the pre-employment business constructor did not default to empty");
        }
        ctx.checks++;
        final CompoundTag oldTag = legacy.write();
        oldTag.remove("positions");
        oldTag.remove("contracts");
        oldTag.remove("complaints");
        oldTag.remove("closeRequestedBy");
        oldTag.remove("closeRequestedAt");
        final BusinessRecord oldRead = BusinessRecord.read(oldTag, null);
        if (!oldRead.positions().isEmpty() || !oldRead.contracts().isEmpty()
                || !oldRead.complaints().isEmpty() || oldRead.pendingClose()) {
            failures.add("a pre-employment business tag did not load with empty defaults");
        }

        // The whole business record with every new block, the founder seat and a close request.
        final BusinessRecord business = legacy
                .withPositions(List.of(ceo, manager))
                .withContracts(List.of(contract))
                .withComplaints(List.of(complaint))
                .withCloseRequest(owner, now + 30L)
                .withCeoContract("StartupCheck M12 Owner", now);
        ctx.checks++;
        if (business.ceoPosition() == null
                || business.position("pos-manager") == null
                || business.activeContractFor(worker) == null
                || !business.hasActiveBusinessRole(owner)
                || !business.hasActiveBusinessRole(worker)
                || !business.pendingClose()
                || business.closeRequestedAt() != now + 30L
                || business.contracts().size() != 2) {
            failures.add("the business employment helpers or the founder seat are wrong");
        }
        ctx.checks++;
        if (!business.equals(BusinessRecord.read(business.write(), null))) {
            failures.add("the full business employment record did not round-trip");
        }

        // Caps: the canonical constructor is the single enforcement point.
        final List<BusinessPositionRecord> manyPositions = new ArrayList<>();
        final List<EmploymentContractRecord> manyContracts = new ArrayList<>();
        final List<BusinessComplaintRecord> manyComplaints = new ArrayList<>();
        for (int i = 0; i <= BusinessKeys.MAX_POSITIONS; i++) {
            manyPositions.add(new BusinessPositionRecord(
                    "pos-cap-" + i, "P" + i, null, Set.of(), 0L, i, false, now, null));
        }
        for (int i = 0; i <= BusinessKeys.MAX_CONTRACTS; i++) {
            manyContracts.add(new EmploymentContractRecord(
                    "c-cap-" + i, "pos-manager", worker, "StartupCheck M12 Worker", owner,
                    0L, false, EmploymentStatus.TERMINATED, now, now, now, 0L, 0L, "cap"));
        }
        for (int i = 0; i <= BusinessKeys.MAX_COMPLAINTS; i++) {
            manyComplaints.add(new BusinessComplaintRecord(
                    "cmp-cap-" + i, worker, owner, "cap", now, false, ""));
        }
        ctx.checks++;
        final BusinessRecord capped = legacy
                .withPositions(manyPositions)
                .withContracts(manyContracts)
                .withComplaints(manyComplaints);
        if (capped.positions().size() != BusinessKeys.MAX_POSITIONS
                || capped.contracts().size() != BusinessKeys.MAX_CONTRACTS
                || capped.complaints().size() != BusinessKeys.MAX_COMPLAINTS) {
            failures.add("a business employment list was not capped at the documented limit");
        }

        // History: newest-first, per-player ten, null ignored, whole-data persistence.
        final GovSavedData historyData = new GovSavedData();
        for (int i = 0; i < 12; i++) {
            historyData.appendHistory(worker, new HistoryEvent(
                    HistoryKind.CONTRACT_ACCEPTED, "event " + i, owner,
                    "StartupCheck M12 Owner", now + i, "c-m12-worker"));
        }
        historyData.appendHistory(null, new HistoryEvent(
                HistoryKind.BUSINESS_FOUNDED, "no player", owner, "StartupCheck", now, "x"));
        historyData.appendHistory(worker, null);
        final List<HistoryEvent> recent = historyData.historyFor(worker);
        ctx.checks++;
        if (recent.size() != BusinessKeys.MAX_HISTORY_PER_PLAYER
                || recent.get(0).at() != now + 11L
                || recent.get(recent.size() - 1).at() != now + 2L
                || !historyData.historyFor(stranger).isEmpty()
                || !historyData.historyFor(null).isEmpty()) {
            failures.add("player history is not newest-first, capped at ten, or null-safe");
        }
        ctx.checks++;
        final CompoundTag historyTag = new CompoundTag();
        historyData.save(historyTag, null);
        final GovSavedData historyReloaded = GovSavedData.load(historyTag, null);
        if (!recent.equals(historyReloaded.historyFor(worker))
                || !historyReloaded.historyFor(stranger).isEmpty()) {
            failures.add("player history did not survive a whole-data save and load");
        }

        // An unknown kind is the one history value dropped on read; the known rows survive.
        ctx.checks++;
        final CompoundTag unknownHistory = new CompoundTag();
        NbtSupport.putUuid(unknownHistory, "player", stranger);
        unknownHistory.putString("kind", "from.the.future");
        unknownHistory.putString("text", "future event");
        unknownHistory.putLong("at", now);
        historyTag.getList("history", net.minecraft.nbt.Tag.TAG_COMPOUND).add(unknownHistory);
        final GovSavedData unknownReloaded = GovSavedData.load(historyTag, null);
        if (!unknownReloaded.historyFor(stranger).isEmpty()
                || unknownReloaded.historyFor(worker).size()
                        != BusinessKeys.MAX_HISTORY_PER_PLAYER) {
            failures.add("an unknown history kind was not dropped without losing known rows");
        }

        ctx.checks++;
        unknownReloaded.clearSeason(false, now);
        if (!unknownReloaded.historyFor(worker).isEmpty()) {
            failures.add("the season reset did not clear player history");
        }
    }
}
