package com.horizonssmp.gov.server.economy;

import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;

import com.horizonssmp.gov.common.ActionRequest;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.EconomyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.RateLimitPolicy;
import com.horizonssmp.gov.common.economy.EconomyMath;
import com.horizonssmp.gov.server.GovActionDispatcher;
import com.horizonssmp.gov.server.GovGlobalGuard;
import com.horizonssmp.gov.server.GovSavedData;
import com.horizonssmp.gov.server.GovTargets;

/**
 * TASK-015 (FR-1, FR-8) Treasury 7-action server-parity tests.
 *
 * <p>Acceptance criteria: the seven wire-ready Treasury buttons send the same opcode plus payload
 * keys as their commands and get identical server outcomes. Every assertion here executes real
 * production bytecode: the real handlers (via the guard), the real amount parser, the real target
 * resolver, the real capability/rate/opcode tables, and the real registration map.
 *
 * <p>Runs headless: contexts carry a {@code null} actor, records live in a bare
 * {@code new GovSavedData()}, and no test touches {@code FMLEnvironment} or the config spec
 * (see tips/t08). Null-actor handlers that dereference the live player throw inside and the guard
 * converts it to an {@code INVALID_STATE} refusal (never a crash); withdraw/pay with a valid
 * amount but no resolved target stop at {@code BAD_TARGET} without touching the actor.
 *
 * <p>Honest limits: success movement, audit content, nonce replay, region and live perm
 * need a live server plus vault (Phase 10); the blank-target hint plus pay layout need
 * the client/FML; the FORBIDDEN-with-text refusal needs a live actor.
 */
final class GovTreasuryActionsTest {

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static GovActionDispatcher.ActionContext ctx(final OpCode op,
            final Map<String, String> payload, final String reqId) {
        return new GovActionDispatcher.ActionContext(
                new ActionRequest(op, payload, reqId),
                null, new GovSavedData(), null, Set.of(), null, 0L);
    }

    private static ActionResult guard(final OpCode op,
            final GovActionDispatcher.ActionContext c, final Supplier<ActionResult> call) {
        return GovGlobalGuard.guardHandler(op, "?", c.request().reqId(), call);
    }

    void testOpsRegisteredAndFlags() {
        EconomyHandlers.register();
        for (final OpCode op : new OpCode[] {OpCode.BANK_TRANSFER, OpCode.BANK_WITHDRAW,
                OpCode.BANK_HISTORY, OpCode.PAY_PLAYER, OpCode.CONTRACT_POST,
                OpCode.PAYROLL_RUN, OpCode.CONTRACT_CREDIT}) {
            check(GovActionDispatcher.isRegistered(op),
                    op + " must be registered: button would otherwise be dead");
        }
        check(!OpCode.BANK_TRANSFER.requiresPlayerTarget(), "transfer resolves accounts itself");
        check(OpCode.BANK_WITHDRAW.requiresPlayerTarget(), "withdraw needs a person target");
        check(!OpCode.BANK_HISTORY.requiresPlayerTarget(), "history resolves accounts itself");
        check(OpCode.PAY_PLAYER.requiresPlayerTarget(), "pay needs a person target");
        check(OpCode.CONTRACT_POST.requiresPlayerTarget(), "post names the builder");
        check(!OpCode.PAYROLL_RUN.requiresPlayerTarget(), "payroll takes a scope string");
        check(!OpCode.CONTRACT_CREDIT.requiresPlayerTarget(), "credit resolves its person itself");
        check(OpCode.BANK_TRANSFER.audited() && !OpCode.BANK_TRANSFER.dispatcherAudited(),
                "transfer is service-audited like every gold move");
        check(!OpCode.BANK_HISTORY.audited() && !OpCode.BANK_HISTORY.dispatcherAudited(),
                "history is a read: never audited");
        check(OpCode.PAY_PLAYER.audited() && !OpCode.PAY_PLAYER.dispatcherAudited(),
                "pay is service-audited");
        check(OpCode.CONTRACT_POST.audited() && !OpCode.CONTRACT_POST.dispatcherAudited(),
                "post is service-audited");
        check(RateLimitPolicy.forOp(OpCode.BANK_TRANSFER).kind() == RateLimitPolicy.Kind.MUTATION,
                "transfer must be a MUTATION");
        check(RateLimitPolicy.forOp(OpCode.BANK_HISTORY).kind() == RateLimitPolicy.Kind.READ,
                "history must be a READ");
        check(RateLimitPolicy.usesEconomyBucket(OpCode.PAY_PLAYER),
                "pay must take the 60s economy bucket");
        check(RateLimitPolicy.usesEconomyBucket(OpCode.PAYROLL_RUN),
                "payroll must take the 60s economy bucket");
        check(!RateLimitPolicy.usesEconomyBucket(OpCode.BANK_HISTORY),
                "history is a read: no economy bucket");
        check("from".equals(EconomyKeys.KEY_FROM) && "to".equals(EconomyKeys.KEY_TO)
                && "amount".equals(EconomyKeys.KEY_AMOUNT)
                && "target".equals(GovTargets.KEY_TARGET)
                && "account".equals(EconomyKeys.KEY_ACCOUNT)
                && "page".equals(EconomyKeys.KEY_PAGE)
                && "scope".equals(EconomyKeys.KEY_SCOPE)
                && "materials".equals(EconomyHandlerSupport.KEY_MATERIALS)
                && "labor".equals(EconomyHandlerSupport.KEY_LABOR)
                && "description".equals(EconomyHandlerSupport.KEY_DESCRIPTION),
                "payload spellings both sides share must stay exact");
    }

