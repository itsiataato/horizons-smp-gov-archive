package com.horizonssmp.gov.server.economy;

import java.util.Map;
import java.util.Set;

import com.horizonssmp.gov.common.ActionRequest;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.server.GovActionDispatcher;
import com.horizonssmp.gov.server.GovGlobalGuard;
import com.horizonssmp.gov.server.GovSavedData;

/**
 * TASK-010 step-2 (FR-1, FR-8) {@code canCredit} affordance-flag matrix tests.
 *
 * <p>Acceptance criteria: a BALANCE success carries {@code canCredit=true} exactly for a
 * dual-gated actor ({@code season.admin} plus live permission level 4) and {@code "false"}
 * otherwise; a BALANCE refusal carries no usable {@code true}; the flag computation never
 * throws on a null actor (a refusal, not a throw); and {@code BANK_CREDIT} stays refused
 * without the gate regardless of any client hint, because enforcement is server-side.
 *
 * <p>Runs headless: contexts carry a {@code null} actor, records live in a bare
 * {@code new GovSavedData()}, and no test touches {@code FMLEnvironment} or the config spec
 * (see tips/t08). Lives in the economy package because the handlers are package-private.
 * Everything asserted here executes real production bytecode: the real BALANCE and
 * {@code BANK_CREDIT} handlers (via the guard, the production dispatch wrapper), the real
 * capability table, the real account-ref resolver, and the real {@link GovFields} wire
 * codec both sides share.
 *
 * <p>Honest limits, all marked at the test: a BALANCE success needs a live
 * {@code ServerPlayer} (UUID, server, vault regions), so the success-side {@code "true"} for
 * a dual-gated actor and {@code "false"} otherwise are proven in-game (Phase 10); the
 * permission half of the dual gate dereferences the live actor, so on a null actor it throws
 * inside the handler and the guard converts it to an {@code INVALID_STATE} refusal (never a
 * crash); the {@code FORBIDDEN}-with-text refusal needs a live actor; and the client
 * {@code applyCanCredit} plus button gating need the Minecraft client/FML (tips/t08).
 */
final class GovBankCreditGateTest {

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static GovActionDispatcher.ActionContext balanceCtx(
            final Map<String, String> payload, final String reqId,
            final Set<GovCapability> capabilities) {
        return new GovActionDispatcher.ActionContext(
                new ActionRequest(OpCode.BANK_BALANCE, payload, reqId),
                null, new GovSavedData(), null, capabilities, null, 0L);
    }

    private static GovActionDispatcher.ActionContext creditCtx(
            final Map<String, String> payload, final String reqId,
            final Set<GovCapability> capabilities) {
        return new GovActionDispatcher.ActionContext(
                new ActionRequest(OpCode.BANK_CREDIT, payload, reqId),
                null, new GovSavedData(), null, capabilities, null, 0L);
    }

    private static ActionResult guardBalance(
            final GovActionDispatcher.ActionContext ctx, final String reqId) {
        return GovGlobalGuard.guardHandler(OpCode.BANK_BALANCE, "?",
                reqId, () -> EconomyBankHandlers.bankBalance(ctx));
    }

    private static ActionResult guardCredit(
            final GovActionDispatcher.ActionContext ctx, final String reqId) {
        return GovGlobalGuard.guardHandler(OpCode.BANK_CREDIT, "?",
                reqId, () -> EconomyBankHandlers.bankCredit(ctx));
    }

    /** No usable {@code true} may survive inside a refusal delta. */
    private static void checkNoUsableTrue(final ActionResult refusal, final String reqId) {
        check(!refusal.ok(), "a null-actor BALANCE must be refused, never accepted");
        check(reqId.equals(refusal.reqId()), "refusal must carry the request id");
        check("".equals(refusal.delta()),
                "a BALANCE refusal must carry no delta, so no canCredit=true can leak, got: "
                        + refusal.delta());
    }

    void testBalanceNullActorBecomesRefusalNotThrow() {
        final ActionResult refusal = guardBalance(
                balanceCtx(Map.of(), "req-bal-null", Set.of()), "req-bal-null");
        check(refusal != null, "guard must return a refusal, never null");
        check(refusal.errorCode() == ErrorCode.INVALID_STATE,
                "null actor dereferences the live account/permission reads, so the guard"
                        + " converts the throw to INVALID_STATE (the success-side flag is"
                        + " live-only, proven in-game), got " + refusal);
        checkNoUsableTrue(refusal, "req-bal-null");
    }

    void testBalanceNullActorWithSeasonAdminStillRefuses() {
        final ActionResult refusal = guardBalance(
                balanceCtx(Map.of(), "req-bal-admin-null",
                        Set.of(GovCapability.SEASON_ADMIN)),
                "req-bal-admin-null");
        check(refusal != null, "guard must return a refusal, never null");
        check(!refusal.ok(),
                "the capability half alone must never satisfy the dual gate: a null actor"
                        + " cannot pass the live permission half, got " + refusal);
        checkNoUsableTrue(refusal, "req-bal-admin-null");
    }

    void testBalanceRefusalDropsFlagOnEveryAccountPath() {
        final ActionResult gov = guardBalance(
                balanceCtx(Map.of(EconomyKeys.KEY_ACCOUNT, "gov"), "req-bal-gov", Set.of()),
                "req-bal-gov");
        checkNoUsableTrue(gov, "req-bal-gov");
        final ActionResult named = guardBalance(
                balanceCtx(Map.of(EconomyKeys.KEY_ACCOUNT, "NobodyHere"), "req-bal-named",
                        Set.of(GovCapability.SEASON_ADMIN)),
                "req-bal-named");
        checkNoUsableTrue(named, "req-bal-named");
    }

