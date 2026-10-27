package com.horizonssmp.gov.client;

import java.util.Map;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.common.BountyKeys;
import com.horizonssmp.gov.client.GovTreasuryTypes.LoadState;

/** Headless suites for the TASK-022 bounty place form. Runs via {@code main()}. */
final class GovBountyFormTest {

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static GovTreasuryPanel newPanel() {
        return new GovTreasuryPanel(null);
    }

    private static void stageBoxes(final GovTreasuryPanel p, final String target,
            final String amount) {
        final GovThemedEditBox targetBox = new GovThemedEditBox(null, 0, 0, 50, 9,
                Component.empty());
        targetBox.setValue(target);
        final GovThemedEditBox amountBox = new GovThemedEditBox(null, 0, 0, 50, 9,
                Component.empty());
        amountBox.setValue(amount);
        p.bounty.targetBox = targetBox;
        p.bounty.amountBox = amountBox;
    }

    void testParseValid() {
        check(GovBountyForm.parseAmount("64") == 64L, "'64' rejected");
        check(GovBountyForm.parseAmount(" 007 ") == 7L, "padded '007' != 7");
        check(GovBountyForm.parseAmount("+12") == 12L, "'+12' rejected");
        check(GovBountyForm.parseAmount("0") == 0L, "'0' rejected");
        check(GovBountyForm.parseAmount("9223372036854775807") == Long.MAX_VALUE,
                "long max rejected");
        check(GovBountyForm.parseAmount("00000000000000000001") == 1L,
                "zero-padded '...01' rejected by the length guard");
    }

    void testParseInvalid() {
        check(GovBountyForm.parseAmount(null) == -1L, "null accepted");
        check(GovBountyForm.parseAmount("") == -1L, "empty accepted");
        check(GovBountyForm.parseAmount("   ") == -1L, "blank accepted");
        check(GovBountyForm.parseAmount("-1") == -1L, "negative accepted");
        check(GovBountyForm.parseAmount("3.5") == -1L, "decimal accepted");
        check(GovBountyForm.parseAmount("abc") == -1L, "text accepted");
        check(GovBountyForm.parseAmount("12a") == -1L, "alphanumeric accepted");
        check(GovBountyForm.parseAmount("10 ingots") == -1L, "unit suffix accepted");
        check(GovBountyForm.parseAmount("9223372036854775808") == -1L,
                "overflowing long accepted");
        check(GovBountyForm.parseAmount("99999999999999999999") == -1L,
                "over-long digits accepted");
    }

    void testClamp() {
        check(GovBountyForm.clampAmount(5L, 10L, 100L) == 10L, "below min not raised");
        check(GovBountyForm.clampAmount(500L, 10L, 100L) == 100L, "above max not cut");
        check(GovBountyForm.clampAmount(50L, 10L, 100L) == 50L, "in-bounds changed");
    }

    void testPlacePayload() {
        final Map<String, String> payload = GovBountyRequests.placePayload("Steve", 64L);
        check(payload != null, "valid target+amount refused");
        check("Steve".equals(payload.get(BountyKeys.KEY_TARGET)), "target key wrong");
        check("64".equals(payload.get(BountyKeys.KEY_AMOUNT)), "amount key wrong");
        check(GovBountyRequests.placePayload("   ", 64L) == null, "blank target sent");
        check(GovBountyRequests.placePayload(null, 64L) == null, "null target sent");
        check(GovBountyRequests.placePayload("Steve", 0L) == null, "zero amount sent");
        check(GovBountyRequests.placePayload("Steve", -5L) == null, "negative sent");
    }

    void testOfflineNameRoutes() {
        // The typed name travels verbatim in the payload keys; the server resolves it
        // through the profile cache and refuses typos with BAD_TARGET (server-side
        // GovBountyHandlersTest.testPlaceMissingTargetIsBadTarget covers that refusal).
        final Map<String, String> payload =
                GovBountyRequests.placePayload("OfflinePlayer", 50L);
        check(payload != null, "typed offline name refused");
        check("OfflinePlayer".equals(payload.get(BountyKeys.KEY_TARGET)),
                "offline name mangled before the wire");
        check("50".equals(payload.get(BountyKeys.KEY_AMOUNT)), "amount key wrong");
    }

    void testStepCoarse() {
        final GovTreasuryPanel p = newPanel();
        p.bountyMin = 10L;
        p.bountyMax = 1000L;
        stageBoxes(p, "Steve", "50");
        GovTreasurySelection.stepBountyAmount(p, 1);
        check("60".equals(p.bounty.amountBox.getValue()), "+1 stepped by 1, not 10");
        GovTreasurySelection.stepBountyAmount(p, -1);
        check("50".equals(p.bounty.amountBox.getValue()), "-1 stepped by 1, not 10");
        stageBoxes(p, "Steve", "1000");
        GovTreasurySelection.stepBountyAmount(p, 1);
        check("1000".equals(p.bounty.amountBox.getValue()), "step escaped the max");
        stageBoxes(p, "Steve", "10");
        GovTreasurySelection.stepBountyAmount(p, -1);
        check("10".equals(p.bounty.amountBox.getValue()), "step escaped the min");
        stageBoxes(p, "Steve", "");
        GovTreasurySelection.stepBountyAmount(p, 1);
        check("10".equals(p.bounty.amountBox.getValue()), "empty box did not seed min");
    }

