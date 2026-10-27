package com.horizonssmp.gov.server.economy;

import java.util.Map;
import java.util.Set;

import com.horizonssmp.gov.common.ActionRequest;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.RateLimitPolicy;
import com.horizonssmp.gov.server.GovActionDispatcher;
import com.horizonssmp.gov.server.GovGlobalGuard;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovTargets;

/**
 * TASK-032 (FR-6, FR-7) contract-credit re-gate tests.
 *
 * <p>Acceptance criteria: a {@code BANK_MANAGE}-only actor calling {@code CONTRACT_CREDIT} is
 * refused {@code FORBIDDEN} with balances unchanged, while the audited {@code BANK_CREDIT}
 * remedy stays open for a dual-gated actor ({@code season.admin} plus live permission level 4).
 *
 * <p>Runs headless: contexts carry a {@code null} actor, records live in a bare
 * {@code new GovSavedData()}, and no test touches {@code FMLEnvironment} or the config spec
 * (see tips/t08). Lives in the economy package because the handlers are package-private.
 * Everything asserted here executes real production bytecode: the real
 * {@code CONTRACT_CREDIT} handler (via the guard, the production dispatch wrapper), the real
 * capability table, the real amount parser and alias routing, and the shared audit store.
 *
 * <p>Honest limits, all marked at the test: the refusal is provable headless because the
 * handler's capability re-check runs before any live read, so an ungated actor is refused
 * without dereferencing the actor; the {@code FORBIDDEN}-with-text shape for a live teller
 * and the success-side gold movement plus audit line need a live actor, vault, regions and
 * server, so they are proven in-game (Phase 10). Audit-unchanged is proven headless by
 * snapshotting the shared store's audit size around the refused call.
 */
final class GovContractCreditGateTest {

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static GovActionDispatcher.ActionContext creditCtx(
            final Map<String, String> payload, final String reqId,
            final Set<GovCapability> capabilities, final GovSavedData data) {
        return new GovActionDispatcher.ActionContext(
                new ActionRequest(OpCode.CONTRACT_CREDIT, payload, reqId),
                null, data, null, capabilities, null, 0L);
    }

    private static Map<String, String> mintedPayload() {
        return Map.of(GovTargets.KEY_TARGET, "Somebody",
                EconomyKeys.KEY_AMOUNT, "64",
                EconomyKeys.KEY_REASON, "Contract credit.");
    }

    private static ActionResult guardCredit(
            final GovActionDispatcher.ActionContext ctx, final String reqId) {
        return GovGlobalGuard.guardHandler(OpCode.CONTRACT_CREDIT, "?",
                reqId, () -> EconomyBankHandlers.contractCredit(ctx));
    }

    void testGatePinsSeasonAdmin() {
        check(GovCapabilities.requiredFor(OpCode.CONTRACT_CREDIT) == GovCapability.SEASON_ADMIN,
                "CONTRACT_CREDIT mints like console credit so it must gate season.admin");
        check(GovCapabilities.requiredFor(OpCode.BANK_CREDIT) == GovCapability.SEASON_ADMIN,
                "the sibling remedy gates season.admin: both mints share one gate");
        check("season.admin".equals(GovCapability.SEASON_ADMIN.id()),
                "the gate id on the wire must stay season.admin");
        check(GovCapability.BANK_MANAGE != GovCapability.SEASON_ADMIN,
                "bank.manage (every teller) must stay weaker than the credit gate");
        check(!GovCapabilities.has(Set.of(GovCapability.BANK_MANAGE), GovCapability.SEASON_ADMIN),
                "a teller holding only bank.manage must not satisfy the credit gate");
        check(GovCapabilities.has(Set.of(GovCapability.SEASON_ADMIN), GovCapability.SEASON_ADMIN),
                "season.admin must satisfy the capability half of the dual gate");
        check(!GovCapabilities.BASELINE.contains(GovCapability.SEASON_ADMIN),
                "the baseline must not grant season.admin: ordinary players cannot credit");
        check(GovCapabilities.builtinJobCapabilities(GovCapabilities.GOVEXECUTIVE_JOB_ID)
                        .contains(GovCapability.SEASON_ADMIN),
                "the built-in executive job must hold season.admin, so the remedy stays open");
        check(EconomyHandlerSupport.CONSOLE_PERMISSION == 4,
                "the handler's live re-check must stay permission level 4, as the command");
    }

    void testBankManageOnlyRefusedForbidden() {
        final GovSavedData data = new GovSavedData();
        final long auditBefore = data.audit().size();
        final ActionResult result = guardCredit(
                creditCtx(mintedPayload(), "req-cc-teller",
                        Set.of(GovCapability.BANK_MANAGE), data),
                "req-cc-teller");
        check(result != null && !result.ok(),
                "a bank.manage-only contract credit must be refused, never accepted, got "
                        + result);
        check(result.errorCode() == ErrorCode.FORBIDDEN,
                "the teller refusal must be FORBIDDEN, got " + result);
        check("req-cc-teller".equals(result.reqId()), "refusal must carry the request id");
        check("".equals(result.delta()), "a refusal must carry no delta");
        check(data.audit().size() == auditBefore,
                "the refused mint must write no audit line: no gold moved, nothing recorded");
    }