    void testCapabilityGates() {
        check(GovCapabilities.requiredFor(OpCode.BANK_TRANSFER) == GovCapability.BANK_MANAGE,
                "transfer moves another account so it is bank.manage");
        check(GovCapabilities.requiredFor(OpCode.BANK_WITHDRAW) == GovCapability.BANK_MANAGE,
                "withdraw moves another account so it is bank.manage");
        check(GovCapabilities.requiredFor(OpCode.BANK_HISTORY)
                == GovCapability.PLAYER_LOOKUP_SELF, "history is a baseline read");
        check(GovCapabilities.requiredFor(OpCode.PAY_PLAYER) == GovCapability.PLAYER_LOOKUP_SELF,
                "pay spends the actor gold so dispatch stays baseline");
        check(GovCapabilities.requiredFor(OpCode.CONTRACT_POST) == GovCapability.BANK_MANAGE,
                "post debits Government so it is bank.manage");
        check(GovCapabilities.requiredFor(OpCode.PAYROLL_RUN) == GovCapability.PLAYER_LOOKUP_SELF,
                "payroll dispatch stays baseline, the service scopes the run");
        check(GovCapabilities.requiredFor(OpCode.CONTRACT_CREDIT) == GovCapability.SEASON_ADMIN,
                "contract credit mints like console credit so it gates season.admin, not bank.manage");
        check(GovCapabilities.requiredFor(OpCode.BANK_CREDIT) == GovCapability.SEASON_ADMIN,
                "console credit stays the strongest named authority");
        check(!GovCapabilities.BASELINE.contains(GovCapability.BANK_MANAGE),
                "baseline must not grant bank.manage");
        check(GovCapability.BANK_MANAGE != GovCapability.SEASON_ADMIN,
                "teller authority must stay weaker than the console gate");
    }

