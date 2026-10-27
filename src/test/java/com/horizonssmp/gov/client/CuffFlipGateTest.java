package com.horizonssmp.gov.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.horizonssmp.gov.client.GovJailTypes.Button;
import com.horizonssmp.gov.client.GovJailTypes.Click;
import com.horizonssmp.gov.client.GovJailTypes.JailRow;
import com.horizonssmp.gov.client.GovJailTypes.RequestKind;
import com.horizonssmp.gov.common.JailKeys;

/** Headless suites for the TASK-024 cuff/uncuff flip button. Runs via {@code main()}. */
final class CuffFlipGateTest {

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static GovJailPanel newPanel(final int barW, final int barH) {
        final GovJailPanel p = new GovJailPanel(null);
        p.selectedId = "";
        p.subtab = 0;
        p.cellConfigured = true;
        p.bounds = new GovRect(0, 0, barW, 400);
        p.topBars[0] = new GovRect(0, 0, barW, barH);
        return p;
    }

    private static void selectRow(final GovJailPanel p, final boolean cuffed,
            final boolean canCuff, final boolean canUncuff) {
        p.rows.add(new JailRow("d-1", "Alex", "", "consent", "Consent", "Sam", 0L, 0L,
                "", cuffed, "ACTIVE", "Held", "", true, "Alex", "held", false, 0, ""));
        p.activeRows.add(p.rows.get(0));
        p.selectedId = "d-1";
        p.detail.put(JailKeys.CUFFED, Boolean.toString(cuffed));
        p.detail.put(JailKeys.CAN_CUFF_THIS, Boolean.toString(canCuff));
        p.detail.put(JailKeys.CAN_UNCUFF_THIS, Boolean.toString(canUncuff));
    }

    private static List<Button> flips(final GovJailPanel p) {
        final List<Button> out = new ArrayList<>();
        for (final Button b : p.buttons) {
            if (b.click() == Click.CUFF || b.click() == Click.UNCUFF) {
                out.add(b);
            }
        }
        return out;
    }

    void testUncuffedShowsCuff() {
        final GovJailPanel p = newPanel(640, 16);
        selectRow(p, false, true, false);
        p.rebuildButtons();
        final List<Button> flip = flips(p);
        check(flip.size() == 1, "expected exactly one flip button, found " + flip.size());
        check(flip.get(0).click() == Click.CUFF, "uncuffed record must dispatch CUFF");
        check(p.buttonCuff.equals(flip.get(0).label()), "uncuffed label must be CUFF");
        check(flip.get(0).enabled(), "gated CUFF must be enabled");
    }

    void testCuffedShowsUncuff() {
        final GovJailPanel p = newPanel(640, 16);
        selectRow(p, true, false, true);
        p.rebuildButtons();
        final List<Button> flip = flips(p);
        check(flip.size() == 1, "expected exactly one flip button, found " + flip.size());
        check(flip.get(0).click() == Click.UNCUFF, "cuffed record must dispatch UNCUFF");
        check(p.buttonUncuff.equals(flip.get(0).label()), "cuffed label must be UNCUFF");
        check(flip.get(0).enabled(), "gated UNCUFF must be enabled");
    }

    void testFlipGateDeniesWithoutCapability() {
        final GovJailPanel denied = newPanel(640, 16);
        selectRow(denied, true, false, false);
        denied.rebuildButtons();
        final List<Button> flip = flips(denied);
        check(flip.size() == 1, "denied state must still show exactly one flip button");
        check(!flip.get(0).enabled(), "UNCUFF without capability must stay disabled");

        final GovJailPanel cuffDenied = newPanel(640, 16);
        selectRow(cuffDenied, false, false, false);
        cuffDenied.rebuildButtons();
        final List<Button> flip2 = flips(cuffDenied);
        check(flip2.size() == 1, "denied CUFF must still show exactly one flip button");
        check(!flip2.get(0).enabled(), "CUFF without capability must stay disabled");
    }

    void testNoSelectionDisablesFlip() {
        final GovJailPanel p = newPanel(640, 16);
        p.rebuildButtons();
        final List<Button> flip = flips(p);
        check(flip.size() <= 1, "no selection must show at most one flip button");
        for (final Button b : flip) {
            check(!b.enabled(), "flip with no selection must be disabled");
            check(b.click() == Click.CUFF, "no selection must default to the CUFF label");
        }
    }

    void testMutationPendingDisablesFlip() {
        final GovJailPanel p = newPanel(640, 16);
        selectRow(p, false, true, false);
        p.pending.put("dummy-headless-block", RequestKind.MUTATE);
        p.rebuildButtons();
        final List<Button> flip = flips(p);
        check(flip.size() == 1, "pending mutation must keep exactly one flip button");
        check(!flip.get(0).enabled(), "flip must disable while a mutation is in flight");
    }

