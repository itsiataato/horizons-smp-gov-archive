package com.horizonssmp.gov.server.economy;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.JobKeys;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.PlayerRecord;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovSavedDataSeeds;
import com.horizonssmp.gov.server.JobService;

/**
 * TASK-033 (FR-3, FR-6) builder built-in job tests.
 *
 * <p>Acceptance criteria: {@code Builder} exists as a built-in seeded job with the baseline
 * bundle, existing worlds gain it idempotently on boot, and the contract lifecycle refuses
 * anyone who does not hold the builder job.
 *
 * <p>Runs headless: records live in a bare {@code new GovSavedData()}, seeding runs with
 * {@code announce=false} so no logger path is touched, and no test touches
 * {@code FMLEnvironment} or the config spec (see tips/t08). Lives in the economy package
 * because {@code ContractSupport} is package-private. Everything asserted here executes real
 * production bytecode: the real {@code GovSavedDataSeeds.seedIfNeeded}, the real capability
 * table, the real {@code builderEligible} choke point, and the real roster classifier.
 *
 * <p>Honest limits, all marked at the test: the post/accept/submit/approve refusal sentences
 * and the escrow movement need live actors, inventories, regions and a server, so the full
 * lifecycle is proven in-game (Phase 10); what is proven headless is the gate every one of
 * those steps funnels through. The boot-order proof (seed line 82 before check line 163 in
 * {@code GovServerLifecycle}) is a code reading, not an execution. The full startup check
 * runs at boot; this pins the same properties it asserts without calling its
 * package-private runner.
 */
final class GovBuilderJobTest {

    private static final long NOW = 1_700_000_000_000L;

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static GovSavedData freshSeeded() {
        final GovSavedData data = new GovSavedData();
        GovSavedDataSeeds.seedIfNeeded(data, NOW, false);
        return data;
    }

    private static UUID playerWithJob(
            final GovSavedData data, final String name, final String jobId) {
        final UUID uuid = UUID.randomUUID();
        PlayerRecord record = PlayerRecord.blank(uuid, name, NOW);
        if (jobId != null) {
            record = record.withJob(jobId, jobId, NOW, null, true);
        }
        data.putPlayer(record);
        return uuid;
    }

    void testSeedContainsBuilder() {
        final GovSavedData data = freshSeeded();
        final JobRecord builder = data.job(GovCapabilities.BUILDER_JOB_ID);
        check(builder != null, "fresh seed must contain the builder job");
        check("builder".equals(builder.id()), "builder job id must stay builder");
        check(!builder.system(), "builder must be deletable (system=false), like judge");
        check(builder.createdBy() == null, "builder must be a seed template (no creator)");
        check(JobKeys.TYPE_TEMPLATE.equals(JobService.typeOf(builder)),
                "builder roster type must be template, got " + JobService.typeOf(builder));
        check(builder.capabilities().equals(
                        EnumSet.of(GovCapability.PLAYER_LOOKUP_SELF, GovCapability.LAW_VIEW)),
                "builder capabilities must be the matrix set, got " + builder.capabilities());
        check(!builder.capabilities().contains(GovCapability.BANK_MANAGE),
                "builder must not hold bank.manage: posting stays the treasury authority");
        check(!builder.capabilities().contains(GovCapability.SEASON_ADMIN),
                "builder must not hold season.admin: TASK-032 credit gate stays executive-only");
    }

    void testBundlePinsMatrix() {
        final Set<GovCapability> bundle =
                GovCapabilities.builtinJobCapabilities(GovCapabilities.BUILDER_JOB_ID);
        check(bundle.equals(
                        EnumSet.of(GovCapability.PLAYER_LOOKUP_SELF, GovCapability.LAW_VIEW)),
                "builtin bundle must be baseline-only, got " + bundle);
        check(GovCapabilities.builtinJobCapabilities(null).isEmpty(),
                "null job id must map to an empty bundle");
        check(GovCapabilities.builtinJobCapabilities("no-such-job").isEmpty(),
                "unknown job id must map to an empty bundle");
        check(GovCapabilities.builtinJobCapabilities(GovCapabilities.GOVEXECUTIVE_JOB_ID)
                        .contains(GovCapability.SEASON_ADMIN),
                "TASK-032 guard: executive must still hold season.admin");
    }

