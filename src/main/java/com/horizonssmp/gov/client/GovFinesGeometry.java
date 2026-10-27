package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class GovFinesGeometry {

    /** Recomputes every rectangle inside the content area. Called on init and resize only. */
    static void layout(final GovFinesPanel p, final int left, final int top, final int right, final int bottom,
            final Font font) {
        p.font = font;
        final int width = Math.max(0, right - left);
        final int height = Math.max(0, bottom - top);
        p.area = new GovRect(left, top, width, height);
        p.compact = width < 420 || height < 120;
        p.pad = p.compact ? GovMetrics.GAP_XS : GovMetrics.GAP_S;
        p.rowH = p.compact ? GovMetrics.ROW_H_COMPACT : GovMetrics.ROW_H;
        p.showTitle = height >= 150;
        GovFinesLabels.rebuildLabels(p);

        computeListGeometry(p);
        computeIssueGeometry(p);
        computeCollectGeometry(p);
        computeWaiveGeometry(p);
        positionFields(p);
        GovFinesSupport.syncWidgets(p);
        GovFinesViews.rebuildAll(p);
    }

    static void computeListGeometry(final GovFinesPanel p) {
        final int innerLeft = p.area.x() + p.pad;
        final int innerW = p.area.w() - p.pad * 2;
        p.titleRect = rect(p, p.area.x() + p.pad, p.area.y() + 2, innerW,
                GovMetrics.LINE_TITLE + 2);
        int y = p.area.y() + 2 + (p.showTitle ? GovMetrics.LINE_TITLE + 2 : 0);
        final int barH = p.compact ? 14 : GovMetrics.BUTTON_H;
        p.topBar = rect(p, innerLeft, y, innerW, barH);
        y = p.topBar.bottom() + 2;
        final int headerH = p.compact ? 10 : 11;
        p.tableHeader = rect(p, innerLeft, y, innerW, headerH);
        y = p.tableHeader.bottom() + 1;

        final int bottomH = p.compact ? 14 : GovMetrics.BUTTON_H;
        p.bottomBar = rect(p, innerLeft, p.area.bottom() - 2 - bottomH, innerW, bottomH);
        final int buttonH = p.compact ? 12 : 14;
        final int buttonY = p.bottomBar.y() + (p.bottomBar.h() - buttonH) / 2;
        p.prevRect = rect(p, p.bottomBar.x(), buttonY, 34, buttonH);
        p.nextRect = rect(p, p.prevRect.right() + 4, buttonY, 34, buttonH);
        final int refreshW = Math.min(p.compact ? 46 : 56, p.bottomBar.w() / 3);
        p.refreshRect = rect(p, p.bottomBar.right() - refreshW, buttonY, refreshW, buttonH);
        final int pageX = p.nextRect.right() + 6;
        p.pageTextRect = rect(p, pageX, buttonY,
                Math.max(0, p.refreshRect.x() - pageX - 4), buttonH);

        final int listW;
        if (p.area.w() >= 340) {
            listW = Math.max(0, innerW * 55 / 100);
            p.listRect = rect(p, innerLeft, y, listW,
                    Math.max(0, p.bottomBar.y() - 2 - y));
            p.detailRect = rect(p, p.listRect.right() + GovMetrics.GAP_XS, y,
                    Math.max(0, innerW - listW - GovMetrics.GAP_XS),
                    Math.max(0, p.bottomBar.y() - 2 - y));
            p.summaryRect = NONE;
        } else {
            listW = innerW;
            p.summaryRect = rect(p, innerLeft, p.bottomBar.y() - 11, innerW, 10);
            p.listRect = rect(p, innerLeft, y, listW,
                    Math.max(0, p.summaryRect.y() - 1 - y));
            p.detailRect = NONE;
        }

        final int gap = 4;
        final int filterW = Math.max(0, (innerW - gap * 3) / 4);
        for (int i = 0; i < FILTERS.length; i++) {
            p.filterRects[i] = rect(p, innerLeft + i * (filterW + gap), p.topBar.y(),
                    filterW, p.topBar.h());
        }
        computeColumns(p);
    }

    static void computeColumns(final GovFinesPanel p) {
        if (p.listRect.w() <= 0) {
            for (int i = 0; i < 5; i++) {
                p.columnX[i] = 0;
                p.columnW[i] = 0;
            }
            return;
        }
        final int inner = Math.max(0, p.listRect.w() - 8);
        final int[] share = {30, 18, 16, 22, 14};
        int x = p.listRect.x() + 4;
        int used = 0;
        for (int i = 0; i < 5; i++) {
            final int width = i == 4 ? Math.max(0, inner - used) : inner * share[i] / 100;
            p.columnX[i] = x;
            p.columnW[i] = Math.max(0, width);
            x += width;
            used += width;
        }
    }

    static void computeIssueGeometry(final GovFinesPanel p) {
        final int innerW = p.area.w() - p.pad * 2;
        final int formW = Math.min(innerW, 560);
        final int formX = p.area.x() + (p.area.w() - formW) / 2;
        final int formTop = p.area.y() + 2
                + (p.showTitle ? GovMetrics.LINE_TITLE + 2 : 0);
        final int formBottom = p.area.bottom() - 2;
        final int labelW = p.compact ? 52 : 70;
        final int colGap = p.compact ? 6 : 12;
        final int colW = Math.max(0, (formW - colGap) / 2);
        final int fieldH = GovMetrics.FIELD_H;
        final int gap = 2;
        int y = formTop;

        p.citizenLabelRect = rect(p, formX, y, labelW, fieldH);
        final int navW = 14;
        p.onlineNextRect = rect(p, formX + formW - navW, y, navW, fieldH);
        p.onlinePrevRect = rect(p, p.onlineNextRect.x() - navW - 2, y, navW, fieldH);
        p.citizenValueRect = rect(p, formX + labelW + 3, y,
                Math.max(0, p.onlinePrevRect.x() - (formX + labelW + 3) - 2), fieldH);
        y += fieldH + gap;

        p.bandLabelRect = rect(p, formX, y, labelW, fieldH);
        final int bandAreaX = formX + labelW + 3;
        final int bandAreaW = Math.max(0, formW - labelW - 3);
        final int bandW = Math.max(0, (bandAreaW - 3 * 3) / 4);
        for (int i = 0; i < BANDS.length; i++) {
            p.bandRects[i] = rect(p, bandAreaX + i * (bandW + 3), y, bandW, fieldH);
        }
        y += fieldH + gap;

        p.articleLabelRect = rect(p, formX, y, labelW, fieldH);
        p.articleFieldRect = rect(p, formX + labelW + 3, y,
                Math.max(0, colW - labelW - 3), fieldH);
        p.restitutionLabelRect = rect(p, formX + colW + colGap, y, labelW, fieldH);
        p.restitutionFieldRect = rect(p, formX + colW + colGap + labelW + 3, y,
                Math.max(0, colW - labelW - 3), fieldH);
        y += fieldH + gap;

        p.victimLabelRect = rect(p, formX, y, labelW, fieldH);
        p.victimFieldRect = rect(p, formX + labelW + 3, y,
                Math.max(0, colW - labelW - 3), fieldH);
        p.exileLabelRect = rect(p, formX + colW + colGap, y, labelW, fieldH);
        p.exileFieldRect = rect(p, formX + colW + colGap + labelW + 3, y,
                Math.max(0, colW - labelW - 3), fieldH);
        y += fieldH + gap;

        p.reasonLabelRect = rect(p, formX, y, labelW, fieldH);
        p.reasonFieldRect = rect(p, formX + labelW + 3, y,
                Math.max(0, formW - labelW - 3), fieldH);
        y += fieldH + gap;

        final int actionH = 14;
        p.issueActionRect = rect(p, formX, formBottom - actionH, formW, actionH);
        final int issueW = Math.min(96, formW / 3);
        p.issueButtonRect = rect(p, p.issueActionRect.right() - issueW,
                p.issueActionRect.y(), issueW, actionH);
        p.issueAmountRect = rect(p, p.issueActionRect.x(), p.issueActionRect.y(),
                Math.max(0, p.issueActionRect.w() - issueW - 4), actionH);
        p.issueHintRect = rect(p, formX, y, formW, 10);
        p.formRect = rect(p, formX, formTop, formW, Math.max(0, formBottom - formTop));
    }

    static void computeCollectGeometry(final GovFinesPanel p) {
        final int innerW = p.area.w() - p.pad * 2;
        final int innerLeft = p.area.x() + p.pad;
        final boolean side = p.area.w() >= 340;
        if (side) {
            p.collectFormRect = p.detailRect;
        } else {
            p.collectFormRect = rect(p, innerLeft, p.area.y() + 2, innerW,
                    Math.max(0, p.area.h() - 4));
        }
        final int x = p.collectFormRect.x() + 4;
        final int w = Math.max(0, p.collectFormRect.w() - 8);
        int y = p.collectFormRect.y() + 4;
        p.collectSummaryRect = rect(p, x, y, w, 10);
        y += 12;
        p.collectPrevRect = rect(p, x, y, 14, GovMetrics.FIELD_H);
        p.collectNextRect = rect(p, x + 16, y, 14, GovMetrics.FIELD_H);
        p.collectTargetRect = rect(p, x + 34, y,
                Math.max(0, w - 34), GovMetrics.FIELD_H);
        y += GovMetrics.FIELD_H + 3;
        p.collectAmountRect = rect(p, x, y, w, 10);
        y += 12;

        final int actionH = 14;
        final int buttonW = Math.min(96, Math.max(0, w / 2));
        if (side) {
            p.collectButtonRect = rect(p, x, y, buttonW, actionH);
            p.collectPayRect = rect(p, x, y + actionH + 3, buttonW, actionH);
            p.collectStatusRect = rect(p, x, y + (actionH + 3) * 2, w,
                    Math.max(0, p.collectFormRect.bottom() - 3 - (y + (actionH + 3) * 2)));
        } else {
            final int bottom = p.collectFormRect.bottom() - 3;
            p.collectButtonRect = rect(p, x, bottom - actionH, buttonW, actionH);
            p.collectPayRect = rect(p, p.collectButtonRect.right() + 4,
                    bottom - actionH, buttonW, actionH);
            p.collectStatusRect = rect(p, x, y, Math.max(0, bottom - actionH - 3 - y), 10);
        }
    }

    static void computeWaiveGeometry(final GovFinesPanel p) {
        final int innerW = p.area.w() - p.pad * 2;
        final int innerLeft = p.area.x() + p.pad;
        final boolean side = p.area.w() >= 340;
        if (side) {
            p.waiveFormRect = p.detailRect;
        } else {
            p.waiveFormRect = rect(p, innerLeft, p.area.y() + 2, innerW,
                    Math.max(0, p.area.h() - 4));
        }
        final int x = p.waiveFormRect.x() + 4;
        final int w = Math.max(0, p.waiveFormRect.w() - 8);
        int y = p.waiveFormRect.y() + 4;
        p.waivePrevRect = rect(p, x, y, 14, GovMetrics.FIELD_H);
        p.waiveNextRect = rect(p, x + 16, y, 14, GovMetrics.FIELD_H);
        p.waiveSummaryRect = rect(p, x + 34, y, Math.max(0, w - 34), 10);
        y += GovMetrics.FIELD_H + 3;
        p.waiveReasonLabelRect = rect(p, x, y, p.compact ? 52 : 70, GovMetrics.FIELD_H);
        p.waiveReasonFieldRect = rect(p, p.waiveReasonLabelRect.right() + 3, y,
                Math.max(0, w - p.waiveReasonLabelRect.w() - 3), GovMetrics.FIELD_H);
        y += GovMetrics.FIELD_H + 3;
        p.waiveRuleRect = rect(p, x, y, w, 10);
        y += 12;
        final int actionH = 14;
        final int bottom = p.waiveFormRect.bottom() - 3;
        final int buttonW = Math.min(112, Math.max(0, w / 2));
        p.waiveButtonRect = rect(p, x, bottom - actionH, buttonW, actionH);
        p.waiveStatusRect = rect(p, p.waiveButtonRect.right() + 4, bottom - actionH,
                Math.max(0, w - buttonW - 4), actionH);
    }

    static void positionFields(final GovFinesPanel p) {
        p.fieldRows.clear();
        addField(p, TAB_ISSUE, p.articleLabelRect, p.articleFieldRect, p.articleBox,
                "gui.gov.fines.field.article");
        addField(p, TAB_ISSUE, p.restitutionLabelRect, p.restitutionFieldRect,
                p.restitutionBox, "gui.gov.fines.field.restitution");
        addField(p, TAB_ISSUE, p.victimLabelRect, p.victimFieldRect, p.victimBox,
                "gui.gov.fines.field.victim");
        addField(p, TAB_ISSUE, p.exileLabelRect, p.exileFieldRect, p.exileBox,
                "gui.gov.fines.field.exile");
        addField(p, TAB_ISSUE, p.reasonLabelRect, p.reasonFieldRect, p.issueReasonBox,
                "gui.gov.fines.field.reason");
        addField(p, TAB_WAIVE, p.waiveReasonLabelRect, p.waiveReasonFieldRect,
                p.waiveReasonBox, "gui.gov.fines.field.reason");
    }

    static void addField(final GovFinesPanel p, final int tab, final GovRect labelRect, final GovRect fieldRect,
            final EditBox box, final String labelKey) {
        if (box == null || fieldRect.w() <= 0) {
            return;
        }
        final String label = GovText.clip(p.font, GovFinesLabels.label(p, labelKey), labelRect.w());
        p.fieldRows.add(new FieldRow(tab, labelRect, fieldRect, box, label));
        layoutBox(box, fieldRect);
    }

    static void layoutBox(final EditBox box, final GovRect rect) {
        if (box == null) {
            return;
        }
        box.setX(rect.x() + 3);
        box.setY(rect.y() + 3);
        box.setWidth(Math.max(0, rect.w() - 6));
    }

    static GovRect rect(final GovFinesPanel p, final int x, final int y, final int width, final int height) {
        return new GovRect(x, y, Math.max(0, width), Math.max(0, height)).clamp(p.area);
    }


}
