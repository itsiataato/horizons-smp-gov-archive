package com.horizonssmp.gov.server.economy;

import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.ActionRequest;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.RateLimitPolicy;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.common.economy.EconomyResult;
import com.horizonssmp.gov.server.GovActionDispatcher;
import com.horizonssmp.gov.server.GovGlobalGuard;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovTargets;

/**
 * TASK-014 (FR-1, FR-8) bank-credit tests: refusal proofs plus gate pins.
 *
 * <p>Acceptance criteria: the dashboard {@code BANK_CREDIT} path matches
 * {@code /gov bank credit} behaviour (perm-4 gate, whole-ingot amount, resolved player target,
 * shared {@code consoleCredit} audit line) and is refused without the gate.
 *
 * <p>Runs headless: contexts carry a {@code null} actor, records live in a bare
 * {@code new GovSavedData()}, and no test touches {@code FMLEnvironment} or the config spec
 * (see tips/t08). Lives in the economy package because the handler and the shared console
 * service are package-private. Everything asserted here executes real production bytecode:
 * the real handler (via the guard), the real amount parser, the real target resolver, the
 * real shared {@code consoleCredit} service entry, the real capability/rate/opcode tables.
 *
 * <p>Honest limits, all marked at the test: the handler's perm-4 check dereferences a live
 * player, so a {@code null} actor that passes the capability half throws inside the handler
 * and the guard converts it to an {@code INVALID_STATE} refusal (never a crash); the
 * {@code FORBIDDEN}-with-text refusal needs a live actor and is proven in-game (Phase 10). A zero amount parses successfully (it is a
 * valid stored amount) and is refused downstream by the shared service with
 * {@code BAD_PAYLOAD}, likewise live-only. The me/blank self-credit branch reads the actor's
 * UUID, so only its input routing (not the UUID read) is provable headless. Gold movement and
 * the audit-line write need a live vault, regions and server.
 */
final class GovBankCreditHandlerTest {

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static GovActionDispatcher.ActionContext creditCtx(
            final Map<String, String> payload, final String reqId, final GovSavedData data) {
        return new GovActionDispatcher.ActionContext(
                new ActionRequest(OpCode.BANK_CREDIT, payload, reqId),
                null, data, null, Set.of(), null, 0L);
    }

    private static GovActionDispatcher.ActionContext amountCtx(
            final String amount, final String reqId) {
        return creditCtx(Map.of(EconomyKeys.KEY_AMOUNT, amount), reqId, new GovSavedData());
    }

    void testNullActorBecomesRefusalNotThrow() {
        final GovSavedData data = new GovSavedData();
        final GovActionDispatcher.ActionContext ctx = creditCtx(
                Map.of(GovTargets.KEY_TARGET, "Somebody",
                        EconomyKeys.KEY_AMOUNT, "10",
                        EconomyKeys.KEY_REASON, "remedy for testing"),
                "req-credit-null", data);
        final ActionResult result = GovGlobalGuard.guardHandler(OpCode.BANK_CREDIT, "?",
                "req-credit-null", () -> EconomyBankHandlers.bankCredit(ctx));
        check(result != null, "guard must return a refusal, never null");
        check(!result.ok(), "a null-actor credit must be refused, never accepted");
        check("req-credit-null".equals(result.reqId()), "refusal must carry the request id");
        check(result.errorCode() == ErrorCode.FORBIDDEN,
                "a capability-less credit is refused FORBIDDEN before any live read: the"
                        + " season.admin re-check runs before the perm check, got " + result);
        check("".equals(result.delta()), "a refusal must carry no delta");
    }

    void testSeasonAdminNullActorStillReachesPermGate() {
        final GovSavedData data = new GovSavedData();
        final GovActionDispatcher.ActionContext ctx = new GovActionDispatcher.ActionContext(
                new ActionRequest(OpCode.BANK_CREDIT,
                        Map.of(GovTargets.KEY_TARGET, "Somebody",
                                EconomyKeys.KEY_AMOUNT, "10",
                                EconomyKeys.KEY_REASON, "remedy for testing"),
                        "req-credit-admin-null"),
                null, data, null, Set.of(GovCapability.SEASON_ADMIN), null, 0L);
        final ActionResult result = GovGlobalGuard.guardHandler(OpCode.BANK_CREDIT, "?",
                "req-credit-admin-null", () -> EconomyBankHandlers.bankCredit(ctx));
        check(result != null && !result.ok(), "a null-actor credit must be refused, got "
                + result);
        check(result.errorCode() == ErrorCode.INVALID_STATE,
                "season.admin passes the capability half, so the live perm check still"
                        + " dereferences the null actor and the guard converts the throw to"
                        + " INVALID_STATE (FORBIDDEN-with-text needs a live actor, proven"
                        + " in-game), got " + result);
        check("".equals(result.delta()), "a refusal must carry no delta");
    }

