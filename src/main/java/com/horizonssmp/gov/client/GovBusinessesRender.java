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

final class GovBusinessesRender {

    static     void render(final GovBusinessesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (!p.active || p.font == null) {
            return;
        }
        switch (p.subtab) {
            case MODE_REGISTRY -> GovBusinessesRender.drawRegistry(p, graphics, mouseX, mouseY);
            case MODE_NEW -> GovBusinessesRenderStaff.drawForm(p, graphics, p.newForm, GovBusinessesRender.newFormNote(p),
                    mouseX, mouseY);
            case MODE_OWNERSHIP -> GovBusinessesRenderStaff.drawForm(p, graphics, p.ownershipForm,
                    p.ownershipNoteText, mouseX, mouseY);
            case MODE_STAFF -> GovBusinessesRenderStaff.drawStaff(p, graphics, mouseX, mouseY);
            case MODE_PAYROLL -> GovBusinessesRenderStaff.drawForm(p, graphics, p.payrollForm,
                    p.payrollNoteText, mouseX, mouseY);
            case MODE_EMPLOYMENT -> GovBusinessesRender.drawEmployment(p, graphics, mouseX, mouseY);
            default -> {
            }
        }
        GovBusinessesRender2.drawBottomBar(p, graphics, mouseX, mouseY);
        if (p.modal != Modal.NONE) {
            GovBusinessesRender2.drawModal(p, graphics, mouseX, mouseY);
        }
    }

    static     String newFormNote(final GovBusinessesPanel p) {
        return "";
    }

    static     void drawRegistry(final GovBusinessesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.registryList.w() <= 0 || p.registryList.h() <= 0) {
            return;
        }
        graphics.fill(p.registryList.x(), p.registryList.y(), p.registryList.right(),
                p.registryList.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, p.registryList, GovTheme.BORDER);

        for (int index = 0; index < p.filterRects.length; index++) {
            final GovRect filterRect = p.filterRects[index];
            final boolean selected = FILTER_IDS[index].equals(p.filter);
            GovWidgets.button(graphics, p.font, filterRect, p.filterDisplayLabels[index],
                    true, filterRect.contains(mouseX, mouseY), selected);
        }

        if (p.registryHeader.w() > 0) {
            graphics.drawString(p.font, p.registryHeaderText,
                    p.registryHeader.x(), p.registryHeader.y() + 1, GovTheme.MUTED, false);
            GovWidgets.rule(graphics, p.registryHeader.x(),
                    p.registryHeader.bottom() - 1, p.registryHeader.w(),
                    GovTheme.BORDER_SOFT);
        }

        if (!p.emptyTitle.isEmpty()) {
            GovBusinessesRender2.drawEmpty(p, graphics, p.registryRows, p.emptyTitle,
                    p.emptyDetail);
            if (p.listState == LoadState.ERROR && p.listRetry.w() > 0) {
                GovWidgets.button(graphics, p.font, p.listRetry, p.retryText,
                        true, p.listRetry.contains(mouseX, mouseY), false);
            }
        } else {
            final boolean scissored = GovBusinessesGeometry.clipRows(graphics, p.registryRows);
            try {
                for (int index = 0; index < p.rowViews.size(); index++) {
                    final int y = p.registryRows.y() + index * p.registryPitch
                            - p.registryScroll;
                    if (y >= p.registryRows.bottom()) {
                        break;
                    }
                    if (y < p.registryRows.y()
                            || y + p.registryPitch > p.registryRows.bottom()) {
                        continue;
                    }
                    GovBusinessesRender.drawRegistryRow(p, graphics, p.rowViews.get(index), y, mouseX, mouseY);
                }
            } finally {
                if (scissored) {
                    graphics.disableScissor();
                }
            }
        }
        GovWidgets.scrollbar(graphics, p.registryScrollTrack, p.registryScroll,
                p.registryScrollMax);