    void testEligibilityRequiresBuilderJob() {
        final GovSavedData data = freshSeeded();
        final UUID builder = playerWithJob(data, "Bea", GovCapabilities.BUILDER_JOB_ID);
        final UUID worker = playerWithJob(data, "Wen", GovCapabilities.GOVWORKER_JOB_ID);
        final UUID citizen = playerWithJob(data, "Cid", GovCapabilities.CITIZEN_JOB_ID);
        final UUID executive = playerWithJob(data, "Exe", GovCapabilities.GOVEXECUTIVE_JOB_ID);
        final UUID judge = playerWithJob(data, "Jud", GovCapabilities.JUDGE_JOB_ID);
        final UUID unemployed = playerWithJob(data, "Una", null);
        check(ContractSupport.builderEligible(data, builder),
                "a builder-job holder must be eligible");
        check(!ContractSupport.builderEligible(data, worker),
                "a govworker must be refused, even with bank.manage");
        check(!ContractSupport.builderEligible(data, citizen),
                "a citizen must be refused without the builder job");
        check(!ContractSupport.builderEligible(data, executive),
                "an executive must be refused: bank.manage never qualifies anyone to build");
        check(!ContractSupport.builderEligible(data, judge),
                "a judge must be refused without the builder job");
        check(!ContractSupport.builderEligible(data, unemployed),
                "an unemployed player must be refused");
        check(!ContractSupport.builderEligible(data, UUID.randomUUID()),
                "an unknown UUID must be refused");
        check(!ContractSupport.builderEligible(data, null),
                "a null builder must be refused, never throw");
        check(!ContractSupport.builderEligible(null, builder),
                "a null data set must be refused, never throw");
    }

    void testMigrationRolloutIdempotent() {
        final GovSavedData data = freshSeeded();
        check(data.job(GovCapabilities.BUILDER_JOB_ID) != null,
                "precondition: fresh seed has the builder");
        check(data.removeJob(GovCapabilities.BUILDER_JOB_ID),
                "builder must be deletable like any template");
        check(data.job(GovCapabilities.BUILDER_JOB_ID) == null,
                "precondition: builder removed, simulating a pre-TASK-033 world");
        final int rolled = GovSavedDataSeeds.seedIfNeeded(data, NOW + 1L, false);
        check(rolled == 1, "migration must write exactly the missing builder, got " + rolled);
        check(data.job(GovCapabilities.BUILDER_JOB_ID) != null,
                "existing worlds must gain the builder on boot");
        final int again = GovSavedDataSeeds.seedIfNeeded(data, NOW + 2L, false);
        check(again == 0, "second seed must be a no-op, got " + again);
        check(data.job(GovCapabilities.BUILDER_JOB_ID) != null,
                "builder must survive a re-seed");
        final GovSavedData fresh = freshSeeded();
        check(GovSavedDataSeeds.seedIfNeeded(fresh, NOW + 3L, false) == 0,
                "a fresh world re-seeded must report no writes");
    }

    void testRefusalMessageShape() {
        check(ContractSupport.BUILDER_REQUIRED != null
                        && !ContractSupport.BUILDER_REQUIRED.isBlank(),
                "the refusal sentence must be present");
        check(ContractSupport.BUILDER_REQUIRED.toLowerCase().contains("builder"),
                "the refusal must name the builder job, got: "
                        + ContractSupport.BUILDER_REQUIRED);
    }

    public static void main(final String[] args) {
        final GovBuilderJobTest t = new GovBuilderJobTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testSeedContainsBuilder", "testBundlePinsMatrix",
                "testEligibilityRequiresBuilderJob", "testMigrationRolloutIdempotent",
                "testRefusalMessageShape"};
        final Runnable[] tests = {
                t::testSeedContainsBuilder, t::testBundlePinsMatrix,
                t::testEligibilityRequiresBuilderJob, t::testMigrationRolloutIdempotent,
                t::testRefusalMessageShape};
        for (int i = 0; i < names.length; i++) {
            try {
                tests[i].run();
                passed++;
                System.out.println("PASS " + names[i]);
            } catch (final Throwable e) {
                failed++;
                System.out.println("FAIL " + names[i] + ": " + e);
                e.printStackTrace(System.out);
            }
        }
        System.out.println("\nResults: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
