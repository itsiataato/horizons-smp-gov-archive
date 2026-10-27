package com.horizonssmp.gov.server;

import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.OpCode;

final class GovStartupCheckNetworkPart2 {

    private GovStartupCheckNetworkPart2() {
    }

    static void run(final GovStartupCheckNetworkContext ctx) {
        try {
        for (final OpCode op : OpCode.values()) {
            ctx.checks++;
            if (OpCode.byName(op.name()).orElse(null) != op) {
                ctx.failures.add("op name does not round-trip: " + op.name());
            }
        }
        ctx.checks++;
        if (OpCode.byName("NOT_AN_OP").isPresent() || OpCode.byName("").isPresent()
                || OpCode.byName(null).isPresent()) {
            ctx.failures.add("op name parser accepted an unknown name");
        }

        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.OPEN_TAB)) {
            ctx.failures.add("OPEN_TAB has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.LAW_ARTICLE)) {
            ctx.failures.add("LAW_ARTICLE has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.JOB_LIST)) {
            ctx.failures.add("JOB_LIST has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.JOB_INFO)) {
            ctx.failures.add("JOB_INFO has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.JOB_CREATE)) {
            ctx.failures.add("JOB_CREATE has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.JOB_EDIT)) {
            ctx.failures.add("JOB_EDIT has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.JOB_DELETE)) {
            ctx.failures.add("JOB_DELETE has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.JOB_ASSIGN)) {
            ctx.failures.add("JOB_ASSIGN has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.LOOKUP_PERSON)) {
            ctx.failures.add("LOOKUP_PERSON has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.PERSON_SEARCH)) {
            ctx.failures.add("PERSON_SEARCH has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.NOTE_LIST)) {
            ctx.failures.add("NOTE_LIST has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.NOTE_ADD)) {
            ctx.failures.add("NOTE_ADD has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.NOTE_EDIT)) {
            ctx.failures.add("NOTE_EDIT has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.NOTE_DELETE)) {
            ctx.failures.add("NOTE_DELETE has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.FINE_LIST)) {
            ctx.failures.add("FINE_LIST has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.FINE_INFO)) {
            ctx.failures.add("FINE_INFO has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.FINE_ISSUE)) {
            ctx.failures.add("FINE_ISSUE has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.FINE_PAY)) {
            ctx.failures.add("FINE_PAY has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.FINE_COLLECT)) {
            ctx.failures.add("FINE_COLLECT has no handler registered");
        }
        ctx.checks++;
        if (!GovActionDispatcher.isRegistered(OpCode.FINE_WAIVE)) {
            ctx.failures.add("FINE_WAIVE has no handler registered");
        }

        // The audit flag: reads must not append to the log they read. A refused action is never
        // audited either, whatever the flag says.
        ctx.checks++;
        if (OpCode.OPEN_TAB.audited() || OpCode.JOB_LIST.audited() || OpCode.LAW_ARTICLE.audited()
                || OpCode.PERSON_SEARCH.audited() || OpCode.LOOKUP_PERSON.audited()
                || OpCode.NOTE_LIST.audited() || OpCode.AUDIT_PAGE.audited()
                || OpCode.AUDIT_INFO.audited() || OpCode.SEASON_STATUS.audited()) {
            ctx.failures.add("a read op is marked as audited");
        }
        ctx.checks++;
        if (!OpCode.AUDIT_EXPORT.audited() || !OpCode.ADMIN_EXPORT.audited()
                || !OpCode.SEASON_BEGIN.audited() || !OpCode.SEASON_COMMIT.audited()) {
            ctx.failures.add("a service-audited mutation is not marked as audited");
        }
        ctx.checks++;
        if (OpCode.AUDIT_EXPORT.dispatcherAudited()
                || OpCode.ADMIN_EXPORT.dispatcherAudited()
                || OpCode.SEASON_BEGIN.dispatcherAudited()
                || OpCode.SEASON_COMMIT.dispatcherAudited()) {
            ctx.failures.add("a service-audited op would also be audited by the dispatcher");
        }
        ctx.checks++;
        if (!OpCode.NOTE_ADD.audited() || !OpCode.NOTE_EDIT.audited() || !OpCode.NOTE_DELETE.audited()
                || !OpCode.JOB_ASSIGN.audited()) {
            ctx.failures.add("a mutation op is not marked as audited");
        }
        ctx.checks++;
        if (!OpCode.FINE_ISSUE.audited() || !OpCode.FINE_PAY.audited()
                || !OpCode.FINE_COLLECT.audited() || !OpCode.FINE_WAIVE.audited()) {
            ctx.failures.add("a fine mutation op is not marked as audited");
        }
        ctx.checks++;
        if (OpCode.FINE_LIST.audited() || OpCode.FINE_INFO.audited()) {
            ctx.failures.add("a fine read op is marked as audited");
        }
        ctx.checks++;
        if (GovCapabilities.requiredFor(OpCode.AUDIT_PAGE) != GovCapability.AUDIT_VIEW
                || GovCapabilities.requiredFor(OpCode.AUDIT_INFO) != GovCapability.AUDIT_VIEW
                || GovCapabilities.requiredFor(OpCode.AUDIT_EXPORT) != GovCapability.AUDIT_VIEW) {
            ctx.failures.add("an audit read or export does not require audit.view");
        }
        ctx.checks++;
        if (GovCapabilities.requiredFor(OpCode.ADMIN_EXPORT) != GovCapability.SEASON_ADMIN
                || GovCapabilities.requiredFor(OpCode.SEASON_STATUS) != GovCapability.SEASON_ADMIN
                || GovCapabilities.requiredFor(OpCode.SEASON_BEGIN) != GovCapability.SEASON_ADMIN
                || GovCapabilities.requiredFor(OpCode.SEASON_COMMIT) != GovCapability.SEASON_ADMIN) {
            ctx.failures.add("an admin export or season action does not require season.admin");
        }

        } catch (final RuntimeException e) {
            ctx.checks++;
            ctx.failures.add("network rule check threw: " + e);
        }
    }
}
