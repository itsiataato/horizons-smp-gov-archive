package com.horizonssmp.gov.server;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.common.ActionRequest;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.BountyKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovCapabilities;
import com.horizonssmp.gov.common.GovCapability;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.RateLimitPolicy;
import com.horizonssmp.gov.common.records.BountyRecord;

/**
 * TASK-013 (FR-1, FR-8) bounty tests: place-to-list-to-cancel round-trip plus refusal proofs.
 *
 * <p>Acceptance criteria: listing shows placed bounties and hides cancelled ones (dense rows,
 * true total); self-target, non-owner cancel, over-cap, no-gold, junk amount and unknown cancel
 * ids are refusals, never throws; the capability gate names {@code player.lookup.self}.
 *
 * <p>Runs headless: contexts carry a {@code null} actor, records live in a bare
 * {@code new GovSavedData()}, and no test touches {@code FMLEnvironment} or the config spec
 * (see tips/t08). Lives in the server package because the handlers are package-private.
 * Honest limit: escrow/vault paths need a live player plus server storage, so gold movement
 * is proven in-game (Phase 10). Everything asserted here executes real production bytecode:
 * the real handlers, classifier, list query and wire encode.
 */
final class GovBountyHandlersTest {

    private static final UUID POSTER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_A = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TARGET_B = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static BountyRecord active(
            final String id, final UUID target, final String name, final long createdAt) {
        return new BountyRecord(id, target, name, POSTER, "Poster", 10L, createdAt, 0L,
                BountyRecord.STATUS_ACTIVE, null, null, 0L);
    }

    private static BountyRecord cancelledCopy(final BountyRecord bounty) {
        return new BountyRecord(bounty.id(), bounty.targetUuid(), bounty.targetName(),
                bounty.posterUuid(), bounty.posterName(), bounty.amount(), bounty.createdAt(),
                bounty.expiresAt(), BountyRecord.STATUS_CANCELLED, bounty.claimedBy(),
                bounty.claimedByName(), bounty.claimedAt());
    }

    private static GovSavedData seeded() {
        final GovSavedData data = new GovSavedData();
        data.putBounty(active("bounty-old", TARGET_A, "TargetA", 1000L));
        data.putBounty(active("bounty-mid", TARGET_B, "TargetB", 2000L));
        data.putBounty(active("bounty-new", TARGET_A, "TargetA", 3000L));
        data.putBounty(new BountyRecord("bounty-expired", TARGET_A, "TargetA", POSTER, "Poster",
                10L, 1000L, 2000L, BountyRecord.STATUS_ACTIVE, null, null, 0L));
        data.putBounty(new BountyRecord("bounty-claimed", TARGET_A, "TargetA", POSTER, "Poster",
                10L, 1000L, 0L, BountyRecord.STATUS_CLAIMED, TARGET_B, "Killer", 4000L));
        data.putBounty(new BountyRecord("bounty-cancelled", TARGET_B, "TargetB", POSTER, "Poster",
                10L, 1000L, 0L, BountyRecord.STATUS_CANCELLED, null, null, 0L));
        return data;
    }

    private static GovActionDispatcher.ActionContext placeCtx(final String amount,
            final GovTargets.Resolved target, final GovSavedData data) {
        return new GovActionDispatcher.ActionContext(
                new ActionRequest(OpCode.BOUNTY_PLACE,
                        Map.of(BountyKeys.KEY_AMOUNT, amount), "req-place"),
                null, data, null, Set.of(), target, 0L);
    }

    private static GovActionDispatcher.ActionContext cancelCtx(
            final String bountyId, final GovSavedData data) {
        return new GovActionDispatcher.ActionContext(
                new ActionRequest(OpCode.BOUNTY_CANCEL,
                        Map.of(BountyKeys.KEY_BOUNTY_ID, bountyId), "req-cancel"),
                null, data, null, Set.of(), null, 0L);
    }

    private static GovActionDispatcher.ActionContext listCtx(final GovSavedData data) {
        return new GovActionDispatcher.ActionContext(
                new ActionRequest(OpCode.BOUNTY_LIST, Map.of(), "req-list"),
                null, data, null, Set.of(), null, 0L);
    }

    /**
     * Runs the handler's private refusal classifier on a service message (white-box: it only runs
     * past live-player service guards needing a server). Inputs are the exact service literals,
     * so either side rewording breaks these tests on purpose: the classifier matches on prefixes.
     */
    private static ErrorCode classify(final String message) {
        try {
            final Method classifier =
                    GovBountyHandlers.class.getDeclaredMethod("placeCode", String.class);
            classifier.setAccessible(true);
            return (ErrorCode) classifier.invoke(null, message);
        } catch (final ReflectiveOperationException e) {
            throw new AssertionError("placeCode classifier missing: " + e);
        }
    }