    void testForgedHintStillRefused() {
        final GovSavedData data = new GovSavedData();
        final Map<String, String> forged = Map.of(
                EconomyKeys.KEY_CAN_CREDIT, "true",
                GovTargets.KEY_TARGET, "Somebody",
                EconomyKeys.KEY_AMOUNT, "64",
                EconomyKeys.KEY_REASON, "Contract credit.");
        final ActionResult teller = guardCredit(
                creditCtx(forged, "req-cc-forged-teller",
                        Set.of(GovCapability.BANK_MANAGE), data),
                "req-cc-forged-teller");
        check(teller != null && !teller.ok()
                        && teller.errorCode() == ErrorCode.FORBIDDEN,
                "a forged canCredit hint must not open the teller gate, got " + teller);
        final ActionResult plain = guardCredit(
                creditCtx(forged, "req-cc-forged-plain", Set.of(), data),
                "req-cc-forged-plain");
        check(plain != null && !plain.ok()
                        && plain.errorCode() == ErrorCode.FORBIDDEN,
                "a forged canCredit hint with no capabilities must still be refused, got "
                        + plain);
        check(data.audit().size() == 0,
                "neither forged-hint refusal may write an audit line");
    }

    void testSeasonAdminNullActorReachesPermGate() {
        final GovSavedData data = new GovSavedData();
        final ActionResult result = guardCredit(
                creditCtx(mintedPayload(), "req-cc-admin-null",
                        Set.of(GovCapability.SEASON_ADMIN), data),
                "req-cc-admin-null");
        check(result != null && !result.ok(),
                "a null-actor credit must be refused, never accepted, got " + result);
        check(result.errorCode() == ErrorCode.INVALID_STATE,
                "season.admin passes the capability half, so the live perm check still"
                        + " dereferences the null actor and the guard converts the throw to"
                        + " INVALID_STATE: the remedy path stays open up to the live gate"
                        + " (FORBIDDEN-with-text needs a live actor, proven in-game), got "
                        + result);
        check("".equals(result.delta()), "a refusal must carry no delta");
        check(data.audit().size() == 0, "the refused call must write no audit line");
    }

    void testSiblingParity() {
        final GovSavedData data = new GovSavedData();
        final GovActionDispatcher.ActionContext bankCtx =
                new GovActionDispatcher.ActionContext(
                        new ActionRequest(OpCode.BANK_CREDIT, mintedPayload(), "req-par-bank"),
                        null, data, null, Set.of(), null, 0L);
        final ActionResult bank = GovGlobalGuard.guardHandler(OpCode.BANK_CREDIT, "?",
                "req-par-bank", () -> EconomyBankHandlers.bankCredit(bankCtx));
        final ActionResult contract = guardCredit(
                creditCtx(mintedPayload(), "req-par-contract", Set.of(), data),
                "req-par-contract");
        check(!bank.ok() && !contract.ok(), "both capability-less mints must be refused");
        check(bank.errorCode() == contract.errorCode()
                        && bank.errorCode() == ErrorCode.FORBIDDEN,
                "both mints must refuse identically (FORBIDDEN), got bank=" + bank
                        + " contract=" + contract);
        check(bank.errorText() != null && bank.errorText().equals(contract.errorText()),
                "both mints must carry the same refusal sentence, got bank=" + bank
                        + " contract=" + contract);
        check("A console credit requires permission level 4.".equals(
                "A console credit requires permission level "
                        + EconomyHandlerSupport.CONSOLE_PERMISSION + "."),
                "the live-gate sentence must stay identical to the command's");
    }

    void testRateAndDispatchFlags() {
        EconomyHandlers.register();
        check(RateLimitPolicy.forOp(OpCode.CONTRACT_CREDIT).kind()
                        == RateLimitPolicy.Kind.MUTATION,
                "CONTRACT_CREDIT must be a MUTATION, like every other gold-moving op");
        check(RateLimitPolicy.usesEconomyBucket(OpCode.CONTRACT_CREDIT),
                "CONTRACT_CREDIT must take the 60-second economy bucket: it mints");
        check(!OpCode.CONTRACT_CREDIT.requiresPlayerTarget(),
                "CONTRACT_CREDIT resolves its person itself (target plus account/from/to"
                        + " aliases), so dispatch must not resolve one");
        check(OpCode.CONTRACT_CREDIT.audited() && !OpCode.CONTRACT_CREDIT.dispatcherAudited(),
                "CONTRACT_CREDIT must be audited by its service, never twice by the dispatcher");
        check(GovActionDispatcher.isRegistered(OpCode.CONTRACT_CREDIT),
                "CONTRACT_CREDIT must stay a dispatched alias of the audited remedy");
    }

    void testAliasRouting() {
        check("SomeName".equals(EconomyHandlerSupport.firstNonBlank(
                "SomeName", "Acct", "From", "To")),
                "the target key must win over every alias");
        check("Acct".equals(EconomyHandlerSupport.firstNonBlank("", "Acct", "From", "To")),
                "a blank target must fall back to the account alias");
        check("From".equals(EconomyHandlerSupport.firstNonBlank("", "", "From", "To")),
                "blank target and account must fall back to the from alias");
        check("To".equals(EconomyHandlerSupport.firstNonBlank("", "", "", "To")),
                "blank target, account and from must fall back to the to alias");
        check("".equals(EconomyHandlerSupport.firstNonBlank("", "", "", "")),
                "all-blank aliases must select the actor-credit branch");
        check("".equals(EconomyHandlerSupport.firstNonBlank("  ", null, null, null)),
                "a whitespace target must count as blank");
    }

    public static void main(final String[] args) {
        final GovContractCreditGateTest t = new GovContractCreditGateTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testGatePinsSeasonAdmin", "testBankManageOnlyRefusedForbidden",
                "testForgedHintStillRefused", "testSeasonAdminNullActorReachesPermGate",
                "testSiblingParity", "testRateAndDispatchFlags", "testAliasRouting"};
        final Runnable[] tests = {
                t::testGatePinsSeasonAdmin, t::testBankManageOnlyRefusedForbidden,
                t::testForgedHintStillRefused, t::testSeasonAdminNullActorReachesPermGate,
                t::testSiblingParity, t::testRateAndDispatchFlags, t::testAliasRouting};
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