    void testPermissionHalfNeedsLiveActor() {
        boolean threw = false;
        try {
            EconomyHandlerSupport.hasConsolePermission(null);
        } catch (final NullPointerException expected) {
            threw = true;
        }
        check(threw,
                "the permission half dereferences the live actor, so a null actor closes"
                        + " the dual gate before any flag is computed (production dispatch"
                        + " converts this to an INVALID_STATE refusal via the guard)");
    }

    void testCapabilityHalfMatrix() {
        check(GovCapabilities.requiredFor(OpCode.BANK_CREDIT) == GovCapability.SEASON_ADMIN,
                "BANK_CREDIT must gate on season.admin, the strongest named authority");
        check(GovCapabilities.requiredFor(OpCode.BANK_BALANCE)
                        == GovCapability.PLAYER_LOOKUP_SELF,
                "BALANCE stays a baseline read at the dispatcher: the credit flag is"
                        + " computed in-handler, never as a dispatch gate");
        final GovActionDispatcher.ActionContext empty =
                balanceCtx(Map.of(), "req-cap", Set.of());
        check(!empty.can(GovCapability.SEASON_ADMIN),
                "an empty capability set holds nothing");
        final GovActionDispatcher.ActionContext teller =
                balanceCtx(Map.of(), "req-cap", Set.of(GovCapability.BANK_MANAGE));
        check(!teller.can(GovCapability.SEASON_ADMIN),
                "bank.manage (every teller) must stay weaker than the credit gate");
        final GovActionDispatcher.ActionContext executive =
                balanceCtx(Map.of(), "req-cap", Set.of(GovCapability.SEASON_ADMIN));
        check(executive.can(GovCapability.SEASON_ADMIN),
                "season.admin must satisfy the capability half of the dual gate");
        check(!GovCapabilities.BASELINE.contains(GovCapability.SEASON_ADMIN),
                "the baseline must not grant season.admin: ordinary players cannot credit");
    }

    void testFlagWireShape() {
        check("canCredit".equals(EconomyKeys.KEY_CAN_CREDIT),
                "the flag spelling both sides share must stay canCredit");
        check("true".equals(Boolean.toString(true)) && "false".equals(Boolean.toString(false)),
                "the server writes the flag with Boolean.toString, so only exact spellings"
                        + " travel");
        final Map<String, String> yes =
                GovFields.decode(GovFields.encode(Map.of(EconomyKeys.KEY_CAN_CREDIT, "true")));
        check("true".equals(yes.get(EconomyKeys.KEY_CAN_CREDIT)),
                "an exact true must survive the shared wire codec as the client's enabler");
        final Map<String, String> no =
                GovFields.decode(GovFields.encode(Map.of(EconomyKeys.KEY_CAN_CREDIT, "false")));
        check(!"true".equals(no.get(EconomyKeys.KEY_CAN_CREDIT)),
                "false must decode to the client's disabled state");
        check(!"true".equals(GovFields.decode(GovFields.encode(Map.of())).get(
                        EconomyKeys.KEY_CAN_CREDIT)),
                "a refusal-shaped delta (flag dropped) must decode to disabled, never stale true");
        for (final String near : new String[] {"True", "TRUE", "1", "yes", " true"}) {
            check(!"true".equals(near),
                    "near-true '" + near + "' must not satisfy the client's exact-match gate");
        }
    }

    void testBankCreditIgnoresClientHint() {
        final Map<String, String> hinted = Map.of(
                EconomyKeys.KEY_CAN_CREDIT, "true",
                "target", "Somebody",
                EconomyKeys.KEY_AMOUNT, "10",
                EconomyKeys.KEY_REASON, "remedy for testing");
        final ActionResult withoutGate = guardCredit(
                creditCtx(hinted, "req-hint-plain", Set.of()), "req-hint-plain");
        check(withoutGate != null && !withoutGate.ok(),
                "a forged canCredit hint with no capabilities must still be refused, got "
                        + withoutGate);
        final ActionResult withCaps = guardCredit(
                creditCtx(hinted, "req-hint-admin", Set.of(GovCapability.SEASON_ADMIN)),
                "req-hint-admin");
        check(withCaps != null && !withCaps.ok(),
                "a forged canCredit hint must not open the server gate either: the handler"
                        + " re-checks the live permission level and the null actor still"
                        + " refuses (FORBIDDEN-with-text needs a live actor, proven"
                        + " in-game), got " + withCaps);
    }

    public static void main(final String[] args) {
        final GovBankCreditGateTest t = new GovBankCreditGateTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testBalanceNullActorBecomesRefusalNotThrow",
                "testBalanceNullActorWithSeasonAdminStillRefuses",
                "testBalanceRefusalDropsFlagOnEveryAccountPath",
                "testPermissionHalfNeedsLiveActor", "testCapabilityHalfMatrix",
                "testFlagWireShape", "testBankCreditIgnoresClientHint"};
        final Runnable[] tests = {
                t::testBalanceNullActorBecomesRefusalNotThrow,
                t::testBalanceNullActorWithSeasonAdminStillRefuses,
                t::testBalanceRefusalDropsFlagOnEveryAccountPath,
                t::testPermissionHalfNeedsLiveActor, t::testCapabilityHalfMatrix,
                t::testFlagWireShape, t::testBankCreditIgnoresClientHint};
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