    void testPlacedBountiesAppearInList() {
        final List<BountyRecord> listed = BountyService.listActive(seeded(), null);
        check(listed.size() == 3, "three active bounties must list, got " + listed.size());
        boolean old = false;
        boolean mid = false;
        boolean fresh = false;
        for (final BountyRecord bounty : listed) {
            old |= "bounty-old".equals(bounty.id()) && bounty.createdAt() == 1000L;
            mid |= "bounty-mid".equals(bounty.id()) && bounty.createdAt() == 2000L;
            fresh |= "bounty-new".equals(bounty.id()) && bounty.createdAt() == 3000L;
        }
        check(old && mid && fresh, "every active bounty must appear with its sort key intact");
    }

    void testListFilterByTarget() {
        final GovSavedData data = seeded();
        check(BountyService.listActive(data, TARGET_A).size() == 2,
                "target A holds two active bounties");
        check(BountyService.listActive(data, TARGET_B).size() == 1,
                "target B holds one active bounty");
        check(BountyService.listActive(data, UUID.randomUUID()).isEmpty(),
                "an unknown target lists nothing");
    }

    void testCancelledBountyVanishes() {
        final GovSavedData data = seeded();
        final BountyRecord before = data.bounty("bounty-new");
        check(before != null && before.activeAt(System.currentTimeMillis()),
                "setup: bounty-new must start active");
        data.putBounty(cancelledCopy(before));
        check(BountyService.listActive(data, null).size() == 2,
                "cancelling must drop the list to two, hiding the cancelled row");
        check(data.bounty("bounty-nope") == null, "an unknown cancel id resolves to no record");
    }

    void testBountyRecordNbtRoundTrip() {
        final BountyRecord back = BountyRecord.read(active("bounty-rt", TARGET_A, "TargetA",
                5000L).write(), "fallback");
        check("bounty-rt".equals(back.id()) && back.amount() == 10L
                && back.createdAt() == 5000L && BountyRecord.STATUS_ACTIVE.equals(back.status())
                && TARGET_A.equals(back.targetUuid()) && POSTER.equals(back.posterUuid()),
                "a bounty must survive save/load with id, parties, amount and status intact");
    }

    void testPlaceMissingTargetIsBadTarget() {
        final GovSavedData data = new GovSavedData();
        for (final String amount : new String[] {"10", "twelve", "", "10.5"}) {
            final ActionResult result = GovBountyHandlers.bountyPlace(placeCtx(amount, null, data));
            check(!result.ok() && result.errorCode() == ErrorCode.BAD_TARGET,
                    "missing target must be BAD_TARGET for amount '" + amount + "', got " + result);
            check("req-place".equals(result.reqId()), "refusal must carry the request id");
        }
    }

    void testPlaceWithoutActorIsInvalidState() {
        final GovSavedData data = new GovSavedData();
        final GovTargets.Resolved target = new GovTargets.Resolved(TARGET_A, "TargetA", null, null);
        final ActionResult result = GovBountyHandlers.bountyPlace(placeCtx("10", target, data));
        check(!result.ok() && result.errorCode() == ErrorCode.INVALID_STATE,
                "a placeless actor must be refused INVALID_STATE, got " + result);
        check(result.errorText() != null && result.errorText().contains("requires a player"),
                "the refusal must name the player requirement, got: " + result.errorText());
    }

    void testPlaceRefusalClassification() {
        check(classify("Bounty amount must be between 5 and 500 gold.") == ErrorCode.BAD_PAYLOAD,
                "junk/out-of-range amounts must be BAD_PAYLOAD, whatever the bounds");
        check(classify("You cannot place a bounty on yourself.") == ErrorCode.INVALID_STATE,
                "a self-target must be refused, never accepted");
        check(classify("That target already has the maximum number of active bounties.")
                        == ErrorCode.LIMIT_EXCEEDED,
                "a full target must be LIMIT_EXCEEDED");
        check(classify("You already have the maximum number of active bounties.")
                        == ErrorCode.LIMIT_EXCEEDED,
                "a full poster must be LIMIT_EXCEEDED");
        check(classify("You do not have enough gold ingots.") == ErrorCode.INSUFFICIENT_ITEMS,
                "missing gold must be INSUFFICIENT_ITEMS");
        check(classify("Could not remove gold from your inventory.")
                        == ErrorCode.INSUFFICIENT_ITEMS,
                "a failed gold removal must be INSUFFICIENT_ITEMS");
        check(classify("Bounty funding failed: broke.") == ErrorCode.INVALID_STATE,
                "an escrow failure must be INVALID_STATE");
        check(classify("Bounty placement requires a player.") == ErrorCode.INVALID_STATE,
                "a missing player must be INVALID_STATE");
        check(classify("") == ErrorCode.INVALID_STATE,
                "an unrecognized refusal must fall back to INVALID_STATE");
    }

    void testCancelWithoutActorIsInvalidState() {
        final GovSavedData data = seeded();
        for (final String id : new String[] {"bounty-new", "bounty-nope", "   "}) {
            final ActionResult result = GovBountyHandlers.bountyCancel(cancelCtx(id, data));
            check(!result.ok() && result.errorCode() == ErrorCode.INVALID_STATE,
                    "cancel id '" + id + "' without an actor must be INVALID_STATE, got " + result);
            check(result.errorText() != null && result.errorText().contains("requires a player"),
                    "the refusal must name the player requirement, got: " + result.errorText());
        }
    }

