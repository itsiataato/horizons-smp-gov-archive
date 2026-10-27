package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.enums.AuditOutcome;
import com.horizonssmp.gov.common.records.AuditEntry;


final class GovSafetyAuditChecks {

    private GovSafetyAuditChecks() {
    }

    /** Audit reads: filters, pagination, detail, and the read-must-not-append rule. */
    static int checkAuditReads(final List<String> failures) {
        int checks = 0;
        final long now = System.currentTimeMillis();
        final GovSavedData data = new GovSavedData();
        data.seedIfNeeded(now, false);
        final UUID alice = new UUID(0L, 31L);
        final UUID bob = new UUID(0L, 32L);
        final UUID carol = new UUID(0L, 33L);

        final AuditEntry accepted = AuditService.appendOutcome(
                data, alice, "Alice", "police", "NOTE_ADD", AuditOutcome.ACCEPTED,
                bob, "Bob", "text=hello", "a note");
        final AuditEntry refused = AuditService.appendOutcome(
                data, bob, "Bob", "citizen", "SEASON_COMMIT", AuditOutcome.REFUSED,
                null, "", "token=invalid", "no token");
        AuditService.appendOutcome(
                data, null, "", "", "SEED_JOBS", AuditOutcome.ACCEPTED,
                null, "", "", "seed");

        checks++;
        if (data.audit().size() != 3) {
            failures.add("audit fixture did not append three lines");
        }
        checks++;
        final AuditEntry roundTrip = AuditEntry.read(accepted.write(), 99L);
        if (!roundTrip.equals(accepted) || roundTrip.outcome() != AuditOutcome.ACCEPTED) {
            failures.add("audit entry did not round-trip through NBT with its outcome");
        }

        final int before = data.audit().size();
        final AuditService.Filter all = AuditService.filter("", "", "", "", "7d", now);
        final AuditService.Page page = AuditService.page(data, all, 0);
        checks++;
        if (page.rows().size() > AuditKeys.PAGE_SIZE || page.rows().size() != 3) {
            failures.add("audit page size or row count is wrong");
        }
        checks++;
        if (data.audit().size() != before) {
            failures.add("audit list read appended to the log it reads");
        }
        checks++;
        if (!page.rows().get(0).action().equals("SEED_JOBS")) {
            failures.add("audit page is not newest-first");
        }

        checks++;
        if (AuditService.page(data, AuditService.filter(
                "ali", "", "", "", "7d", now), 0).matching() != 1) {
            failures.add("audit actor filter did not match Alice");
        }
        checks++;
        if (AuditService.page(data, AuditService.filter(
                "", "note_add", "", "", "7d", now), 0).matching() != 1) {
            failures.add("audit action filter is not case-insensitive");
        }
        checks++;
        if (AuditService.page(data, AuditService.filter(
                "", "", "bo", "", "7d", now), 0).matching() != 1) {
            failures.add("audit target filter did not match Bob");
        }
        checks++;
        if (AuditService.page(data, AuditService.filter(
                "", "", "", "refused", "7d", now), 0).matching() != 1) {
            failures.add("audit outcome filter did not match the refusal");
        }
        checks++;
        if (AuditService.page(data, AuditService.filter(
                "", "", "", "", "nonsense", now), 0).matching() != 3) {
            failures.add("unknown audit window did not default to the bounded window");
        }
        checks++;
        if (AuditService.page(data, AuditService.filter(
                "", "", "", "", "all", now), 99).page() != 0) {
            failures.add("audit page did not clamp to the last real page");
        }

        final Map<String, String> fields =
                AuditService.listFields(data, all, 0, EnumSet.of(
                        GovCapability.AUDIT_VIEW, GovCapability.SEASON_ADMIN));
        checks++;
        if (!"3".equals(fields.get(AuditKeys.COUNT))
                || !"3".equals(fields.get(AuditKeys.MATCHING))
                || !"true".equals(fields.get(AuditKeys.CAN_EXPORT))
                || !"true".equals(fields.get(AuditKeys.CAN_ADMIN))) {
            failures.add("audit list header fields are incomplete");
        }
        checks++;
        if (Long.parseLong(fields.get(AuditKeys.OLDEST_AT)) <= 0L
                || Long.parseLong(fields.get(AuditKeys.NEWEST_AT)) <= 0L) {
            failures.add("audit list header lost the oldest/newest timestamps");
        }

        final Map<String, String> detail = AuditService.detailFields(refused);
        checks++;
        if (!"REFUSED".equals(detail.get(AuditKeys.DETAIL_OUTCOME))
                || !detail.containsKey(AuditKeys.PAYLOAD)
                || !detail.containsKey(AuditKeys.REASON)
                || !detail.containsKey(AuditKeys.ACTOR_UUID)
                || !detail.containsKey(AuditKeys.TARGET_UUID)
                || !detail.containsKey(AuditKeys.ACTOR_JOB)) {
            failures.add("audit detail block is missing a field");
        }
        checks++;
        if (AuditService.bySeq(data, refused.seq()) == null) {
            failures.add("audit detail lookup by sequence failed");
        }
        checks++;
        if (AuditService.bySeq(data, -1L) != null) {
            failures.add("audit detail lookup accepted a negative sequence");
        }
        checks++;
        if (AuditOutcome.parse("unknown") != AuditOutcome.ACCEPTED) {
            failures.add("unknown stored outcome did not default to ACCEPTED");
        }
        return checks;
    }
}
