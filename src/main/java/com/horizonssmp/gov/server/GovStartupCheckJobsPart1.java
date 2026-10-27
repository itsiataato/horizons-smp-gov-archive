package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.JobKeys;
import com.horizonssmp.gov.common.records.JobRecord;

final class GovStartupCheckJobsPart1 {

    private GovStartupCheckJobsPart1() {
    }

    static void run(final GovStartupCheckJobsContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final JobService.Actor manager = ctx.manager;
        final UUID alice = ctx.alice;

            // A custom job: created with an empty set, typed custom, caps only through edits.
            ctx.checks++;
            if (!JobService.create(scratch, manager, "Harbor Patrol", now).ok()) {
                failures.add("could not create a custom job");
            }
            final JobRecord custom = scratch.job("harbor_patrol");
            ctx.checks++;
            if (custom == null || !JobKeys.TYPE_CUSTOM.equals(JobService.typeOf(custom))) {
                failures.add("a created job is not classified as custom");
            }
            ctx.checks++;
            if (custom != null && !custom.capabilities().isEmpty()) {
                failures.add("a new job did not start with an empty capability set");
            }
            ctx.checks++;
            if (JobService.create(scratch, manager, "Harbor Patrol", now).ok()) {
                failures.add("a duplicate job id was accepted");
            }
            ctx.checks++;
            if (JobService.create(scratch, manager, "!!!", now).ok()) {
                failures.add("a job name with no usable id characters was accepted");
            }

            ctx.checks++;
            if (!JobService.editCapability(scratch, manager, "harbor_patrol", "fine.issue", true).ok()) {
                failures.add("adding a capability to a custom job failed");
            }
            ctx.checks++;
            final JobRecord withCap = scratch.job("harbor_patrol");
            if (withCap == null || !withCap.capabilities().contains(GovCapability.FINE_ISSUE)) {
                failures.add("the added capability is not on the job");
            }
            ctx.checks++;
            if (JobService.editCapability(scratch, manager, "harbor_patrol", "fine.issue", true).ok()) {
                failures.add("adding the same capability twice was accepted");
            }
            ctx.checks++;
            if (!JobService.editCapability(scratch, manager, "harbor_patrol", "fine.issue", false).ok()) {
                failures.add("removing a capability failed");
            }
            ctx.checks++;
            if (scratch.job("harbor_patrol").capabilities().contains(GovCapability.FINE_ISSUE)) {
                failures.add("the removed capability is still on the job");
            }
            ctx.checks++;
            if (JobService.editCapability(scratch, manager, "harbor_patrol", "not.a.capability", true).ok()) {
                failures.add("an unknown capability id was accepted at write time");
            }
            ctx.checks++;
            if (JobService.parseCapability("FINE.ISSUE").orElse(null) != GovCapability.FINE_ISSUE) {
                failures.add("the capability parser is not case tolerant");
            }

            // A non-built-in can be renamed; the id is the key and never changes.
            ctx.checks++;
            if (!JobService.rename(scratch, "harbor_patrol", "Harbor Patrol Division").ok()) {
                failures.add("renaming a custom job failed");
            }
            ctx.checks++;
            final JobRecord renamed = scratch.job("harbor_patrol");
            if (renamed == null || !"Harbor Patrol Division".equals(renamed.displayName())) {
                failures.add("the renamed display name is not on the job");
            }
            ctx.checks++;
            if (scratch.job("harbor_patrol_division") != null) {
                failures.add("a rename changed the job id instead of the display name");
            }

            // Built-in protection: no delete, no rename, capability edits allowed.
            ctx.checks++;
            if (JobService.delete(scratch, GovCapabilities.POLICE_JOB_ID).ok()) {
                failures.add("a built-in job was deleted");
            }
            ctx.checks++;
            if (JobService.rename(scratch, GovCapabilities.POLICE_JOB_ID, "Constabulary").ok()) {
                failures.add("a built-in job was renamed");
            }
            ctx.checks++;
            if (!JobService.editCapability(scratch, manager, GovCapabilities.POLICE_JOB_ID, "audit.view", true).ok()) {
                failures.add("a built-in capability set could not be edited through job.manage");
            }

            // Unknown jobs are refused with the stable code, everywhere.
            ctx.checks++;
            if (JobService.delete(scratch, "nosuchjob").code() != ErrorCode.BAD_JOB) {
                failures.add("deleting an unknown job did not report BAD_JOB");
            }
            ctx.checks++;
            if (JobService.rename(scratch, "nosuchjob", "x").code() != ErrorCode.BAD_JOB) {
                failures.add("renaming an unknown job did not report BAD_JOB");
            }
            ctx.checks++;
            if (JobService.assign(scratch, manager, alice, "Alice", "nosuchjob", now).code()
                    != ErrorCode.BAD_JOB) {
                failures.add("assigning an unknown job did not report BAD_JOB");
            }


    }
}
