package com.horizonssmp.gov.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.network.chat.Component;

import com.horizonssmp.gov.client.GovJailTypes.Click;
import com.horizonssmp.gov.client.GovJailTypes.JailRow;
import com.horizonssmp.gov.client.GovJailTypes.LoadState;
import com.horizonssmp.gov.client.GovJailTypes.RequestKind;

/** Headless suites for the TASK-020 cell prompt. Runs via {@code main()}, no client needed. */
final class GovJailCellPromptTest {

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static GovJailPanel newPanel() {
        final GovJailPanel p = new GovJailPanel(null);
        p.selectedId = "";
        return p;
    }

    private static void selectDetention(final GovJailPanel p) {
        p.rows.add(new JailRow("d-1", "Alex", "", "consent", "Consent", "Sam", 0L, 0L,
                "", false, "ACTIVE", "Held", "", true, "Alex", "held", false, 0, ""));
        p.selectedId = "d-1";
    }

    private static void stagePrompt(final GovJailPanel p, final String entry) {
        p.cellPromptOpen = true;
        p.cellPromptMax = GovJailCellPrompt.maxCell(0);
        final GovThemedEditBox box = new GovThemedEditBox(null, 0, 0, 50, 9,
                Component.empty());
        box.setValue(entry);
        p.cellPromptBox = box;
    }

    void testMaxCellFallback() {
        check(GovJailCellPrompt.FALLBACK_MAX == 20, "fallback changed");
        check(GovJailCellPrompt.maxCell(0) == 20, "empty runtime count must keep 1..20");
        check(GovJailCellPrompt.maxCell(5) == 20, "small runtime count must keep 1..20");
        check(GovJailCellPrompt.maxCell(20) == 20, "boundary changed");
        check(GovJailCellPrompt.maxCell(37) == 37, "larger runtime count must expand N");
        check(GovJailCellPrompt.maxCell(-3) == 20, "negative count must fall back");
    }

    void testParseValid() {
        check(Integer.valueOf(1).equals(GovJailCellPrompt.parseCell("1", 20)), "'1' rejected");
        check(Integer.valueOf(20).equals(GovJailCellPrompt.parseCell("20", 20)), "'20' rejected");
        check(Integer.valueOf(7).equals(GovJailCellPrompt.parseCell("007", 20)), "'007' != 7");
        check(Integer.valueOf(3).equals(GovJailCellPrompt.parseCell(" 3 ", 20)), "padded '3'");
        check(Integer.valueOf(21).equals(GovJailCellPrompt.parseCell("21", 37)), "'21' of 37");
    }

    void testParseInvalid() {
        check(GovJailCellPrompt.parseCell(null, 20) == null, "null accepted");
        check(GovJailCellPrompt.parseCell("", 20) == null, "empty accepted");
        check(GovJailCellPrompt.parseCell("   ", 20) == null, "blank accepted");
        check(GovJailCellPrompt.parseCell("0", 20) == null, "zero accepted");
        check(GovJailCellPrompt.parseCell("21", 20) == null, "over-max accepted");
        check(GovJailCellPrompt.parseCell("-1", 20) == null, "negative accepted");
        check(GovJailCellPrompt.parseCell("3.5", 20) == null, "decimal accepted");
        check(GovJailCellPrompt.parseCell("abc", 20) == null, "text accepted");
        check(GovJailCellPrompt.parseCell("12a", 20) == null, "alphanumeric accepted");
        check(GovJailCellPrompt.parseCell("99999999999999999999", 20) == null,
                "overflowing digits accepted");
    }

    void testCellIncGone() throws Exception {
        final String[] paths = {
            "src/main/java/com/horizonssmp/gov/client/GovJailPanelPart05.java",
            "src/main/java/com/horizonssmp/gov/client/GovJailPanelPart08.java",
            "src/main/java/com/horizonssmp/gov/client/GovJailTypes.java",
            "src/main/java/com/horizonssmp/gov/client/GovJailPanelBase.java"
        };
        for (final String path : paths) {
            final String source = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            check(!source.contains("CELL_INC"), "CELL_INC still referenced in " + path);
        }
    }

    void testSubmitRoutesStagedCell() throws Exception {
        final String source = Files.readString(
                Path.of("src/main/java/com/horizonssmp/gov/client/GovJailPanelPart06.java"),
                StandardCharsets.UTF_8);
        check(source.contains("KEY_CELL") && source.contains("cellNumber"),
                "submitCell no longer sends the staged KEY_CELL cellNumber");
    }

    void testInvalidConfirmKeepsPrompt() {
        final GovJailPanel p = newPanel();
        selectDetention(p);
        stagePrompt(p, "99");
        p.pending.clear();
        GovJailCellPrompt.confirm(p);
        check(p.cellPromptOpen, "invalid entry closed the prompt");
        check(p.statusIsError, "invalid entry set no error status");
        check(!p.status.isEmpty(), "invalid entry set an empty message");
        check(p.cellNumber == 1, "invalid entry overwrote the staged cell");
        check(!p.pending.containsValue(RequestKind.MUTATE), "invalid entry sent JAIL_CELL");
    }

