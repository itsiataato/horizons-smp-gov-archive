package com.horizonssmp.gov.client;


import com.horizonssmp.gov.client.GovDashboardScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.BusinessKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.horizonssmp.gov.client.GovBusinessesTypes.*;
import static com.horizonssmp.gov.client.GovBusinessesTypes.*;

final class GovBusinessesRenderStaff {

    static     void drawStaff(final GovBusinessesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.staffList.w() <= 0 || p.staffList.h() <= 0) {
            return;
        }
        graphics.fill(p.staffList.x(), p.staffList.y(), p.staffList.right(),
                p.staffList.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, p.staffList, GovTheme.BORDER);
        graphics.drawString(p.font, p.staffHeaderText, p.staffHeader.x(),
                p.staffHeader.y() + 1, GovTheme.MUTED, false);
        GovWidgets.rule(graphics, p.staffHeader.x(), p.staffHeader.bottom() - 1,
                p.staffHeader.w(), GovTheme.BORDER_SOFT);

        if (p.selectedId.isEmpty() || p.detailState == LoadState.NONE) {
            GovBusinessesRender2.drawEmpty(p, graphics, p.staffRows, p.noSelectionText, "");
        } else if (p.detailState == LoadState.LOADING) {
            GovBusinessesRender2.drawEmpty(p, graphics, p.staffRows, p.loadingText, "");
        } else if (p.detailState == LoadState.ERROR) {
            GovBusinessesRender2.drawEmpty(p, graphics, p.staffRows,
                    p.detailErrorTitle, p.staffErrorText);
            if (p.staffRetry.w() > 0) {
                GovWidgets.button(graphics, p.font, p.staffRetry, p.retryText,
                        true, p.staffRetry.contains(mouseX, mouseY), false);
            }
        } else if (p.staffViews.isEmpty()) {
            GovBusinessesRender2.drawEmpty(p, graphics, p.staffRows, p.noStaffText, "");
        } else {
            final boolean scissored = GovBusinessesGeometry.clipRows(graphics, p.staffRows);
            try {
                for (int index = 0; index < p.staffViews.size(); index++) {
                    final int y = p.staffRows.y() + index * p.staffPitch
                            - p.staffScroll;
                    if (y >= p.staffRows.bottom()) {
                        break;
                    }
                    if (y < p.staffRows.y()
                            || y + p.staffPitch > p.staffRows.bottom()) {
                        continue;
                    }
                    final StaffView view = p.staffViews.get(index);
                    final boolean hovered = GovTheme.inside(mouseX, mouseY,
                            p.staffList.x() + 2, y, Math.max(0, p.staffList.w() - 4),
                            p.staffPitch);
                    if (hovered) {
                        graphics.fill(p.staffList.x() + 2, y,
                                p.staffList.right() - 2, y + p.staffPitch,
                                GovTheme.HOVER);
                    }
                    graphics.drawString(p.font, view.line1(), p.staffList.x() + 8,
                            y + 2, GovTheme.BODY, false);
                    graphics.drawString(p.font, view.line2(), p.staffList.x() + 8,
                            y + 10, GovTheme.FAINT, false);
                    if (GovBusinessesStatus.canEdit(p) && view.removable()
                            && index < p.staffRemoveRects.size()) {
                        final GovRect remove = p.staffRemoveRects.get(index);
                        if (remove.w() > 0) {
                            final boolean armed = GovBusinessesArm.isStaffRemoveArmed(p, view.uuid());
                            GovWidgets.button(graphics, p.font, remove,
                                    armed ? p.removeConfirm : p.removeAction,
                                    !GovBusinessesRequests.mutationPending(p), remove.contains(mouseX, mouseY), armed);
                        }
                    }
                }
            } finally {
                if (scissored) {
                    graphics.disableScissor();
                }
            }
        }
        GovWidgets.scrollbar(graphics, p.staffScrollTrack, p.staffScroll,
                p.staffScrollMax);
    }

    static void drawForm(final GovBusinessesPanel p, final GuiGraphics graphics, final FormGeometry form,
            final String note, final int mouseX, final int mouseY) {
        if (form == null || form.area.w() <= 0 || form.area.h() <= 0) {
            return;
        }
        for (final FormRow row : form.rows) {
            if (row.labelRect().w() > 0 && row.labelRect().h() > 0) {
                graphics.drawString(p.font, row.label(), row.labelRect().x(),
                        row.labelRect().y() + 3, GovTheme.MUTED, false);
            }
            drawFormControl(p, graphics, row, mouseX, mouseY);
        }
        for (final FormAction action : form.actions) {
            final boolean armed = GovBusinessesArm.isArmed(p, action.click());
            String label = action.label();
            if (armed) {
                if (action.click() == Click.PAYROLL_RECORD
                        && !p.payrollRecordArmedLabel.isEmpty()) {
                    label = p.payrollRecordArmedLabel;
                } else if (action.armedLabel() != null) {
                    label = action.armedLabel();
                }
            }
            GovWidgets.button(graphics, p.font, action.rect(), label,
                    GovBusinessesStatus.actionEnabled(p, action.click()), action.rect().contains(mouseX, mouseY),
                    action.primary() || armed);
        }
        if (form.noteRect.w() > 0 && form.noteRect.h() >= 9 && note != null
                && !note.isEmpty()) {
            final int noteY = form.noteRect.h() >= 11 ? form.noteRect.y() + 2
                    : form.noteRect.y();
            graphics.drawString(p.font, note, form.noteRect.x(), noteY,
                    GovTheme.FAINT, false);
        }
    }

    static void drawFormControl(final GovBusinessesPanel p, final GuiGraphics graphics, final FormRow row,
            final int mouseX, final int mouseY) {
        final GovRect control = row.controlRect();
        if (control.w() <= 0 || control.h() <= 0) {
            return;
        }
        switch (row.kind()) {
            case NAME, OWNER, TRANSFER_OWNER, REASON, STAFF_PLAYER, LINK_JOB,
                    RATE, SCHEDULE, AMOUNT, PAYMENT_NOTE -> {
                final EditBox box = GovBusinessesWidgets.boxFor(p, row.kind());
                GovWidgets.fieldFrame(graphics, control, box != null && box.isFocused());
            }
            case TYPE -> GovWidgets.button(graphics, p.font, control,
                    p.registerTypeText, true, control.contains(mouseX, mouseY), false);
            case BUSINESS -> drawBusinessSelector(p, graphics, control, mouseX, mouseY);
            case ROLE -> GovWidgets.button(graphics, p.font, control,
                    p.staffRoleText, true, control.contains(mouseX, mouseY), false);
            case CURRENT_OWNER -> {
                graphics.fill(control.x(), control.y(), control.right(), control.bottom(),
                        GovTheme.PANEL_ALT);
                GovTheme.border(graphics, control, GovTheme.BORDER_SOFT);
                graphics.drawString(p.font, p.currentOwnerValue, control.x() + 2,
                        control.y() + 3, GovTheme.BODY, false);
            }
            case STATUS -> {
                graphics.fill(control.x(), control.y(), control.right(), control.bottom(),
                        GovTheme.PANEL_ALT);
                GovTheme.border(graphics, control, GovTheme.BORDER_SOFT);
                graphics.drawString(p.font, p.businessStatusValue, control.x() + 2,
                        control.y() + 3, GovTheme.BODY, false);
            }
            default -> {
            }
        }
    }

    static void drawBusinessSelector(final GovBusinessesPanel p, final GuiGraphics graphics, final GovRect control,
            final int mouseX, final int mouseY) {
        GovWidgets.button(graphics, p.font, p.businessPrevRect, "<", true,
                p.businessPrevRect.contains(mouseX, mouseY), false);
        GovWidgets.button(graphics, p.font, p.businessNextRect, ">", true,
                p.businessNextRect.contains(mouseX, mouseY), false);
        if (p.businessCentreRect.w() > 0) {
            graphics.fill(p.businessCentreRect.x(), p.businessCentreRect.y(),
                    p.businessCentreRect.right(), p.businessCentreRect.bottom(),
                    GovTheme.FIELD);
            GovTheme.border(graphics, p.businessCentreRect, GovTheme.BORDER_SOFT);
            GovText.centered(graphics, p.font, p.selectedBusinessText,
                    p.businessCentreRect.x() + 2, p.businessCentreRect.y() + 3,
                    p.businessCentreRect.w() - 4, GovTheme.BODY);
        }
    }

}
