package com.horizonssmp.gov.server;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.server.economy.AccountService;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * TASK-008 Cycle 1 hardening proofs: least-privilege job edits, debit-first bounty
 * escrow, null-safe saved-data accessors and sanitized console cell-name echoes.
 *
 * <p>Acceptance criteria: adding a capability the actor does not hold is {@code FORBIDDEN}
 * (mirroring the assignment guard); a null actor is {@code INVALID_STATE}, never a throw;
 * a held add succeeds and a remove needs no hold; a failed escrow debit creates nothing
 * and a debit-first payout conserves the total; {@code getIfPresent(null)} and
 * {@code get((ServerLevel) null)} return null, never a throw; and {@code Sanitizer.name}
 * (the console branch echo) strips formatting codes and control characters.
 *
 * <p>Runs headless: records live in a bare {@code new GovSavedData()}, the ledger
 * assertions execute the real {@code AccountService} debit/credit bytecode, and no test
 * touches {@code FMLEnvironment} or the config spec (see tips/t08). Lives in the server
 * package. Honest limits: full bounty payout/cancel gold movement needs a live
 * {@code ServerPlayer}, vault inventory and server storage, so end-to-end money creation
 * is proven in-game (Phase 10); the unknown-op {@code RATE_LIMITED} refusal needs the
 * NeoForge network channel, so it is proven in-game too.
 */
final class GovHardeningCycle1Test {

    private static final long NOW = 1_700_000_000_000L;
    private static final UUID MANAGER_ID = new UUID(0L, 1001L);
    private static final String GOV = GovSavedData.GOVERNMENT_ACCOUNT_ID;

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static JobService.Actor actorWith(final GovCapability... held) {
        final Set<GovCapability> caps = held.length == 0
                ? EnumSet.noneOf(GovCapability.class)
                : EnumSet.copyOf(Set.of(held));
        return new JobService.Actor(MANAGER_ID, "Manager", caps);
    }

    private static GovSavedData withCustomJob() {
        final GovSavedData data = new GovSavedData();
        final JobService.Outcome created = JobService.create(
                data, actorWith(GovCapability.JOB_MANAGE), "Harbor Patrol", NOW);
        check(created.ok(), "setup: custom job must be created, got " + created);
        return data;
    }

    void testUnheldAddIsForbidden() {
        final GovSavedData data = withCustomJob();
        final JobService.Outcome outcome = JobService.editCapability(
                data, actorWith(GovCapability.JOB_MANAGE), "harbor_patrol", "fine.issue", true);
        check(!outcome.ok() && outcome.code() == ErrorCode.FORBIDDEN,
                "adding an unheld capability must be FORBIDDEN, got " + outcome);
        check(!data.job("harbor_patrol").capabilities().contains(GovCapability.FINE_ISSUE),
                "a refused add must store nothing");
    }

    void testNullActorIsInvalidState() {
        final GovSavedData data = withCustomJob();
        final JobService.Outcome outcome = JobService.editCapability(
                data, null, "harbor_patrol", "fine.issue", true);
        check(!outcome.ok() && outcome.code() == ErrorCode.INVALID_STATE,
                "a null actor must be INVALID_STATE, never a throw, got " + outcome);
    }

    void testHeldAddSucceedsAndRemoveNeedsNoHold() {
        final GovSavedData data = withCustomJob();
        final JobService.Outcome added = JobService.editCapability(
                data, actorWith(GovCapability.FINE_ISSUE), "harbor_patrol", "fine.issue", true);
        check(added.ok(), "adding a held capability must succeed, got " + added);
        check(data.job("harbor_patrol").capabilities().contains(GovCapability.FINE_ISSUE),
                "the added capability must be on the job");
        final JobService.Outcome removed = JobService.editCapability(
                data, actorWith(), "harbor_patrol", "fine.issue", false);
        check(removed.ok(), "removing must need no hold, got " + removed);
        check(!data.job("harbor_patrol").capabilities().contains(GovCapability.FINE_ISSUE),
                "the removed capability must be gone");
    }

    void testFailedDebitCreatesNothing() {
        final GovSavedData data = new GovSavedData();
        final AccountService accounts = new AccountService(data);
        accounts.government();
        final UUID killerId = UUID.randomUUID();
        accounts.player(killerId);
        final String player = AccountService.playerAccountId(killerId);
        check(accounts.debit(GOV, 100L, "bounty payout").failed(),
                "debiting an empty escrow must fail");
        check(accounts.balance(GOV).balance() == 0L,
                "a failed debit must leave the escrow at zero");
        check(accounts.balance(player).balance() == 0L,
                "no credit may follow a failed debit, so the player stays at zero");
        check(accounts.conservationHolds(),
                "a failed debit must keep ledger conservation intact");
    }

    void testDebitFirstConservesTotal() {
        final GovSavedData data = new GovSavedData();
        final AccountService accounts = new AccountService(data);
        accounts.government();
        final UUID killerId = UUID.randomUUID();
        accounts.player(killerId);
        final String player = AccountService.playerAccountId(killerId);
        check(!accounts.credit(GOV, 100L, "bounty escrow").failed(),
                "setup: escrowing 100 must succeed");
        check(!accounts.debit(GOV, 30L, "bounty payout").failed(),
                "setup: debiting 30 must succeed");
        check(!accounts.credit(player, 30L, "bounty payout").failed(),
                "setup: paying 30 must succeed");
        final long gov = accounts.balance(GOV).balance();
        final long paid = accounts.balance(player).balance();
        check(gov == 70L, "escrow must hold 70 after a 30 payout, got " + gov);
        check(paid == 30L, "the player must hold 30 after a 30 payout, got " + paid);
        check(gov + paid == 100L, "debit-first movement must conserve the total");
    }

    void testNullServerAndLevelGuards() {
        check(GovSavedData.getIfPresent((MinecraftServer) null) == null,
                "getIfPresent(null server) must return null, never throw");
        check(GovSavedData.get((ServerLevel) null) == null,
                "get((ServerLevel) null) must return null, never throw");
    }

    void testCellNameEchoSanitized() {
        final String hostile = "§aEvil\nCell§r\tName";
        final String echoed = Sanitizer.name(hostile);
        check(!echoed.contains("§"), "the console echo must strip formatting codes: " + echoed);
        check(echoed.indexOf('\n') < 0 && echoed.indexOf('\t') < 0,
                "the console echo must strip control characters: " + echoed);
        check(echoed.contains("Evil") && echoed.contains("Cell") && echoed.contains("Name"),
                "sanitizing must keep the readable words: " + echoed);
    }

    public static void main(final String[] args) {
        final GovHardeningCycle1Test t = new GovHardeningCycle1Test();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testUnheldAddIsForbidden", "testNullActorIsInvalidState",
                "testHeldAddSucceedsAndRemoveNeedsNoHold", "testFailedDebitCreatesNothing",
                "testDebitFirstConservesTotal", "testNullServerAndLevelGuards",
                "testCellNameEchoSanitized"};
        final Runnable[] tests = {
                t::testUnheldAddIsForbidden, t::testNullActorIsInvalidState,
                t::testHeldAddSucceedsAndRemoveNeedsNoHold, t::testFailedDebitCreatesNothing,
                t::testDebitFirstConservesTotal, t::testNullServerAndLevelGuards,
                t::testCellNameEchoSanitized};
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