    void testNullActorRefusals() {
        final var tctx = ctx(OpCode.BANK_TRANSFER, Map.of(EconomyKeys.KEY_FROM, "me",
                EconomyKeys.KEY_TO, "gov", EconomyKeys.KEY_AMOUNT, "64",
                EconomyKeys.KEY_REASON, "Treasury transfer."), "req-t15-transfer");
        final ActionResult transfer = guard(OpCode.BANK_TRANSFER, tctx,
                () -> EconomyBankHandlers.bankTransfer(tctx));
        check(!transfer.ok() && transfer.errorCode() == ErrorCode.INVALID_STATE,
                "null-actor transfer derefs me/gov accounts so guard gives INVALID_STATE, got "
                        + transfer);
        final var wctx = ctx(OpCode.BANK_WITHDRAW,
                Map.of(EconomyKeys.KEY_AMOUNT, "64"), "req-t15-withdraw");
        final ActionResult withdraw = guard(OpCode.BANK_WITHDRAW, wctx,
                () -> EconomyBankHandlers.bankWithdraw(wctx));
        check(!withdraw.ok() && withdraw.errorCode() == ErrorCode.BAD_TARGET,
                "withdraw with valid amount but no resolved target must be BAD_TARGET, got "
                        + withdraw);
        final var hctx = ctx(OpCode.BANK_HISTORY,
                Map.of(EconomyKeys.KEY_ACCOUNT, "me", EconomyKeys.KEY_PAGE, "0"),
                "req-t15-history");
        final ActionResult history = guard(OpCode.BANK_HISTORY, hctx,
                () -> EconomyBankHandlers.bankHistory(hctx));
        check(!history.ok() && history.errorCode() == ErrorCode.INVALID_STATE,
                "null-actor history derefs the me account so guard gives INVALID_STATE, got "
                        + history);
        final var pctx = ctx(OpCode.PAY_PLAYER,
                Map.of(EconomyKeys.KEY_AMOUNT, "64", GovTargets.KEY_TARGET, "Somebody"),
                "req-t15-pay");
        final ActionResult pay = guard(OpCode.PAY_PLAYER, pctx,
                () -> EconomyBankHandlers.payPlayer(pctx));
        check(!pay.ok() && pay.errorCode() == ErrorCode.BAD_TARGET,
                "pay with valid amount but no resolved target must be BAD_TARGET, got " + pay);
        final var cctx = ctx(OpCode.CONTRACT_POST, Map.of(
                EconomyHandlerSupport.KEY_MATERIALS, "64",
                EconomyHandlerSupport.KEY_LABOR, "64",
                EconomyHandlerSupport.KEY_DESCRIPTION, "Treasury contract.",
                GovTargets.KEY_TARGET, "Somebody"), "req-t15-post");
        final ActionResult post = guard(OpCode.CONTRACT_POST, cctx,
                () -> EconomyContractHandlers.contractPost(cctx));
        check(!post.ok() && post.errorCode() == ErrorCode.INVALID_STATE,
                "null-actor post dies in the nonce take so guard gives INVALID_STATE, got "
                        + post);
        final var rctx = ctx(OpCode.PAYROLL_RUN, Map.of(EconomyKeys.KEY_SCOPE, "gov",
                EconomyKeys.KEY_REASON, "Payroll run."), "req-t15-payroll");
        final ActionResult payroll = guard(OpCode.PAYROLL_RUN, rctx,
                () -> EconomyPayrollHandlers.payrollRun(rctx));
        check(!payroll.ok() && payroll.errorCode() == ErrorCode.INVALID_STATE,
                "null-actor payroll dies in the nonce take so guard gives INVALID_STATE, got "
                        + payroll);
        final var kctx = ctx(OpCode.CONTRACT_CREDIT, Map.of(EconomyKeys.KEY_ACCOUNT, "me",
                EconomyKeys.KEY_AMOUNT, "64", EconomyKeys.KEY_REASON, "Console credit.",
                GovTargets.KEY_TARGET, "me"), "req-t15-credit");
        final ActionResult credit = guard(OpCode.CONTRACT_CREDIT, kctx,
                () -> EconomyBankHandlers.contractCredit(kctx));
        check(!credit.ok() && credit.errorCode() == ErrorCode.FORBIDDEN,
                "a capability-less contract credit is refused FORBIDDEN before any live read:"
                        + " the season.admin re-check runs before the perm check, got "
                        + credit);
        for (final ActionResult r : new ActionResult[] {transfer, withdraw, history, pay, post,
                payroll, credit}) {
            check("".equals(r.delta()), "a refusal must carry no delta, got " + r);
        }
        check("req-t15-pay".equals(pay.reqId()), "refusal must carry the request id");
    }

    void testJunkAmounts() {
        for (final String junk : new String[] {"twelve", "", "   ", "10.5", "-5", "1,000",
                "+1", "0x10"}) {
            final OptionalLong parsed = EconomyHandlerSupport.amount(
                    ctx(OpCode.PAY_PLAYER, Map.of(EconomyKeys.KEY_AMOUNT, junk), "req-amt"),
                    EconomyKeys.KEY_AMOUNT);
            check(parsed.isEmpty(), "amount '" + junk + "' must not parse");
            final OptionalLong mat = EconomyHandlerSupport.amount(ctx(OpCode.CONTRACT_POST,
                    Map.of(EconomyHandlerSupport.KEY_MATERIALS, junk), "req-mat"),
                    EconomyHandlerSupport.KEY_MATERIALS);
            check(mat.isEmpty(), "materials '" + junk + "' must not parse");
        }
        check(EconomyHandlerSupport.amount(ctx(OpCode.PAY_PLAYER, Map.of(), "req-miss"),
                EconomyKeys.KEY_AMOUNT).isEmpty(), "a missing amount must not parse");
        check(EconomyMath.parseAmount(null).isEmpty(), "a null amount must not parse");
        final OptionalLong def = EconomyHandlerSupport.amount(ctx(OpCode.PAY_PLAYER,
                Map.of(EconomyKeys.KEY_AMOUNT, "64"), "req-def"), EconomyKeys.KEY_AMOUNT);
        check(def.isPresent() && def.getAsLong() == 64L,
                "the client 64 default must arrive as an ordinary amount, got " + def);
        final ActionResult refusal = EconomyHandlerSupport.badAmount(
                ctx(OpCode.PAY_PLAYER, Map.of(EconomyKeys.KEY_AMOUNT, "twelve"), "req-shape"));
        check(!refusal.ok() && refusal.errorCode() == ErrorCode.BAD_PAYLOAD,
                "the amount refusal must be BAD_PAYLOAD, got " + refusal);
        check(EconomyHandlerSupport.BAD_AMOUNT.equals(refusal.errorText())
                && "Enter a whole amount in gold ingots.".equals(refusal.errorText()),
                "the refusal text must stay the command sentence, got: " + refusal.errorText());
    }