        if (p.registrySummary.w() > 0) {
            GovWidgets.rule(graphics, p.registrySummary.x(), p.registrySummary.y(),
                    p.registrySummary.w(), GovTheme.BORDER_SOFT);
            graphics.drawString(p.font, p.registrySummaryText,
                    p.registrySummary.x() + 2, p.registrySummary.y() + 1,
                    GovTheme.MUTED, false);
        } else {
            GovBusinessesRender2.drawDetail(p, graphics, mouseX, mouseY);
        }
    }

    static     void drawEmployment(final GovBusinessesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.positionList.w() <= 0 || p.positionList.h() <= 0) {
            return;
        }
        graphics.fill(p.positionList.x(), p.positionList.y(), p.positionList.right(),
                p.positionList.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, p.positionList, GovTheme.BORDER);

        graphics.drawString(p.font, GovBusinessesUtil.tr("gui.gov.businesses.position_list"),
                p.positionHeader.x(), p.positionHeader.y() + 1, GovTheme.MUTED, false);
        GovWidgets.rule(graphics, p.positionHeader.x(), p.positionHeader.bottom() - 1,
                p.positionHeader.w(), GovTheme.BORDER_SOFT);

        if (p.selectedId.isEmpty()) {
            GovBusinessesRender2.drawEmpty(p, graphics, p.positionListRows, p.noSelectionText, "");
        } else if (p.positionState == LoadState.LOADING) {
            GovBusinessesRender2.drawEmpty(p, graphics, p.positionListRows, p.loadingText, "");
        } else if (p.positionState == LoadState.ERROR) {
            GovBusinessesRender2.drawEmpty(p, graphics, p.positionListRows,
                    p.detailErrorTitle, p.positionError);
        } else if (p.positionRows.isEmpty()) {
            GovBusinessesRender2.drawEmpty(p, graphics, p.positionListRows, p.noStaffText, "");
        } else {
            final boolean scissored = GovBusinessesGeometry.clipRows(graphics, p.positionListRows);
            try {
                for (int index = 0; index < p.positionRows.size(); index++) {
                    final int y = p.positionListRows.y() + index * p.positionPitch
                            - p.positionScroll;
                    if (y >= p.positionListRows.bottom()) {
                        break;
                    }
                    if (y < p.positionListRows.y()
                            || y + p.positionPitch > p.positionListRows.bottom()) {
                        continue;
                    }
                    drawPositionRow(p, graphics, p.positionRows.get(index), y, mouseX, mouseY);
                }
            } finally {
                if (scissored) {
                    graphics.disableScissor();
                }
            }
        }
        GovWidgets.scrollbar(graphics, p.positionScrollTrack, p.positionScroll,
                p.positionScrollMax);

        // ---- edit area below the list ----
        if (p.positionEditArea.w() <= 0 || p.positionEditArea.h() <= 0) {
            return;
        }
        graphics.fill(p.positionEditArea.x(), p.positionEditArea.y(),
                p.positionEditArea.right(), p.positionEditArea.bottom(), GovTheme.PANEL_ALT);
        GovTheme.border(graphics, p.positionEditArea, GovTheme.BORDER_SOFT);

        graphics.drawString(p.font, GovBusinessesUtil.tr("gui.gov.businesses.position_edit_title"),
                p.positionTitleLabel.x(), p.positionTitleLabel.y() + 3, GovTheme.MUTED, false);
        GovWidgets.fieldFrame(graphics, p.positionTitleCtrl,
                p.host.getFocused() == p.positionTitleBox);
        graphics.drawString(p.font, GovBusinessesUtil.tr("gui.gov.businesses.position_edit_wage"),
                p.positionWageLabel.x(), p.positionWageLabel.y() + 3, GovTheme.MUTED, false);
        GovWidgets.fieldFrame(graphics, p.positionWageCtrl,
                p.host.getFocused() == p.positionWageBox);

        final boolean editArmed = GovBusinessesArm.isArmed(p, Click.POSITION_EDIT_SUBMIT);
        GovWidgets.button(graphics, p.font, p.positionEditBtn,
                editArmed ? p.removeConfirm : GovBusinessesUtil.tr("gui.gov.businesses.position_edit_submit"),
                GovBusinessesStatus.actionEnabled(p, Click.POSITION_EDIT_SUBMIT),
                p.positionEditBtn.contains(mouseX, mouseY), editArmed);
    }

    private static void drawPositionRow(final GovBusinessesPanel p, final GuiGraphics graphics, final Map<String, String> row, final int y,
            final int mouseX, final int mouseY) {
        final String id = row.get(BusinessKeys.POSITION_ROW_ID);
        final boolean selected = id != null && id.equals(p.selectedPosition);
        final boolean hovered = GovTheme.inside(mouseX, mouseY, p.positionList.x() + 2, y,
                Math.max(0, p.positionList.w() - 4), p.positionPitch);
        if (selected) {
            graphics.fill(p.positionList.x() + 2, y, p.positionList.right() - 2,
                    y + p.positionPitch, GovTheme.SELECTED);
            graphics.fill(p.positionList.x() + 2, y, p.positionList.x() + 5,
                    y + p.positionPitch, GovTheme.ACCENT);
        } else if (hovered) {
            graphics.fill(p.positionList.x() + 2, y, p.positionList.right() - 2,
                    y + p.positionPitch, GovTheme.HOVER);
        }
        final int textX = p.positionList.x() + 8;
        final int textY = y + 2;
        graphics.drawString(p.font, row.getOrDefault(BusinessKeys.POSITION_ROW_TITLE, id),
                textX, textY, GovTheme.BODY, false);
        final String wage = row.getOrDefault(BusinessKeys.POSITION_ROW_WAGE, "");
        if (!wage.isEmpty()) {
            graphics.drawString(p.font, wage, textX, y + 10, GovTheme.FAINT, false);
        }
    }

    private static void drawRegistryRow(final GovBusinessesPanel p, final GuiGraphics graphics, final RowView view, final int y,
            final int mouseX, final int mouseY) {
        final boolean selected = view.id().equals(p.selectedId);
        final boolean hovered = GovTheme.inside(mouseX, mouseY, p.registryList.x() + 2, y,
                Math.max(0, p.registryList.w() - 4), p.registryPitch);
        if (selected) {
            graphics.fill(p.registryList.x() + 2, y, p.registryList.right() - 2,
                    y + p.registryPitch, GovTheme.SELECTED);
            graphics.fill(p.registryList.x() + 2, y, p.registryList.x() + 5,
                    y + p.registryPitch, GovTheme.ACCENT);
        } else if (hovered) {
            graphics.fill(p.registryList.x() + 2, y, p.registryList.right() - 2,
                    y + p.registryPitch, GovTheme.HOVER);
        }
        final int textX = p.registryList.x() + 8;
        final int textY = y + 2;
        graphics.drawString(p.font, view.name(), textX, textY,
                selected ? GovTheme.TEXT : GovTheme.BODY, false);
        if (view.statusWidth() > 0) {
            final int badgeX = p.registryList.right() - 4 - view.statusWidth();
            graphics.fill(badgeX, y + 2, badgeX + view.statusWidth(), y + 13,
                    GovTheme.PANEL_ALT);
            GovTheme.border(graphics, badgeX, y + 2, view.statusWidth(), 11, GovTheme.BORDER);
            GovText.centered(graphics, p.font, view.status(), badgeX + 2, y + 4,
                    view.statusWidth() - 4, GovTheme.TEXT);
        }
        graphics.drawString(p.font, view.meta(), textX, y + 10, GovTheme.FAINT, false);
    }





}
