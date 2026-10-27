package com.horizonssmp.gov.client;

import static com.horizonssmp.gov.client.GovTreasuryTypes.*;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * The Balances-tab bank-credit form: target plus amount plus reason.
 *
 * <p>The dashboard parity path for {@code /gov bank credit}. The form carves three rows from
 * the top of the balances notes area and the notes shrink to the remainder; every other
 * subtab is button-only, so these are the only text fields the treasury panel owns. The
 * boxes follow the jail lifecycle: created once through {@link GovWidgets#field}, placed
 * with the jail {@code placeBox} offsets, hidden (and defocused) whenever the section is
 * not the active Balances tab, and cleared by reset.
 *
 * <p>There is no client-side validation here on purpose: every submit goes to the server,
 * so {@code FORBIDDEN}, {@code BAD_TARGET}, {@code BAD_AMOUNT} and the short-reason
 * {@code BAD_PAYLOAD} refusals surface verbatim through the shared status line. The reason
 * box caps at 128 characters, the server {@code AccountService.MAX_REASON_LENGTH} the
 * console-credit path enforces (named here as a literal so the client never imports a
 * server class for a length).
 */
final class GovBankCreditForm {

    /** Server reason ceiling, mirrored as a literal; see the class javadoc. */
    static final int MAX_REASON = 128;

    /** Minecraft player-name ceiling for the target hint box. */
    static final int MAX_TARGET = 16;

    /** Enough room for any whole-ingot long the server parses. */
    static final int MAX_AMOUNT = 19;

    EditBox targetBox;
    EditBox amountBox;
    EditBox reasonBox;

    GovRect formRect = NONE;
    GovRect targetRect = NONE;
    GovRect amountRect = NONE;
    GovRect placeRect = NONE;
    GovRect reasonRect = NONE;

    static List<EditBox> createWidgets(final GovTreasuryPanel p, final Font font) {
        final GovBankCreditForm form = p.credit;
        if (form.targetBox == null) {
            form.targetBox = box(font, MAX_TARGET, "gui.gov.treasury.credit_target_hint");
            form.amountBox = box(font, MAX_AMOUNT, "gui.gov.treasury.credit_amount_hint");
            form.reasonBox = box(font, MAX_REASON, "gui.gov.treasury.credit_reason_hint");
        }
        if (p.area.w() > 0 && p.area.h() > 0) {
            position(p);
        }
        sync(p);
        return List.of(form.targetBox, form.amountBox, form.reasonBox);
    }

    static EditBox box(final Font font, final int maxLength, final String hintKey) {
        final EditBox box = GovWidgets.field(font, 0, 0, 10, 9,
                Component.translatable(hintKey));
        box.setTextShadow(false);
        box.setMaxLength(maxLength);
        box.setVisible(false);
        return box;
    }

    /** Carves three rows from the top of the notes area; the notes keep the remainder. */
    static void compute(final GovTreasuryPanel p) {
        final GovBankCreditForm form = p.credit;
        if (!p.canCredit) {
            zero(p);
            return;
        }
        final GovRect notes = p.balanceNotesRect;
        final int rowH = BUTTON_H;
        final int gap = 2;
        final int formH = rowH * 3 + gap * 2;
        if (notes.w() <= 0 || notes.h() < formH + LINE) {
            zero(p);
            return;
        }
        final int x = notes.x();
        int y = notes.y();
        form.formRect = GovTreasuryGeometry.rect(p, x, y, notes.w(), formH);
        form.targetRect = GovTreasuryGeometry.rect(p, x, y, notes.w(), rowH);
        y += rowH + gap;
        final int placeW = Math.min(64, Math.max(0, notes.w() / 3));
        final int amountW = Math.max(0, notes.w() - placeW - gap);
        form.amountRect = GovTreasuryGeometry.rect(p, x, y, amountW, rowH);
        form.placeRect = GovTreasuryGeometry.rect(p, x + amountW + gap, y, placeW, rowH);
        y += rowH + gap;
        form.reasonRect = GovTreasuryGeometry.rect(p, x, y, notes.w(), rowH);
        final int notesTop = form.formRect.bottom() + 4;
        p.balanceNotesRect = GovTreasuryGeometry.rect(p, x, notesTop, notes.w(),
                Math.max(0, p.balanceStatusRect.y() - 3 - notesTop));
        position(p);
        sync(p);
    }

    /** Clears every credit rectangle and hides the boxes; the values survive layout. */
    static void zero(final GovTreasuryPanel p) {
        final GovBankCreditForm form = p.credit;
        form.formRect = NONE;
        form.targetRect = NONE;
        form.amountRect = NONE;
        form.placeRect = NONE;
        form.reasonRect = NONE;
        sync(p);
    }

    /** Forgets the typed values and drops field focus; the next visit starts blank. */
    static void reset(final GovTreasuryPanel p) {
        final GovBankCreditForm form = p.credit;
        if (form.targetBox != null) {
            form.targetBox.setValue("");
        }
        if (form.amountBox != null) {
            form.amountBox.setValue("");
        }
        if (form.reasonBox != null) {
            form.reasonBox.setValue("");
        }
        clearFocus(p);
        sync(p);
    }

    static void position(final GovTreasuryPanel p) {
        final GovBankCreditForm form = p.credit;
        placeBox(form.targetBox, form.targetRect);
        placeBox(form.amountBox, form.amountRect);
        placeBox(form.reasonBox, form.reasonRect);
    }

    static void placeBox(final EditBox box, final GovRect rect) {
        if (box == null) {
            return;
        }
        box.setX(rect.x() + 3);
        box.setY(rect.y() + 4);
        box.setWidth(Math.max(0, rect.w() - 6));
    }

    /** Shows the boxes only on the active Balances tab with a laid-out form. */
    static void sync(final GovTreasuryPanel p) {
        final GovBankCreditForm form = p.credit;
        final boolean show = p.canCredit && p.active && p.subtab == TAB_BALANCES
                && form.formRect.w() > 0 && form.formRect.h() > 0;
        setVisible(p, form.targetBox, show);
        setVisible(p, form.amountBox, show);
        setVisible(p, form.reasonBox, show);
    }

    static void setVisible(final GovTreasuryPanel p, final EditBox box,
            final boolean visible) {
        if (box == null) {
            return;
        }
        box.setVisible(visible);
        if (!visible && p.host.getFocused() == box) {
            p.host.setFocused(null);
        }
    }

    /** Drops field focus so Esc routes field first, then the dashboard. */
    static boolean clearFocus(final GovTreasuryPanel p) {
        final GovBankCreditForm form = p.credit;
        final EditBox[] boxes = {form.targetBox, form.amountBox, form.reasonBox};
        for (final EditBox box : boxes) {
            if (box != null && p.host.getFocused() == box) {
                p.host.setFocused(null);
                return true;
            }
        }
        return false;
    }

    /** Draws the field frames; the screen draws the boxes themselves. */
    static void drawForm(final GovTreasuryPanel p, final GuiGraphics graphics) {
        final GovBankCreditForm form = p.credit;
        if (!p.canCredit) {
            return;
        }
        if (form.formRect.w() <= 0 || form.formRect.h() <= 0) {
            return;
        }
        drawFrame(p, graphics, form.targetRect, form.targetBox);
        drawFrame(p, graphics, form.amountRect, form.amountBox);
        drawFrame(p, graphics, form.reasonRect, form.reasonBox);
    }

    static void drawFrame(final GovTreasuryPanel p, final GuiGraphics graphics,
            final GovRect rect, final EditBox box) {
        if (rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        GovWidgets.fieldFrame(graphics, rect, p.host.getFocused() == box);
    }
}
