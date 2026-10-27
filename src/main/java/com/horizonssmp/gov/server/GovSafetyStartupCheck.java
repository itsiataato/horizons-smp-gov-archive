package com.horizonssmp.gov.server;

import java.util.List;

/**
 * The M9 safety contract check: rate-limit coverage, hostile-input hardening, audit reads, safe
 * exports and the two-step season reset.
 *
 * <p>Everything here runs against scratch data and a scratch directory under the build tree. The
 * live {@code gov.dat} is only read: the check captures its counts before and after and fails if
 * any of them moved. That is what lets the smoke gate prove a full begin/commit reset without
 * touching the live world.
 *
 * <p>The hostile corpus is deliberately large and deterministic. Each case is one assertion; a
 * parser that throws fails the boot with the exact value in the message. The cases cover control
 * characters, unicode, lone surrogates, oversized strings, path traversal, numeric overflow, NaN
 * and Infinity, unknown enum ids and empty/null input. Every case must return a clean refusal or a
 * documented safe default.
 */
public final class GovSafetyStartupCheck {

    private static final int MIN_CORPUS_CASES = 200;

    private GovSafetyStartupCheck() {
    }

    /** Runs the M9 checks. Never throws; returns the number of assertions. */
    public static int run(final GovSavedData live, final List<String> failures) {
        final int livePlayers = live == null ? 0 : live.players().size();
        final int liveAudit = live == null ? 0 : live.audit().size();
        final int liveFines = live == null ? 0 : live.fines().size();
        final int liveNotes = live == null ? 0 : live.notes().size();
        final int liveVehicles = live == null ? 0 : live.vehicles().size();

        int checks = 0;
        checks += GovSafetyRateLimitChecks.checkRatePolicies(failures);
        checks += GovSafetyRateLimitChecks.checkRateLimiterBoundaries(failures);
        checks += GovSafetyFuzzChecks.checkFuzzCorpus(failures);
        checks += GovSafetyUnicodeChecks.checkUnicodeWellFormed(failures);
        checks += GovSafetyDispatcherChecks.checkDispatcherBoundary(failures);
        checks += GovSafetyAuditChecks.checkAuditReads(failures);
        checks += GovSafetyExportChecks.checkExportSafety(failures);
        checks += GovSafetyDataHardeningChecks.checkDataHardening(failures);
        checks += GovSafetySeasonChecks.checkSeasonReset(failures);

        checks++;
        if (live != null && (live.players().size() != livePlayers
                || live.audit().size() != liveAudit
                || live.fines().size() != liveFines
                || live.notes().size() != liveNotes
                || live.vehicles().size() != liveVehicles)) {
            failures.add("M9 self-check changed the live government data");
        }
        return checks;
    }
}