    void testEffectiveAmount() {
        final GovTreasuryPanel p = newPanel();
        p.bountyMin = 10L;
        p.bountyMax = 100L;
        stageBoxes(p, "Steve", "500");
        check(GovBountyForm.effectiveAmount(p) == 100L, "typed 500 not clamped to 100");
        stageBoxes(p, "Steve", "abc");
        check(GovBountyForm.effectiveAmount(p) == GovBountyForm.AMOUNT_INVALID,
                "junk amount treated as placeable");
        final GovTreasuryPanel bare = newPanel();
        bare.bountyMin = 10L;
        bare.bountyMax = 100L;
        check(GovBountyForm.effectiveAmount(bare) == GovBountyForm.AMOUNT_INVALID,
                "missing box treated as placeable");
    }

    void testCanPlaceRequiresTypedTarget() {
        final GovTreasuryPanel p = newPanel();
        p.bountyState = LoadState.READY;
        p.bountyMin = 10L;
        p.bountyMax = 1000L;
        stageBoxes(p, "", "50");
        check(!GovTreasurySupport.bountyCanPlace(p), "empty target can place");
        stageBoxes(p, "OfflinePlayer", "50");
        check(GovTreasurySupport.bountyCanPlace(p), "typed offline name cannot place");
        stageBoxes(p, "OfflinePlayer", "abc");
        check(!GovTreasurySupport.bountyCanPlace(p), "junk amount can place");
        stageBoxes(p, "OfflinePlayer", "");
        check(!GovTreasurySupport.bountyCanPlace(p), "empty amount can place");
        p.bountyMin = 0L;
        check(!GovTreasurySupport.bountyCanPlace(p), "insane bounds can place");
    }

    void testFormRowTiles() {
        final GovTreasuryPanel p = newPanel();
        p.area = new GovRect(0, 0, 400, 300);
        GovBountyGeometry.compute(p);
        check(p.bountyTargetRect.w() > 0, "target box has no width");
        check(p.bountyAmountRect.w() > 0, "amount box has no width");
        check(p.bountyTargetRect.x() < p.bountyAmountRect.x(), "target not left of amount");
        check(p.bountyAmountRect.right() <= p.bountyAmountMinusRect.x(), "amount overlaps -");
        check(p.bountyAmountMinusRect.right() <= p.bountyAmountPlusRect.x(), "- overlaps +");
        check(p.bountyAmountPlusRect.right() <= p.bountyPlaceRect.x(), "+ overlaps place");
        check(p.bountyPlaceRect.right() == p.bountyFormRect.right(),
                "place not right-aligned to the form row");
    }

    void testSeedOnParse() {
        final GovTreasuryPanel p = newPanel();
        stageBoxes(p, "", "");
        final Map<String, String> fields = Map.of(
                BountyKeys.TOTAL, "0",
                BountyKeys.MIN_AMOUNT, "10",
                BountyKeys.MAX_AMOUNT, "1000",
                BountyKeys.ONLINE_COUNT, "0");
        GovBountyList.parse(p, fields);
        check("10".equals(p.bounty.amountBox.getValue()), "list answer did not seed min");
        stageBoxes(p, "", "77");
        GovBountyList.parse(p, fields);
        check("77".equals(p.bounty.amountBox.getValue()), "list answer overwrote typing");
    }

    public static void main(final String[] args) {
        final GovBountyFormTest t = new GovBountyFormTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {
            "testParseValid", "testParseInvalid", "testClamp", "testPlacePayload",
            "testOfflineNameRoutes", "testStepCoarse",
            "testEffectiveAmount", "testCanPlaceRequiresTypedTarget", "testFormRowTiles",
            "testSeedOnParse"
        };
        final Runnable[] tests = {
            t::testParseValid, t::testParseInvalid, t::testClamp, t::testPlacePayload,
            t::testOfflineNameRoutes,
            t::testStepCoarse, t::testEffectiveAmount, t::testCanPlaceRequiresTypedTarget,
            t::testFormRowTiles, t::testSeedOnParse
        };
        for (int index = 0; index < names.length; index++) {
            try {
                tests[index].run();
                passed++;
                System.out.println("PASS " + names[index]);
            } catch (final Throwable e) {
                failed++;
                System.out.println("FAIL " + names[index] + ": " + e.getMessage());
            }
        }
        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            throw new AssertionError(failed + " failures");
        }
    }
}
