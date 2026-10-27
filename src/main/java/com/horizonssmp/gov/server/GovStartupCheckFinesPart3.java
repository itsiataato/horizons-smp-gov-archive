package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.horizonssmp.gov.common.records.GoldAmount;
import com.horizonssmp.gov.common.records.NbtSupport;
import java.util.UUID;
import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.enums.FineBand;
import com.horizonssmp.gov.common.enums.FineStatus;
import com.horizonssmp.gov.common.records.FineRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import net.minecraft.nbt.CompoundTag;

final class GovStartupCheckFinesPart3 {

    private GovStartupCheckFinesPart3() {
    }

    static void run(final GovStartupCheckFinesContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final GovSavedData pay = ctx.pay;
        final UUID alice = ctx.alice;
        final UUID bob = ctx.bob;
        final UUID carol = ctx.carol;
        final FineService.Actor officer = ctx.officer;
        final FineRecord aggravatedFine = ctx.aggravatedFine;
        final FineRecord collectedFine = ctx.collectedFine;

            final FineService.Page alicePage = FineService.page(
                    pay, alice, EnumSet.noneOf(GovCapability.class), FineKeys.FILTER_ALL, bob, 0);
            ctx.checks++;
            if (alicePage.rows().isEmpty()) {
                failures.add("a player's own fine page is empty");
            }
            boolean leaked = false;
            for (final FineService.FineRow row : alicePage.rows()) {
                if (!alice.equals(row.fine().targetUuid())) {
                    leaked = true;
                    break;
                }
            }
            ctx.checks++;
            if (leaked) {
                failures.add("a fine page without fine.view.all leaked another player's fine");
            }
            final FineService.Page allPage = FineService.page(
                    pay, officer.uuid(), EnumSet.of(GovCapability.FINE_VIEW_ALL),
                    FineKeys.FILTER_ALL, null, 0);
            ctx.checks++;
            if (allPage.total() != pay.fines().size()) {
                failures.add("a fine.view.all page did not see every fine");
            }
            int unpaidCount = 0;
            int waivedCount = 0;
            int paidCount = 0;
            for (final FineRecord fine : pay.fines().values()) {
                if (fine.status() == FineStatus.UNPAID) {
                    unpaidCount++;
                }
                if (fine.status() == FineStatus.WAIVED) {
                    waivedCount++;
                }
                if (fine.status() == FineStatus.PAID) {
                    paidCount++;
                }
            }
            ctx.checks++;
            if (FineService.page(pay, officer.uuid(), EnumSet.of(GovCapability.FINE_VIEW_ALL),
                    FineKeys.FILTER_UNPAID, null, 0).total() != unpaidCount) {
                failures.add("the unpaid filter count is wrong");
            }
            ctx.checks++;
            if (FineService.page(pay, officer.uuid(), EnumSet.of(GovCapability.FINE_VIEW_ALL),
                    FineKeys.FILTER_WAIVED, null, 0).total() != waivedCount) {
                failures.add("the waived filter count is wrong");
            }
            ctx.checks++;
            if (FineService.page(pay, officer.uuid(), EnumSet.of(GovCapability.FINE_VIEW_ALL),
                    FineKeys.FILTER_PAID, null, 0).total() != paidCount) {
                failures.add("the paid filter count is wrong");
            }

            pay.putPlayer(PlayerRecord.blank(carol, "StartupCheck Fine Carol", now));
            for (int i = 0; i < 10; i++) {
                FineService.issue(pay, officer, carol, "StartupCheck Fine Carol",
                        "PETTY", "9.11", "0", "", "",
                        "StartupCheck page filler " + i, now + 10L + i);
            }
            final FineService.Page firstPage = FineService.page(
                    pay, officer.uuid(), EnumSet.of(GovCapability.FINE_VIEW_ALL),
                    FineKeys.FILTER_ALL, carol, 0);
            ctx.checks++;
            if (firstPage.total() != 10 || firstPage.rows().size() != FineKeys.PAGE_SIZE
                    || firstPage.pages() != 2 || firstPage.page() != 0) {
                failures.add("the first fine page did not respect the page size and true total");
            }
            ctx.checks++;
            if (FineService.page(pay, officer.uuid(), EnumSet.of(GovCapability.FINE_VIEW_ALL),
                    FineKeys.FILTER_ALL, carol, 9999).page() != 1) {
                failures.add("an out-of-range fine page was not clamped to the last page");
            }

            final Map<String, String> listFields = FineService.listFields(
                    pay, officer,
                    EnumSet.of(GovCapability.FINE_ISSUE, GovCapability.FINE_WAIVE,
                            GovCapability.FINE_VIEW_ALL),
                    FineKeys.FILTER_ALL, null, 0, List.of("StartupCheck Online"));
            ctx.checks++;
            if (!"true".equals(listFields.get(FineKeys.CAN_ISSUE))
                    || !"true".equals(listFields.get(FineKeys.CAN_VIEW_ALL))
                    || listFields.get(FineKeys.TOTAL) == null
                    || listFields.get(FineKeys.ROW_PREFIX + "0." + FineKeys.ROW_ID) == null
                    || !"1".equals(listFields.get(FineKeys.ONLINE_COUNT))) {
                failures.add("the fine list field block is missing its flags, total or first row");
            }
            ctx.checks++;
            if (!FineService.canViewAll(EnumSet.of(GovCapability.FINE_WAIVE))) {
                failures.add("fine.waive does not imply the fine read scope the reception needs");
            }

            final Map<String, String> detail = FineService.detailFields(
                    scratch, aggravatedFine, EnumSet.of(GovCapability.FINE_WAIVE), officer.uuid());
            ctx.checks++;
            if (!"7".equals(detail.get(FineKeys.EXILE_DAYS))
                    || detail.get(FineKeys.ARTICLE_TITLE) == null
                    || detail.get(FineKeys.ARTICLE_TITLE).isBlank()
                    || !"true".equals(detail.get(FineKeys.CAN_WAIVE_THIS))) {
                failures.add("the fine detail block is missing the exile order, article title or waiver flag");
            }
            final Map<String, String> receiptDetail = FineService.detailFields(
                    pay, collectedFine, EnumSet.of(GovCapability.FINE_WAIVE), alice);
            ctx.checks++;
            if (receiptDetail.get(FineKeys.RECEIPT) == null
                    || receiptDetail.get(FineKeys.RECEIPT).isBlank()
                    || !officer.name().equals(receiptDetail.get(FineKeys.COLLECTOR))
                    || !"false".equals(receiptDetail.get(FineKeys.SELF_PAID))) {
                failures.add("the receipt detail does not name the receipt and collector");
            }
            ctx.checks++;
            if (FineService.visibleFine(pay, bob, EnumSet.noneOf(GovCapability.class),
                    collectedFine.id()) != null) {
                failures.add("a stranger without fine.view.all could read another player's fine");
            }
            ctx.checks++;
            if (FineService.visibleFine(pay, bob, EnumSet.of(GovCapability.FINE_VIEW_ALL),
                    collectedFine.id()) == null) {
                failures.add("a fine.view.all viewer could not read a fine");
            }

            // Tolerant read: an M3 tag has none of the M4 fields and unknown names fall back.
            ctx.checks++;
            final CompoundTag legacyTag = new CompoundTag();
            legacyTag.putString("id", "startup-check-legacy-fine");
            NbtSupport.putUuid(legacyTag, "targetUuid", alice);
            legacyTag.putString("band", "NOT_A_BAND");
            legacyTag.putString("status", "LOST");
            legacyTag.put("amount", GoldAmount.of(8L).write());
            final FineRecord legacyFine = FineRecord.read(legacyTag, null);
            if (legacyFine.band() != FineBand.PETTY
                    || legacyFine.status() != FineStatus.UNPAID
                    || legacyFine.restitution().count() != 0L
                    || legacyFine.exileDays() != 0
                    || !legacyFine.victim().isEmpty()
                    || !legacyFine.collectedBy().isEmpty()
                    || legacyFine.selfPaid()) {
                failures.add("an older fine tag did not load with tolerant defaults");
            }
            ctx.checks++;
            final FineRecord fullFine = new FineRecord(
                    "f-startup-roundtrip", alice, officer.uuid(), "9.11", FineBand.AGGRAVATED,
                    GoldAmount.of(64L), GoldAmount.of(10L), "StartupCheck round trip",
                    now, 0L, FineStatus.PAID, now + 1L, "R-abcdef12", null, "",
                    7, "StartupCheck Victim", "StartupCheck Fine Officer", false);
            if (!fullFine.equals(FineRecord.read(fullFine.write(), null))) {
                failures.add("the fine record did not round-trip through its own encoding");
            }

            // Audit: every fine mutation is flagged and an entry can carry each action verb.
            final GovSavedData auditData = new GovSavedData();
            AuditService.append(auditData, null, "FINE_ISSUE", null, Map.of("fineId", "f-a"),
                    "StartupCheck issue audit");
            AuditService.append(auditData, null, "FINE_PAY", null, Map.of("fineId", "f-a"),
                    "StartupCheck pay audit");
            AuditService.append(auditData, null, "FINE_COLLECT", null, Map.of("fineId", "f-a"),
                    "StartupCheck collect audit");
            AuditService.append(auditData, null, "FINE_WAIVE", null, Map.of("fineId", "f-a"),
                    "StartupCheck waive audit");
            final Set<String> auditActions = new HashSet<>();
            for (final var entry : auditData.audit()) {
                auditActions.add(entry.action());
            }
            ctx.checks++;
            if (auditData.audit().size() != 4
                    || !auditActions.containsAll(
                            Set.of("FINE_ISSUE", "FINE_PAY", "FINE_COLLECT", "FINE_WAIVE"))) {
                failures.add("the audit log did not carry an entry for every fine mutation");
            }

    }
}
