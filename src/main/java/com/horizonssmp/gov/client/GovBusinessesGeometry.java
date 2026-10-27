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

final class GovBusinessesGeometry {






    static     void positionFormWidgets(final GovBusinessesPanel p, final FormGeometry form) {
        if (form == null) {
            return;
        }
        for (final FormRow row : form.rows()) {
            final EditBox box = GovBusinessesWidgets.boxFor(p, row.kind());
            if (box != null) {
                GovBusinessesGeometry.positionBox(p, box, row.controlRect());
            }
        }
    }

    static     void positionBox(final GovBusinessesPanel p, final EditBox box, final GovRect rect) {
        if (box == null) {
            return;
        }
        final int width = Math.max(0, rect.w() - 6);
        box.setX(rect.x() + 3);
        box.setY(rect.y() + 3);
        box.setWidth(width);
    }

        static GovRect retryRect(final GovRect area) {
        if (area == null || area.w() <= 0 || area.h() < 9) {
            return EMPTY;
        }
        final int width = Math.min(RETRY_W, Math.max(0, area.w() - 4));
        final int height = Math.min(RETRY_H, area.h());
        if (width <= 0 || height < 9) {
            return EMPTY;
        }
        return new GovRect(area.x() + (area.w() - width) / 2, area.bottom() - height,
                width, height);
    }

        static boolean fullyVisible(final GovRect rect, final GovRect viewport) {
        return rect != null && viewport != null && rect.w() > 0 && rect.h() > 0
                && rect.x() >= viewport.x() && rect.y() >= viewport.y()
                && rect.right() <= viewport.right() && rect.bottom() <= viewport.bottom();
    }

        static boolean clipRows(final GuiGraphics graphics, final GovRect rows) {
        if (rows == null || rows.w() <= 2 || rows.h() <= 2) {
            return false;
        }
        graphics.enableScissor(rows.x() + 1, rows.y() + 1, rows.right() - 1,
                rows.bottom() - 1);
        return true;
    }

    static     void layout(final GovBusinessesPanel p, final int left, final int top, final int right, final int bottom,             final Font font) {
        p.font = font;
        final int width = Math.max(0, right - left);
        final int height = Math.max(0, bottom - top);
        p.rect = new GovRect(left, top, width, height);

        final int pad = width < 360 ? 4 : width < 600 ? 6 : 8;
        final GovRect inner = p.rect.inset(pad);
        final int barHeight = GovMetrics.BUTTON_H;
        final int statusHeight = height >= 150 ? 10 : 0;
        final int gap = 4;
        final int bodyHeight = Math.max(0,
                inner.h() - barHeight - (statusHeight > 0 ? statusHeight + gap : 0));
        p.body = new GovRect(inner.x(), inner.y(), inner.w(), bodyHeight);
        p.bar = new GovRect(inner.x(), inner.bottom() - barHeight, inner.w(), barHeight);
        p.statusRect = statusHeight > 0
                ? new GovRect(inner.x(), p.body.bottom() + gap, inner.w(), statusHeight)
                : EMPTY;

        final int refreshW = GovMetrics.clamp(p.bar.w() / 5, 52, 64);
        p.refreshRect = new GovRect(p.bar.right() - refreshW, p.bar.y(),
                refreshW, p.bar.h()).clamp(p.bar);
        p.formStatusBar = new GovRect(p.bar.x(), p.bar.y(),
                Math.max(0, p.refreshRect.x() - p.bar.x() - 4), p.bar.h())
                .clamp(p.bar);

        GovBusinessesGeometry2.layoutRegistry(p);
        GovBusinessesGeometry2.layoutStaff(p);
        GovBusinessesGeometry2.layoutForms(p);
        GovBusinessesGeometry2.layoutEmployment(p);
        GovBusinessesGeometry2.layoutModal(p);

        GovBusinessesGeometry.positionFormWidgets(p, p.newForm);
        GovBusinessesGeometry.positionFormWidgets(p, p.ownershipForm);
        GovBusinessesGeometry.positionFormWidgets(p, p.payrollForm);
        GovBusinessesGeometry.positionFormWidgets(p, p.staffAddForm);
        GovBusinessesGeometry.positionFormWidgets(p, p.linkForm);
        GovBusinessesGeometry.positionFormWidgets(p, p.suspendForm);
        GovBusinessesGeometry.positionBox(p, p.positionTitleBox, p.positionTitleCtrl);
        GovBusinessesGeometry.positionBox(p, p.positionWageBox, p.positionWageCtrl);

        GovBusinessesCaches.rebuildCaches(p);
    }

}