    void testConsoleServiceNullSourceIsForbidden() {
        final EconomyResult result = BankServiceConsole.consoleCredit(
                null, null, UUID.randomUUID(), 10L, "remedy for testing");
        check(result != null && !result.ok(), "a null console source must be refused");
        check(result.error() == ErrorCode.FORBIDDEN,
                "the shared console entry both command and handler funnel into must refuse"
                        + " a null source with FORBIDDEN (not throw), got " + result);
    }

    void testJunkEmptyAmountGate() {
        for (final String junk : new String[] {
                "twelve", "", "   ", "10.5", "-5", "1,000", "+1", "0x10"}) {
            final OptionalLong parsed =
                    EconomyHandlerSupport.amount(amountCtx(junk, "req-amt"), EconomyKeys.KEY_AMOUNT);
            check(parsed.isEmpty(),
                    "amount '" + junk + "' must not parse, so the handler refuses BAD_AMOUNT");
        }
        final OptionalLong missing = EconomyHandlerSupport.amount(
                creditCtx(Map.of(), "req-amt-missing", new GovSavedData()), EconomyKeys.KEY_AMOUNT);
        check(missing.isEmpty(), "a missing amount must not parse either");
        final OptionalLong zero = EconomyHandlerSupport.amount(
                amountCtx("0", "req-amt-zero"), EconomyKeys.KEY_AMOUNT);
        check(zero.isPresent() && zero.getAsLong() == 0L,
                "zero parses (it is a valid stored amount); its refusal happens downstream"
                        + " in the shared service (amount <= 0 is BAD_PAYLOAD), live-only");
        final OptionalLong valid = EconomyHandlerSupport.amount(
                amountCtx("10", "req-amt-ok"), EconomyKeys.KEY_AMOUNT);
        check(valid.isPresent() && valid.getAsLong() == 10L,
                "a whole-ingot amount must pass the gate, got " + valid);
        final ActionResult refusal =
                EconomyHandlerSupport.badAmount(amountCtx("twelve", "req-amt-shape"));
        check(!refusal.ok() && refusal.errorCode() == ErrorCode.BAD_PAYLOAD,
                "the handler's amount refusal must be BAD_PAYLOAD, got " + refusal);
        check(EconomyHandlerSupport.BAD_AMOUNT.equals(refusal.errorText())
                && "Enter a whole amount in gold ingots.".equals(refusal.errorText()),
                "the refusal text must stay the sentence the command shows, got: "
                        + refusal.errorText());
        check("req-amt-shape".equals(refusal.reqId()), "refusal must carry the request id");
        check(EconomyMath.parseAmount(null).isEmpty(), "a null amount must not parse");
    }

    void testUnknownTargetGate() {
        final GovSavedData data = new GovSavedData();
        check(GovTargets.resolve(null, data, "NobodyHere") == null,
                "an unknown name with no server behind it resolves to no target");
        check(GovTargets.resolve(null, data, "") == null, "an empty target resolves to nothing");
        check(GovTargets.resolve(null, data, "   ") == null,
                "a blank target resolves to nothing");
        check(GovTargets.resolve(null, data, null) == null, "a null target resolves to nothing");
        check(GovTargets.parseUuid("junk").isEmpty(), "junk is not a UUID target");
        final ActionResult refusal = EconomyHandlerSupport.badTarget(
                creditCtx(Map.of(), "req-target-shape", data));
        check(!refusal.ok() && refusal.errorCode() == ErrorCode.BAD_TARGET,
                "the handler's unresolvable-target refusal must be BAD_TARGET, got " + refusal);
        check("req-target-shape".equals(refusal.reqId()), "refusal must carry the request id");
    }

    void testMeBlankTargetRouting() {
        check("".equals(EconomyHandlerSupport.firstNonBlank("", "")),
                "blank target plus blank alias must select the actor-credit branch");
        check("".equals(EconomyHandlerSupport.firstNonBlank("  ", null)),
                "whitespace target must count as blank");
        check("".equals(EconomyHandlerSupport.firstNonBlank(null, null)),
                "missing target keys must count as blank");
        check("SomeName".equals(
                EconomyHandlerSupport.firstNonBlank("SomeName", "player:dead-beef")),
                "the target key must win over the account alias");
        check("SomeAccount".equals(EconomyHandlerSupport.firstNonBlank("", "SomeAccount")),
                "a blank target must fall back to the account alias");
        check("SomeAccount".equals(EconomyHandlerSupport.firstNonBlank("  ", "SomeAccount")),
                "a whitespace target must fall back to the account alias");
    }