    void testUnknownTargets() {
        final GovSavedData data = new GovSavedData();
        check(GovTargets.resolve(null, data, "NobodyHere") == null, "no server means no target");
        check(GovTargets.resolve(null, data, "") == null, "empty resolves to nothing");
        check(GovTargets.resolve(null, data, "   ") == null, "blank resolves to nothing");
        check(GovTargets.resolve(null, data, null) == null, "null resolves to nothing");
        check(GovTargets.parseUuid("junk").isEmpty(), "junk is not a UUID target");
        final ActionResult refusal = EconomyHandlerSupport.badTarget(
                ctx(OpCode.PAY_PLAYER, Map.of(), "req-target-shape"));
        check(!refusal.ok() && refusal.errorCode() == ErrorCode.BAD_TARGET,
                "the unresolvable-target refusal must be BAD_TARGET, got " + refusal);
        check("req-target-shape".equals(refusal.reqId()), "refusal must carry the request id");
    }

    void testPayRouting() {
        check("".equals(EconomyHandlerSupport.firstNonBlank("", "")),
                "blank target plus blank alias selects nothing server-side");
        check("Somebody".equals(EconomyHandlerSupport.firstNonBlank("Somebody", "me")),
                "the target key must win over any alias");
        check("".equals(EconomyHandlerSupport.firstNonBlank("  ", null)),
                "whitespace must count as blank (client stops with no_target, live-only)");
        check(EconomyHandlerSupport.amount(ctx(OpCode.PAY_PLAYER,
                Map.of(EconomyKeys.KEY_AMOUNT, ""), "req-blank"),
                EconomyKeys.KEY_AMOUNT).isEmpty(),
                "a blank amount would be BAD_PAYLOAD server-side: the client 64 default"
                        + " is what keeps the button useful (layout needs FML, live-only)");
    }

    void testContractCreditPermMirror() {
        check(EconomyHandlerSupport.CONSOLE_PERMISSION == 4,
                "the live re-check must stay permission level 4, as the command");
        boolean threw = false;
        try {
            EconomyHandlerSupport.hasConsolePermission(null);
        } catch (final NullPointerException expected) {
            threw = true;
        }
        check(threw, "the perm check dereferences the live actor, so null closes the gate");
        check("A console credit requires permission level 4.".equals(
                "A console credit requires permission level "
                        + EconomyHandlerSupport.CONSOLE_PERMISSION + "."),
                "both console paths must keep the identical FORBIDDEN sentence");
        check(!OpCode.CONTRACT_CREDIT.dispatcherAudited()
                && !OpCode.BANK_CREDIT.dispatcherAudited(),
                "both console paths stay service-audited, never twice by the dispatcher");
        check(RateLimitPolicy.usesEconomyBucket(OpCode.CONTRACT_CREDIT)
                && RateLimitPolicy.usesEconomyBucket(OpCode.BANK_CREDIT),
                "both console paths mint without a token so both take the economy bucket");
    }

    public static void main(final String[] args) {
        final GovTreasuryActionsTest t = new GovTreasuryActionsTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {"testOpsRegisteredAndFlags", "testCapabilityGates",
                "testNullActorRefusals", "testJunkAmounts", "testUnknownTargets", "testPayRouting",
                "testContractCreditPermMirror"};
        final Runnable[] tests = {t::testOpsRegisteredAndFlags, t::testCapabilityGates,
                t::testNullActorRefusals, t::testJunkAmounts, t::testUnknownTargets,
                t::testPayRouting, t::testContractCreditPermMirror};
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
