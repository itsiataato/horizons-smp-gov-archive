package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.NbtSupport;
import com.horizonssmp.gov.common.records.PlayerRecord;
import net.minecraft.nbt.CompoundTag;

final class GovStartupCheckJobsPart2 {

    private GovStartupCheckJobsPart2() {
    }

    static void run(final GovStartupCheckJobsContext ctx) {
        final List<String> failures = ctx.failures;
        final long now = ctx.now;
        final GovSavedData scratch = ctx.scratch;
        final JobService.Actor manager = ctx.manager;
        final UUID alice = ctx.alice;
        final UUID bob = ctx.bob;
        final UUID carol = ctx.carol;
        final UUID dave = ctx.dave;

            // Assign, replace (never stack), refuse a duplicate, unassign.
            ctx.checks++;
            if (!JobService.assign(scratch, manager, alice, "Alice", GovCapabilities.POLICE_JOB_ID, now).ok()) {
                failures.add("assigning police to Alice failed");
            }
            final PlayerRecord aliceRecord = scratch.player(alice);
            ctx.checks++;
            if (aliceRecord == null || !GovCapabilities.POLICE_JOB_ID.equals(aliceRecord.primaryJobId())) {
                failures.add("the assigned primary job is not police");
            }
            ctx.checks++;
            if (aliceRecord != null
                    && !GovCapabilities.POLICE_JOB_ID.equals(aliceRecord.employment().jobId())) {
                failures.add("the employment block disagrees with the primary job");
            }
            ctx.checks++;
            if (aliceRecord != null && !"Officer".equals(aliceRecord.rank())) {
                failures.add("the assigned rank is not the job's rank label");
            }
            ctx.checks++;
            if (aliceRecord != null && !aliceRecord.jobDecided()) {
                failures.add("assignment did not mark the job decided");
            }
            ctx.checks++;
            if (JobService.holderCount(scratch, GovCapabilities.POLICE_JOB_ID) != 1) {
                failures.add("the police member count is not 1 after the assignment");
            }
            ctx.checks++;
            final int historyBeforeSameAssign = scratch.historyFor(alice).size();
            if (!JobService.assign(scratch, manager, alice, "Alice",
                    GovCapabilities.POLICE_JOB_ID, now).ok()
                    || scratch.historyFor(alice).size() != historyBeforeSameAssign) {
                failures.add("re-assigning the same job was not an idempotent no-op");
            }
            ctx.checks++;
            if (!JobService.assign(scratch, manager, alice, "Alice", GovCapabilities.JUDGE_JOB_ID, now).ok()) {
                failures.add("replacing the job with judge failed");
            }
            final PlayerRecord replaced = scratch.player(alice);
            ctx.checks++;
            if (replaced != null && (!GovCapabilities.JUDGE_JOB_ID.equals(replaced.primaryJobId())
                    || !GovCapabilities.JUDGE_JOB_ID.equals(replaced.employment().jobId()))) {
                failures.add("replacing a job stacked instead of replacing");
            }
            ctx.checks++;
            if (JobService.holderCount(scratch, GovCapabilities.POLICE_JOB_ID) != 0) {
                failures.add("the replaced job still counts the player as a member");
            }
            ctx.checks++;
            if (JobService.delete(scratch, GovCapabilities.JUDGE_JOB_ID).ok()) {
                failures.add("a job in use was deleted");
            }
            ctx.checks++;
            if (!JobService.assign(scratch, manager, alice, "Alice", "", now).ok()) {
                failures.add("unassigning Alice failed");
            }
            final PlayerRecord cleared = scratch.player(alice);
            ctx.checks++;
            if (cleared != null && (cleared.employed() || cleared.employment().employed()
                    || !cleared.jobDecided())) {
                failures.add("unassign did not clear the job while keeping the decision marker");
            }
            ctx.checks++;
            if (JobService.assign(scratch, manager, alice, "Alice", "", now).ok()) {
                failures.add("unassigning an already unemployed player was accepted");
            }
            ctx.checks++;
            if (!JobService.delete(scratch, GovCapabilities.JUDGE_JOB_ID).ok()) {
                failures.add("an unused template could not be deleted");
            }
            ctx.checks++;
            if (scratch.job(GovCapabilities.JUDGE_JOB_ID) != null) {
                failures.add("the deleted template is still present");
            }

            // Self-promotion: no new authority for yourself, but no new authority means no objection.
            final JobService.Actor bobActor =
                    new JobService.Actor(bob, "Bob", GovCapabilities.BASELINE);
            ctx.checks++;
            if (JobService.assign(scratch, bobActor, bob, "Bob", GovCapabilities.GOVEXECUTIVE_JOB_ID, now).code()
                    != ErrorCode.SELF_PROMOTION) {
                failures.add("self-promotion was not refused");
            }
            final JobService.Actor carolActor =
                    new JobService.Actor(carol, "Carol", EnumSet.allOf(GovCapability.class));
            ctx.checks++;
            if (!JobService.assign(scratch, carolActor, carol, "Carol",
                    GovCapabilities.GOVEXECUTIVE_JOB_ID, now).ok()) {
                failures.add("an executive could not assign themselves the executive job");
            }

            // The last executive cannot be removed by an assignment or by a capability edit.
            ctx.checks++;
            if (!JobService.assign(scratch, carolActor, dave, "Dave",
                    GovCapabilities.GOVEXECUTIVE_JOB_ID, now).ok()) {
                failures.add("could not assign a second executive");
            }
            ctx.checks++;
            if (!JobService.assign(scratch, carolActor, carol, "Carol", "", now).ok()) {
                failures.add("demoting one of two executives was refused");
            }
            ctx.checks++;
            if (JobService.assign(scratch, carolActor, dave, "Dave", "", now).code()
                    != ErrorCode.LAST_EXEC) {
                failures.add("removing the last executive was not refused");
            }
            ctx.checks++;
            if (JobService.editCapability(scratch, manager, GovCapabilities.GOVEXECUTIVE_JOB_ID,
                    "job.manage", false).code() != ErrorCode.LAST_EXEC) {
                failures.add("stripping job.manage from the executive job while it is held was not refused");
            }

            // Tolerant read: an unknown capability id in a stored job is dropped, never fatal.
            // The synthetic id names this check, so the drop warning in the boot log cannot be
            // mistaken for a damaged world file.
            ctx.checks++;
            final CompoundTag legacyTag = new CompoundTag();
            legacyTag.putString("id", "startup-check-legacy");
            NbtSupport.putStringList(legacyTag, "capabilities",
                    List.of("fine.issue", "not.a.capability", "fine.issue"));
            final JobRecord legacy = JobRecord.read(legacyTag, null);
            if (!legacy.capabilities().equals(EnumSet.of(GovCapability.FINE_ISSUE))) {
                failures.add("an unknown capability id was not dropped on read");
            }

            // The M2 records survive their own encoding, including the job-decided marker.
            ctx.checks++;
            final PlayerRecord playerRoundTrip = PlayerRecord.blank(alice, "Alice", now)
                    .withJob(GovCapabilities.POLICE_JOB_ID, "Officer", now, manager.uuid(), true);
            if (!playerRoundTrip.equals(PlayerRecord.read(playerRoundTrip.write(), null))) {
                failures.add("player record did not round-trip through its own encoding");
            }
            ctx.checks++;
            final JobRecord jobRoundTrip = new JobRecord(
                    "roundtrip",
                    "Round Trip",
                    "#112233",
                    "Department",
                    "Rank",
                    false,
                    EnumSet.of(GovCapability.FINE_ISSUE, GovCapability.JOB_VIEW),
                    now,
                    manager.uuid());
            if (!jobRoundTrip.equals(JobRecord.read(jobRoundTrip.write(), null))) {
                failures.add("job record did not round-trip through its own encoding");
            }
    }
}