    void testEmptyConfirmSendsNothing() {
        final GovJailPanel p = newPanel();
        selectDetention(p);
        stagePrompt(p, "");
        p.pending.clear();
        GovJailCellPrompt.confirm(p);
        check(p.cellPromptOpen, "empty entry closed the prompt");
        check(p.statusIsError, "empty entry set no error status");
        check(!p.pending.containsValue(RequestKind.MUTATE), "empty entry sent JAIL_CELL");
    }

    void testValidConfirmStagesAndSends() {
        final GovJailPanel p = newPanel();
        selectDetention(p);
        stagePrompt(p, "3");
        // Block the network leaf headless: PacketDistributor.sendToServer NPEs without a
        // loaded FMLEnvironment, so a dummy MUTATE makes mutate() return before the send
        // while confirm() still stages the entered value and closes the prompt. The
        // staged-value-to-KEY_CELL routing itself is pinned by testSubmitRoutesStagedCell,
        // and the live round trip needs tester coverage in-game.
        p.pending.clear();
        p.pending.put("dummy-headless-block", RequestKind.MUTATE);
        GovJailCellPrompt.confirm(p);
        check(p.cellNumber == 3, "confirm did not stage the entered cell");
        check(!p.cellPromptOpen, "confirm left the prompt open");
        check(p.pending.size() == 1 && p.pending.containsKey("dummy-headless-block"),
                "confirm sent a request past the headless network block");
    }

    void testCancelClosesWithoutSending() {
        final GovJailPanel p = newPanel();
        selectDetention(p);
        stagePrompt(p, "4");
        p.pending.clear();
        GovJailCellPrompt.close(p);
        check(!p.cellPromptOpen, "cancel left the prompt open");
        check(p.cellNumber == 1, "cancel staged a cell");
        check(p.pending.isEmpty(), "cancel sent a request");
    }

    void testClickDispatch() {
        final GovJailPanel p = newPanel();
        p.cellPromptOpen = true;
        p.cellPromptMax = GovJailCellPrompt.maxCell(0);
        p.bounds = new GovRect(0, 0, 640, 400);
        p.cellPromptRect = GovMetrics.fit(p.bounds, GovJailCellPrompt.MODAL_W,
                GovJailCellPrompt.MODAL_H);
        final GovRect modal = p.cellPromptRect;
        p.buttons.clear();
        p.buttons.add(new GovJailTypes.Button(new GovRect(modal.right() - 70,
                modal.bottom() - 22, 64, 16), Click.CELL_CANCEL, "", "CANCEL", true,
                false, false));
        final Click hit = GovJailCellPrompt.click(p, modal.right() - 40,
                modal.bottom() - 14);
        check(hit == Click.CELL_CANCEL, "cancel button click not routed");
        final Click miss = GovJailCellPrompt.click(p, modal.x() + modal.w() / 2,
                modal.y() + modal.h() / 2);
        check(miss == null, "modal-body click dispatched a button");
    }

    public static void main(final String[] args) {
        final GovJailCellPromptTest t = new GovJailCellPromptTest();
        int passed = 0;
        int failed = 0;

        try { t.testMaxCellFallback(); passed++;
            System.out.println("PASS testMaxCellFallback");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testMaxCellFallback: " + e.getMessage());
        }
        try { t.testParseValid(); passed++;
            System.out.println("PASS testParseValid");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testParseValid: " + e.getMessage());
        }
        try { t.testParseInvalid(); passed++;
            System.out.println("PASS testParseInvalid");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testParseInvalid: " + e.getMessage());
        }
        try { t.testCellIncGone(); passed++;
            System.out.println("PASS testCellIncGone");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testCellIncGone: " + e.getMessage());
        }
        try { t.testSubmitRoutesStagedCell(); passed++;
            System.out.println("PASS testSubmitRoutesStagedCell");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testSubmitRoutesStagedCell: " + e.getMessage());
        }
        try { t.testInvalidConfirmKeepsPrompt(); passed++;
            System.out.println("PASS testInvalidConfirmKeepsPrompt");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testInvalidConfirmKeepsPrompt: " + e.getMessage());
        }
        try { t.testEmptyConfirmSendsNothing(); passed++;
            System.out.println("PASS testEmptyConfirmSendsNothing");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testEmptyConfirmSendsNothing: " + e.getMessage());
        }
        try { t.testValidConfirmStagesAndSends(); passed++;
            System.out.println("PASS testValidConfirmStagesAndSends");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testValidConfirmStagesAndSends: " + e);
        }
        try { t.testCancelClosesWithoutSending(); passed++;
            System.out.println("PASS testCancelClosesWithoutSending");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testCancelClosesWithoutSending: " + e.getMessage());
        }
        try { t.testClickDispatch(); passed++;
            System.out.println("PASS testClickDispatch");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testClickDispatch: " + e.getMessage());
        }

        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            throw new AssertionError(failed + " failures");
        }
    }
}
