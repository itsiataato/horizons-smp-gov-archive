package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.enums.DutyState;
import com.horizonssmp.gov.common.records.EmploymentRecord;
import com.horizonssmp.gov.common.records.JailRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.common.records.SuspectRecord;

final class GovStartupCheckSystemDefaultJob {

    private GovStartupCheckSystemDefaultJob() {
    }

    static int run(final List<String> failures) {
        int checks = 0;
        try {
            final long now = System.currentTimeMillis();
            final GovSavedData scratch = new GovSavedData();
            scratch.seedIfNeeded(now, false);

            final JobService.Actor system = new JobService.Actor(
                    null, "server", GovCapabilities.BASELINE);
            final UUID newcomer = new UUID(0L, 9701L);
            final UUID usurper = new UUID(0L, 9702L);

            checks++;
            final JobService.Outcome citizen = JobService.assign(
                    scratch, system, newcomer, "StartupCheck Newcomer",
                    GovCapabilities.CITIZEN_JOB_ID, now);
            final PlayerRecord record = scratch.player(newcomer);
            if (!citizen.ok() || record == null || !record.jobDecided()) {
                failures.add("the system actor could not assign the citizen default to a fresh player");
            }

            checks++;
            final JobService.Outcome executive = JobService.assign(
                    scratch, system, usurper, "StartupCheck Usurper",
                    GovCapabilities.GOVEXECUTIVE_JOB_ID, now);
            if (executive.code() != ErrorCode.FORBIDDEN) {
                failures.add("the system actor was not refused the executive job");
            }

            checks++;
            // WHY: a file written before the marker existed can hold its job only in the
            // denormalized employment block, with an empty primary job id. Marking it decided must
            // carry that block through untouched; withJob would collapse it to NONE and wipe the
            // job and every capability derived from it.
            final UUID legacyPlayer = new UUID(0L, 9703L);
            final long legacySince = now - 86_400_000L;
            final PlayerRecord legacy = new PlayerRecord(
                    legacyPlayer,
                    "StartupCheck Legacy",
                    now,
                    now,
                    "",
                    0L,
                    null,
                    "",
                    EnumSet.noneOf(GovCapability.class),
                    SuspectRecord.NONE,
                    DutyState.N_A,
                    new EmploymentRecord(
                            GovCapabilities.POLICE_JOB_ID, "Officer", legacySince, legacyPlayer),
                    JailRecord.NONE,
                    false);
            final PlayerRecord decided = legacy.withJobDecided(true);
            if (!decided.jobDecided()
                    || !decided.primaryJobId().isEmpty()
                    || !decided.employment().employed()
                    || !GovCapabilities.POLICE_JOB_ID.equals(GovCapabilities.jobIdOf(decided))) {
                failures.add("marking a legacy file decided without a primary job id lost the "
                        + "employment-block job: decided=" + decided.jobDecided()
                        + " primary='" + decided.primaryJobId() + "'"
                        + " employment='" + decided.employment().jobId() + "'"
                        + " effective='" + GovCapabilities.jobIdOf(decided) + "'");
            }
        } catch (final RuntimeException e) {
            checks++;
            failures.add("system default job check threw: " + e);
        }
        return checks;
    }

    /**
     * The M3 person and note rules, exercised against a scratch data set.
     *
     * <p>A scratch set, not the live one: the check writes notes, edits them, deletes them and
     * fills a file to its cap, and none of that may reach {@code gov.dat}. The synthetic records
     * are named {@code StartupCheck ...} and use UUIDs from the reserved {@code 0x3000} range, so
     * anything that ever leaked into a log or a file identifies itself (tip t41).
     *
     * <p>The dispatcher needs a {@code ServerPlayer}, so this exercises the service layer the
     * handlers call, with a synthetic actor; handler registration and the capability mapping are
     * asserted in {@link #checkNetwork}.
     */

}