    void testSingleFlipAcrossWidths() {
        final int[] widths = {640, 320, 240, 120};
        final int[] heights = {16, 14};
        for (final int w : widths) {
            for (final int h : heights) {
                final GovJailPanel loose = newPanel(w, h);
                selectRow(loose, false, true, false);
                loose.rebuildButtons();
                check(flips(loose).size() == 1,
                        "uncuffed " + w + "x" + h + " must show one flip button");
                final GovJailPanel cuffed = newPanel(w, h);
                selectRow(cuffed, true, false, true);
                cuffed.rebuildButtons();
                check(flips(cuffed).size() == 1,
                        "cuffed " + w + "x" + h + " must show one flip button");
            }
        }
    }

    private static boolean overlaps(final GovRect a, final GovRect b) {
        return a.x() < b.right() && b.x() < a.right();
    }

    void testTopBarNoOverlap() {
        // At sane widths the freed slot is absorbed by PUT IN CELL: filter, cell-put
        // and flip must tile the bar without overlapping at both bar heights.
        final int[] widths = {640, 320, 240};
        final int[] heights = {16, 14};
        for (final int w : widths) {
            for (final int h : heights) {
                final GovJailPanel p = newPanel(w, h);
                selectRow(p, false, true, false);
                p.rebuildButtons();
                final GovRect bar = p.topBars[0];
                final GovRect filter = p.buttonRect(Click.FILTER);
                final GovRect cell = p.buttonRect(Click.CELL);
                final GovRect flip = flips(p).get(0).rect();
                check(filter != null && cell != null, "top bar buttons missing");
                for (final GovRect r : new GovRect[] {filter, cell, flip}) {
                    check(r.x() >= bar.x() && r.right() <= bar.right(),
                            "button outside bar at " + w + "x" + h);
                }
                check(!overlaps(filter, cell), "filter/cell overlap at " + w + "x" + h);
                check(!overlaps(filter, flip), "filter/flip overlap at " + w + "x" + h);
                check(!overlaps(cell, flip), "cell/flip overlap at " + w + "x" + h);
            }
        }
    }

    void testSourceHasSingleFlipSite() throws Exception {
        final String source = Files.readString(
                Path.of("src/main/java/com/horizonssmp/gov/client/GovJailPanelPart05.java"),
                StandardCharsets.UTF_8);
        check(source.contains("cuffed ? Click.UNCUFF : Click.CUFF"),
                "flip dispatch ternary missing from Part05");
        check(source.contains("cuffed ? this.buttonUncuff : this.buttonCuff"),
                "flip label ternary missing from Part05");
        int cuffSites = 0;
        int index = 0;
        while ((index = source.indexOf("Click.CUFF", index)) >= 0) {
            cuffSites++;
            index += 1;
        }
        check(cuffSites == 1, "expected one Click.CUFF site, found " + cuffSites);
        int uncuffSites = 0;
        index = 0;
        while ((index = source.indexOf("Click.UNCUFF", index)) >= 0) {
            uncuffSites++;
            index += 1;
        }
        check(uncuffSites == 1, "expected one Click.UNCUFF site, found " + uncuffSites);

        final String handlers = Files.readString(
                Path.of("src/main/java/com/horizonssmp/gov/client/GovJailPanelPart08.java"),
                StandardCharsets.UTF_8);
        check(handlers.contains("case CUFF -> mutate(OpCode.CUFF,"),
                "CUFF opcode dispatch changed in Part08");
        check(handlers.contains("case UNCUFF -> mutate(OpCode.UNCUFF,"),
                "UNCUFF opcode dispatch changed in Part08");
    }

    public static void main(final String[] args) {
        final CuffFlipGateTest t = new CuffFlipGateTest();
        int passed = 0;
        int failed = 0;
        final String[] names = {"testUncuffedShowsCuff", "testCuffedShowsUncuff",
            "testFlipGateDeniesWithoutCapability", "testNoSelectionDisablesFlip",
            "testMutationPendingDisablesFlip", "testSingleFlipAcrossWidths",
            "testTopBarNoOverlap", "testSourceHasSingleFlipSite"};
        for (final String name : names) {
            try {
                CuffFlipGateTest.class.getDeclaredMethod(name).invoke(t);
                passed++;
                System.out.println("PASS " + name);
            } catch (final Throwable e) {
                failed++;
                final Throwable cause = e.getCause() == null ? e : e.getCause();
                System.out.println("FAIL " + name + ": " + cause.getMessage());
            }
        }
        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            throw new AssertionError(failed + " failures");
        }
    }
}
