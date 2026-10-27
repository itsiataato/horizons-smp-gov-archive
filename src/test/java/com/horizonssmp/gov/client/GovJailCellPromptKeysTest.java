package com.horizonssmp.gov.client;

import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import com.horizonssmp.gov.client.GovJailTypes.JailRow;
import com.horizonssmp.gov.client.GovJailTypes.RequestKind;

/**
 * Headless key-routing suites for the TASK-025 prompt wiring.
 *
 * <p>Runs via {@code main()}, no client needed. These tests call the real
 * {@code GovJailPanel.keyPressed} (via the Part11 override, which delegates to
 * Part09 for everything but an armed Cells delete), so they go red if the
 * Part09 {@code cellPromptOpen} branch is ever removed: Enter would no-op
 * instead of confirming, and Esc would fall through toward the dashboard
 * {@code onClose} instead of cancelling.
 */
final class GovJailCellPromptKeysTest {

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static GovJailPanel newPanel() {
        final GovJailPanel p = new GovJailPanel(null);
        p.active = true;
        p.subtab = 1;
        p.selectedId = "";
        return p;
    }

    private static void addRow(final GovJailPanel p, final String id, final String name) {
        p.rows.add(new JailRow(id, name, "", "consent", "Consent", "Sam", 0L, 0L,
                "", false, "ACTIVE", "Held", "", true, name, "held", false, 0, ""));
    }

    private static void stagePrompt(final GovJailPanel p, final String entry) {
        p.cellPromptOpen = true;
        p.cellPromptMax = GovJailCellPrompt.maxCell(0);
        final GovThemedEditBox box = new GovThemedEditBox(null, 0, 0, 50, 9,
                Component.empty());
        box.setValue(entry);
        p.cellPromptBox = box;
    }

    void testEnterRoutesToConfirm() {
        final GovJailPanel p = newPanel();
        addRow(p, "d-1", "Alex");
        p.selectedId = "d-1";
        stagePrompt(p, "3");
        // Block the network leaf headless, as in GovJailCellPromptTest: confirm still
        // stages the entered value and closes the prompt before the send.
        p.pending.clear();
        p.pending.put("dummy-headless-block", RequestKind.MUTATE);
        final boolean consumed = p.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        check(consumed, "Enter with prompt open was not consumed");
        check(p.cellNumber == 3, "Enter did not route to confirm (cell still "
                + p.cellNumber + ")");
        check(!p.cellPromptOpen, "Enter left the prompt open");
    }

    void testEscRoutesToClose() {
        final GovJailPanel p = newPanel();
        addRow(p, "d-1", "Alex");
        p.selectedId = "d-1";
        stagePrompt(p, "4");
        p.pending.clear();
        final boolean consumed = p.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        check(consumed, "Esc with prompt open was not consumed (falls to onClose)");
        check(!p.cellPromptOpen, "Esc did not route to close");
        check(p.cellNumber == 1, "Esc staged a cell");
        check(p.pending.isEmpty(), "Esc sent a request");
    }

    void testPromptSwallowsListKeys() {
        final GovJailPanel p = newPanel();
        addRow(p, "d-1", "Alex");
        addRow(p, "d-2", "Bo");
        p.selectedId = "d-1";
        stagePrompt(p, "3");
        p.pending.clear();
        check(p.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0), "Down with prompt open fell through");
        check(p.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0),
                "PageDown with prompt open fell through");
        check(p.selectedId.equals("d-1"), "list moved behind the open prompt");
        check(p.cellPromptOpen, "list key closed the prompt");
        check(p.pending.isEmpty(), "list key sent a request");
    }

    public static void main(final String[] args) {
        final GovJailCellPromptKeysTest t = new GovJailCellPromptKeysTest();
        int passed = 0;
        int failed = 0;

        try { t.testEnterRoutesToConfirm(); passed++;
            System.out.println("PASS testEnterRoutesToConfirm");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testEnterRoutesToConfirm: " + e.getMessage());
        }
        try { t.testEscRoutesToClose(); passed++;
            System.out.println("PASS testEscRoutesToClose");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testEscRoutesToClose: " + e.getMessage());
        }
        try { t.testPromptSwallowsListKeys(); passed++;
            System.out.println("PASS testPromptSwallowsListKeys");
        } catch (final Throwable e) { failed++;
            System.out.println("FAIL testPromptSwallowsListKeys: " + e.getMessage());
        }

        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            throw new AssertionError(failed + " failures");
        }
    }
}