    void testListWithoutActorBecomesRefusalNotThrow() {
        final GovActionDispatcher.ActionContext ctx = listCtx(new GovSavedData());
        final ActionResult result = GovGlobalGuard.guardHandler(OpCode.BOUNTY_LIST, "actor",
                "req-list", () -> GovBountyHandlers.bountyList(ctx));
        check(!result.ok() && result.errorCode() == ErrorCode.INVALID_STATE,
                "a null-actor list must become an INVALID_STATE refusal, never a throw");
        check("req-list".equals(result.reqId()), "refusal must carry the request id");
    }

    void testBountyCapabilityGate() {
        for (final OpCode op : new OpCode[] {
                OpCode.BOUNTY_PLACE, OpCode.BOUNTY_LIST, OpCode.BOUNTY_CANCEL}) {
            check(GovCapabilities.requiredFor(op) == GovCapability.PLAYER_LOOKUP_SELF,
                    op + " must gate on player.lookup.self");
        }
        check(!GovCapabilities.has(Set.of(), GovCapability.PLAYER_LOOKUP_SELF),
                "an empty capability set holds nothing, so dispatch refuses before any handler");
        check(GovCapabilities.has(GovCapabilities.BASELINE, GovCapability.PLAYER_LOOKUP_SELF),
                "the baseline grants player.lookup.self to every player");
        check("player.lookup.self".equals(GovCapability.PLAYER_LOOKUP_SELF.id()),
                "the gate id on the wire must stay player.lookup.self");
    }

    void testBountyWireContract() {
        check(BountyKeys.KEY_TARGET.equals(GovTargets.KEY_TARGET),
                "the dispatcher resolves 'target' and the client sends the same key");
        check("amount".equals(BountyKeys.KEY_AMOUNT)
                && "bountyId".equals(BountyKeys.KEY_BOUNTY_ID),
                "place/cancel payload spellings must stay amount/bountyId");
        check("bounties".equals(BountyKeys.TOTAL) && "bounty.".equals(BountyKeys.ROW_PREFIX),
                "the list total and row prefix must stay bounties/bounty.");
        check(BountyKeys.MAX_LISTED_BOUNTIES == 64, "the list page cap must stay 64");
        check(OpCode.BOUNTY_PLACE.requiresPlayerTarget()
                && !OpCode.BOUNTY_LIST.requiresPlayerTarget()
                && !OpCode.BOUNTY_CANCEL.requiresPlayerTarget(),
                "only BOUNTY_PLACE resolves a person target in dispatch");
        check(!OpCode.BOUNTY_LIST.audited()
                && OpCode.BOUNTY_PLACE.audited() && OpCode.BOUNTY_CANCEL.audited(),
                "only the list is unaudited; place and cancel leave an audit line");
        check(RateLimitPolicy.forOp(OpCode.BOUNTY_PLACE).kind() == RateLimitPolicy.Kind.MUTATION
                && RateLimitPolicy.forOp(OpCode.BOUNTY_CANCEL).kind() == RateLimitPolicy.Kind.MUTATION
                && RateLimitPolicy.forOp(OpCode.BOUNTY_LIST).kind() == RateLimitPolicy.Kind.READ,
                "place/cancel are MUTATION, list is READ");
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(BountyKeys.TOTAL, "2");
        fields.put(BountyKeys.ROW_PREFIX + 0 + "." + BountyKeys.ROW_ID, "bounty-a");
        fields.put(BountyKeys.ROW_PREFIX + 1 + "." + BountyKeys.ROW_ID, "bounty-b");
        final Map<String, String> back = GovFields.decode(GovFields.encode(fields));
        check("2".equals(back.get(BountyKeys.TOTAL)), "the true total must survive the wire");
        check("bounty-a".equals(back.get("bounty.0.id"))
                && "bounty-b".equals(back.get("bounty.1.id")),
                "dense rows must survive the wire in order");
        check(!back.containsKey("bounty.2.id"), "no gap row may appear beyond the total");
    }

    public static void main(final String[] args) {
        final GovBountyHandlersTest t = new GovBountyHandlersTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
                "testPlacedBountiesAppearInList", "testListFilterByTarget",
                "testCancelledBountyVanishes", "testBountyRecordNbtRoundTrip",
                "testPlaceMissingTargetIsBadTarget", "testPlaceWithoutActorIsInvalidState",
                "testPlaceRefusalClassification", "testCancelWithoutActorIsInvalidState",
                "testListWithoutActorBecomesRefusalNotThrow", "testBountyCapabilityGate",
                "testBountyWireContract"};
        final Runnable[] tests = {
                t::testPlacedBountiesAppearInList, t::testListFilterByTarget,
                t::testCancelledBountyVanishes, t::testBountyRecordNbtRoundTrip,
                t::testPlaceMissingTargetIsBadTarget, t::testPlaceWithoutActorIsInvalidState,
                t::testPlaceRefusalClassification, t::testCancelWithoutActorIsInvalidState,
                t::testListWithoutActorBecomesRefusalNotThrow, t::testBountyCapabilityGate,
                t::testBountyWireContract};
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
