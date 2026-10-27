package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.OpCode;

final class GovStartupCheckNetworkPart3 {

    private GovStartupCheckNetworkPart3() {
    }

    static void run(final GovStartupCheckNetworkContext ctx) {
        try {
        // The M3 authority matrix: the note reads need note.view.all, the note writes need
        // note.write, and people lookup needs player.lookup. Asserted by id, so the check names
        // the capability a reviewer expects.
        ctx.checks++;
        if (GovCapabilities.requiredFor(OpCode.PERSON_SEARCH) != GovCapability.PLAYER_LOOKUP
                || GovCapabilities.requiredFor(OpCode.LOOKUP_PERSON) != GovCapability.PLAYER_LOOKUP) {
            ctx.failures.add("people lookup does not require player.lookup");
        }
        ctx.checks++;
        if (GovCapabilities.requiredFor(OpCode.NOTE_LIST) != GovCapability.NOTE_VIEW_ALL) {
            ctx.failures.add("NOTE_LIST does not require note.view.all");
        }
        ctx.checks++;
        if (GovCapabilities.requiredFor(OpCode.NOTE_ADD) != GovCapability.NOTE_WRITE
                || GovCapabilities.requiredFor(OpCode.NOTE_EDIT) != GovCapability.NOTE_WRITE
                || GovCapabilities.requiredFor(OpCode.NOTE_DELETE) != GovCapability.NOTE_WRITE) {
            ctx.failures.add("a note mutation does not require note.write");
        }

        // The M4 authority matrix: issue needs fine.issue, waive and collect need the reception
        // fine.waive authority, and the fine reads plus a self-payment are baseline.
        ctx.checks++;
        if (GovCapabilities.requiredFor(OpCode.FINE_ISSUE) != GovCapability.FINE_ISSUE) {
            ctx.failures.add("FINE_ISSUE does not require fine.issue");
        }
        ctx.checks++;
        if (GovCapabilities.requiredFor(OpCode.FINE_WAIVE) != GovCapability.FINE_WAIVE
                || GovCapabilities.requiredFor(OpCode.FINE_COLLECT) != GovCapability.FINE_WAIVE) {
            ctx.failures.add("fine waive/collect does not require fine.waive");
        }
        ctx.checks++;
        if (GovCapabilities.requiredFor(OpCode.FINE_LIST) != GovCapability.PLAYER_LOOKUP_SELF
                || GovCapabilities.requiredFor(OpCode.FINE_INFO) != GovCapability.PLAYER_LOOKUP_SELF
                || GovCapabilities.requiredFor(OpCode.FINE_PAY) != GovCapability.PLAYER_LOOKUP_SELF) {
            ctx.failures.add("a fine read or self-payment is not reachable by a baseline player");
        }

        // Both read actions must be reachable by a brand new player: the baseline capability set
        // is what every player has before any job is assigned.
        ctx.checks++;
        if (!GovCapabilities.BASELINE.contains(GovCapabilities.requiredFor(OpCode.OPEN_TAB))) {
            ctx.failures.add("OPEN_TAB is not a baseline capability, so a new player cannot open their card");
        }
        ctx.checks++;
        if (!GovCapabilities.BASELINE.contains(GovCapabilities.requiredFor(OpCode.LAW_ARTICLE))) {
            ctx.failures.add("LAW_ARTICLE is not a baseline capability, so a new player cannot read the law");
        }
        ctx.checks++;
        if (!GovCapabilities.BASELINE.contains(GovCapabilities.requiredFor(OpCode.JOB_LIST))) {
            ctx.failures.add("JOB_LIST is not a baseline capability, so a new player cannot open the Jobs tab");
        }
        ctx.checks++;
        if (!GovCapabilities.BASELINE.contains(GovCapabilities.requiredFor(OpCode.JOB_INFO))) {
            ctx.failures.add("JOB_INFO is not a baseline capability, so a new player cannot open the Jobs tab");
        }
        } catch (final RuntimeException e) {
            ctx.checks++;
            ctx.failures.add("network rule check threw: " + e);
        }
    }
}