    void testCapabilityGatePinsSeasonAdmin() {
        check(GovCapabilities.requiredFor(OpCode.BANK_CREDIT) == GovCapability.SEASON_ADMIN,
                "BANK_CREDIT must gate on season.admin, the strongest named authority");
        check("season.admin".equals(GovCapability.SEASON_ADMIN.id()),
                "the gate id on the wire must stay season.admin");
        check(!GovCapabilities.has(Set.of(), GovCapability.SEASON_ADMIN),
                "an empty capability set holds nothing, so dispatch refuses before any handler");
        check(!GovCapabilities.BASELINE.contains(GovCapability.SEASON_ADMIN),
                "the baseline must not grant season.admin: ordinary players cannot credit");
        check(GovCapabilities.builtinJobCapabilities(GovCapabilities.GOVEXECUTIVE_JOB_ID)
                        .contains(GovCapability.SEASON_ADMIN),
                "the built-in executive job must hold season.admin");
        check(GovCapability.BANK_MANAGE != GovCapability.SEASON_ADMIN,
                "bank.manage (every teller) must stay weaker than the credit gate");
        check(EconomyHandlerSupport.CONSOLE_PERMISSION == 4,
                "the handler's live re-check must stay permission level 4, as the command");
    }

    void testRateAndDispatchFlags() {
        check(RateLimitPolicy.forOp(OpCode.BANK_CREDIT).kind() == RateLimitPolicy.Kind.MUTATION,
                "BANK_CREDIT must be a MUTATION, like every other gold-moving op");
        check(RateLimitPolicy.usesEconomyBucket(OpCode.BANK_CREDIT),
                "BANK_CREDIT must take the 60-second economy bucket: it mints without a token");
        check(!OpCode.BANK_CREDIT.requiresPlayerTarget(),
                "BANK_CREDIT resolves its person itself (target key plus account alias),"
                        + " so dispatch must not resolve one");
        check(OpCode.BANK_CREDIT.audited() && !OpCode.BANK_CREDIT.dispatcherAudited(),
                "BANK_CREDIT must be audited by its service, never twice by the dispatcher");
        check(!OpCode.CONTRACT_CREDIT.dispatcherAudited(),
                "the sibling console path must keep the same service-audited routing");
    }

    void testWireKeysAndAuditParity() {
        check("target".equals(GovTargets.KEY_TARGET)
                && "amount".equals(EconomyKeys.KEY_AMOUNT)
                && "reason".equals(EconomyKeys.KEY_REASON)
                && "account".equals(EconomyKeys.KEY_ACCOUNT),
                "credit payload spellings must stay target/amount/reason (+account alias)");
        check("A console credit requires permission level 4.".equals(
                "A console credit requires permission level "
                        + EconomyHandlerSupport.CONSOLE_PERMISSION + "."),
                "the handler's FORBIDDEN sentence must stay identical to the command's");
        check(OpCode.BANK_CREDIT.audited(),
                "the credit must stay audited: the shared consoleCredit call writes the same"
                        + " system line the command path writes (movement plus line content"
                        + " need a live vault and server, proven in-game)");
    }

    public static void main(final String[] args) {
        final GovBankCreditHandlerTest t = new GovBankCreditHandlerTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testNullActorBecomesRefusalNotThrow", "testSeasonAdminNullActorStillReachesPermGate",
                "testConsoleServiceNullSourceIsForbidden",
                "testJunkEmptyAmountGate", "testUnknownTargetGate", "testMeBlankTargetRouting",
                "testCapabilityGatePinsSeasonAdmin", "testRateAndDispatchFlags",
                "testWireKeysAndAuditParity"};
        final Runnable[] tests = {
                t::testNullActorBecomesRefusalNotThrow, t::testSeasonAdminNullActorStillReachesPermGate,
                t::testConsoleServiceNullSourceIsForbidden,
                t::testJunkEmptyAmountGate, t::testUnknownTargetGate, t::testMeBlankTargetRouting,
                t::testCapabilityGatePinsSeasonAdmin, t::testRateAndDispatchFlags,
                t::testWireKeysAndAuditParity};
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
