package com.horizonssmp.gov.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import static com.horizonssmp.gov.client.GovJailTypes.*;
import static com.horizonssmp.gov.client.GovJailConstants.*;

/**
 * The PUT IN CELL number prompt on the Detentions subtab.
 *
 * <p>PUT IN CELL (Click.CELL) stages nothing itself: it opens this modal, which asks for a cell
 * number from 1 to N and only then submits. N is {@code max(20, runtime cell count)} so the old
 * 1..20 cycler range is preserved and strictly expands when more runtime cells exist. The server
 * stays authoritative: {@code putInCell} still rejects anything outside 1..allCells.size().
 *
 * <p>The prompt borrows the shared {@code buttons} list with Click.CELL_CONFIRM/CELL_CANCEL, so
 * {@code rebuildButtons()} re-lays the modal on every resize instead of wiping it. Confirm reads
 * the box text inline (Part08 cannot see Part09's {@code value()}), stages
 * {@code cellNumber}, and reuses the unchanged no-arg {@code submitCell()}, whose abstract
 * declaration lives outside this task's file contract.
 */
final class GovJailCellPrompt {

    static final int FALLBACK_MAX = 20;
    static final int MODAL_W = 320;
    static final int MODAL_H = 160;
    static final int MIN_MODAL_W = 180;
    static final int MIN_MODAL_H = 100;

    private GovJailCellPrompt() {
    }

    /** Upper bound N: the old 1..20 range, expanding with the runtime cell count. */

    static int maxCell(final int runtimeCount) {
        return Math.max(FALLBACK_MAX, runtimeCount);
    }

    /**
     * Parses a prompt entry; null when empty, non-numeric or outside 1..max. The box filter
     * already allows digits only, so this is the pasted-input and headless-matrix backstop.
     */

    static Integer parseCell(final String raw, final int max) {
        if (raw == null) {
            return null;
        }
        final String text = raw.strip();
        if (text.isEmpty() || !text.matches("\\d+")) {
            return null;
        }
        try {
            final int value = Integer.parseInt(text);
            return value >= 1 && value <= max ? value : null;
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    static int runtimeCount(final GovJailPanelBase panel) {
        if (panel instanceof GovJailPanelPart10 part) {
            return Math.max(0, part.cellCount);
        }
        return 0;
    }

    /** Opens the prompt, defaulting the field to the last confirmed cell number. */

    static void open(final GovJailPanelPart06 panel) {
        if (panel.font == null) {
            return;
        }
        panel.cellPromptMax = maxCell(runtimeCount(panel));
        ensureBox(panel);
        if (panel.cellPromptBox == null) {
            return;
        }
        panel.cellPromptOpen = true;
        panel.setValue(panel.cellPromptBox, Integer.toString(panel.cellNumber));
        panel.status = "";
        panel.statusIsError = false;
        layoutPanel(panel);
        if (!panel.cellPromptOpen) {
            return;
        }
        setVisible(panel, true);
        if (panel.host != null) {
            panel.host.setFocused(panel.cellPromptBox);
        }
        panel.rebuildButtons();
    }

    /**
     * Validates the entry without {@code fail()}: failing would disarm and rebuild around a
     * prompt that must stay open, so the message is set directly on the status line instead.
     */

    static void confirm(final GovJailPanelPart06 panel) {
        if (!panel.cellPromptOpen) {
            return;
        }
        final String raw = panel.cellPromptBox == null
                ? "" : panel.cellPromptBox.getValue().strip();
        final Integer value = parseCell(raw, panel.cellPromptMax);
        if (value == null) {
            panel.status = tr("gui.gov.jail.cell_prompt_invalid", panel.cellPromptMax);
            panel.statusIsError = true;
            return;
        }
        panel.cellNumber = value;
        close(panel);
        panel.submitCell();
    }

    /** Closes the prompt; a no-op without a rebuild when already closed. */

    static void close(final GovJailPanelPart06 panel) {
        if (!panel.cellPromptOpen) {
            return;
        }
        panel.cellPromptOpen = false;
        hideBox(panel);
        panel.rebuildButtons();
    }

    static void hideBox(final GovJailPanelBase panel) {
        if (panel.cellPromptBox != null) {
            panel.cellPromptBox.setVisible(false);
            if (panel.host != null && panel.host.getFocused() == panel.cellPromptBox) {
                panel.host.setFocused(null);
            }
        }
    }

    static void ensureBox(final GovJailPanelBase panel) {
        if (panel.cellPromptBox != null || panel.font == null) {
            return;
        }
        final EditBox box = GovWidgets.field(panel.font, 0, 0, 10, 9, Component.empty());
        box.setMaxLength(5);
        box.setFilter(value -> value != null && value.matches("\\d*"));
        box.setVisible(false);
        panel.cellPromptBox = box;
        if (panel.host instanceof GovDashboardScreen screen) {
            screen.addGovWidget(box);
        }
        final List<EditBox> all = new ArrayList<>(panel.widgets);
        all.add(box);
        panel.widgets = List.copyOf(all);
    }

    static void setVisible(final GovJailPanelPart06 panel, final boolean visible) {
        if (panel.cellPromptBox == null) {
            return;
        }
        panel.cellPromptBox.setVisible(visible);
        if (!visible && panel.host != null
                && panel.host.getFocused() == panel.cellPromptBox) {
            panel.host.setFocused(null);
        }
    }

    /**
     * Rebuild entry called at the end of {@code rebuildButtons()}: re-lays the modal geometry
     * and appends Confirm/Cancel after the main buttons. A degenerate container auto-closes.
     */

    static void rebuild(final GovJailPanelPart05 panel) {
        if (!panel.cellPromptOpen) {
            return;
        }
        layoutPanel(panel);
        if (!panel.cellPromptOpen || panel.font == null) {
            return;
        }
        final GovRect modal = panel.cellPromptRect;
        final int buttonH = 16;
        final int y = modal.bottom() - 6 - buttonH;
        final String confirm = caps(tr("gui.gov.jail.confirm_prefix"));
        final String cancel = caps(tr("gui.gov.jail.cancel"));
        final int confirmW = GovMetrics.span(modal.w(), panel.font.width(confirm) + 16,
                56, 110);
        final int cancelW = GovMetrics.span(modal.w(), panel.font.width(cancel) + 16,
                52, 96);
        final int confirmX = modal.right() - 6 - confirmW;
        panel.addButton(confirmX, y, confirmW, buttonH, Click.CELL_CONFIRM, "", confirm,
                !panel.mutationPending(), true, false);
        panel.addButton(confirmX - 4 - cancelW, y, cancelW, buttonH, Click.CELL_CANCEL, "",
                cancel, true, false, false);
    }

    static void layoutPanel(final GovJailPanelPart05 panel) {
        if (!panel.cellPromptOpen) {
            return;
        }
        panel.cellPromptRect = GovMetrics.fit(panel.bounds, MODAL_W, MODAL_H);
        final GovRect modal = panel.cellPromptRect;
        if (modal.w() < MIN_MODAL_W || modal.h() < MIN_MODAL_H) {
            panel.cellPromptOpen = false;
            hideBox(panel);
            panel.rebuildButtons();
            return;
        }
        final int pad = 6;
        final int fieldY = modal.y() + 34;
        panel.cellPromptField = panel.rect(modal.x() + pad, fieldY,
                Math.max(0, modal.w() - pad * 2), 16);
        panel.cellPromptHint = panel.labelCell + " 1-" + panel.cellPromptMax;
        placePromptBox(panel);
    }

    static void placePromptBox(final GovJailPanelPart05 panel) {
        final EditBox box = panel.cellPromptBox;
        final GovRect frame = panel.cellPromptField;
        if (box == null || frame.w() <= 0 || frame.h() <= 0) {
            return;
        }
        box.setX(frame.x() + 3);
        box.setY(frame.y() + 4);
        box.setWidth(Math.max(0, frame.w() - 6));
    }

    /**
     * Modal input: Confirm/Cancel hits are returned for the caller to dispatch (the helper is
     * typed at Part06 and cannot see Part08's {@code handleClick}); every other click is
     * swallowed so the list behind the modal stays inert.
     */

    static Click click(final GovJailPanelPart05 panel, final double mouseX,
            final double mouseY) {
        for (final Button entry : panel.buttons) {
            final Click kind = entry.click();
            if ((kind == Click.CELL_CONFIRM || kind == Click.CELL_CANCEL) && entry.enabled()
                    && entry.rect().contains(mouseX, mouseY)) {
                return kind;
            }
        }
        if (panel.cellPromptBox != null && panel.cellPromptBox.visible
                && panel.cellPromptField.contains(mouseX, mouseY)) {
            if (panel.host != null) {
                panel.host.setFocused(panel.cellPromptBox);
            }
        } else if (panel.host != null && panel.host.getFocused() == panel.cellPromptBox) {
            panel.host.setFocused(null);
        }
        return null;
    }

    /** Paints the modal over the detentions surface; a no-op unless the prompt is open. */

    static void paint(final GovJailPanelPart05 panel, final GuiGraphics graphics,
            final int mouseX, final int mouseY) {
        if (!panel.cellPromptOpen || panel.font == null) {
            return;
        }
        final GovRect modal = panel.cellPromptRect;
        if (modal.w() <= 0 || modal.h() <= 0) {
            return;
        }
        GovWidgets.modalBackdrop(graphics, panel.bounds);
        GovWidgets.modalFrame(graphics, panel.font, modal, panel.buttonPutCell);
        GovText.clipped(graphics, panel.font, panel.cellPromptHint, modal.x() + 6,
                modal.y() + 22, Math.max(0, modal.w() - 12), GovTheme.MUTED);
        final boolean focused = panel.cellPromptBox != null
                && panel.cellPromptBox.isFocused();
        GovWidgets.fieldFrame(graphics, panel.cellPromptField, focused);
        for (final Button entry : panel.buttons) {
            final Click kind = entry.click();
            if (kind != Click.CELL_CONFIRM && kind != Click.CELL_CANCEL) {
                continue;
            }
            final boolean hovered = entry.rect().contains(mouseX, mouseY);
            GovWidgets.button(graphics, panel.font, entry.rect(), entry.label(),
                    entry.enabled(), entry.enabled() && hovered,
                    entry.primary() || entry.selected());
        }
    }
}
