package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.LicenseKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.VehicleKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import com.horizonssmp.gov.client.GovVehiclesTypes.*;
import static com.horizonssmp.gov.client.GovVehiclesConstants.*;
abstract class GovVehiclesPanelPart07 extends GovVehiclesPanelPart06 {
    GovVehiclesPanelPart07(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void drawDetail(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (this.detailRect.w() <= 0 || this.detailRect.h() <= 0) {
            return;
        }
        graphics.fill(this.detailRect.x(), this.detailRect.y(), this.detailRect.right(),
                this.detailRect.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, this.detailRect, GovTheme.BORDER);

        if (this.selectedPlate.isEmpty() || this.detailState == LoadState.NONE) {
            drawText(graphics, label("gui.gov.vehicles.no_selection"), this.detailRect.x() + 6,
                    this.detailRect.y() + 6, this.detailRect.w() - 12, GovTheme.MUTED);
            return;
        }
        if (this.detailState == LoadState.LOADING) {
            drawText(graphics, label("gui.gov.vehicles.loading"), this.detailRect.x() + 6,
                    this.detailRect.y() + 6, this.detailRect.w() - 12, GovTheme.MUTED);
            return;
        }
        if (this.detailState == LoadState.ERROR) {
            drawText(graphics, label("gui.gov.vehicles.detail_error"), this.detailRect.x() + 6,
                    this.detailRect.y() + 6, this.detailRect.w() - 12, GovTheme.DANGER);
            drawText(graphics, this.detailErrorView, this.detailRect.x() + 6,
                    this.detailRect.y() + 18, this.detailRect.w() - 12, GovTheme.MUTED);
            return;
        }

        graphics.drawString(this.font, this.detailTitleBold, this.detailRect.x() + 6,
                this.detailRect.y() + 4, GovTheme.TEXT, false);
        if (this.detailStateView != null && !this.detailStateView.isEmpty()) {
            graphics.drawString(this.font, this.detailStateView,
                    this.detailRect.right() - 6 - this.font.width(this.detailStateView),
                    this.detailRect.y() + 4, GovTheme.TEXT, false);
        }
        graphics.fill(this.detailRect.x() + 4, this.detailRect.y() + 15,
                this.detailRect.right() - 4, this.detailRect.y() + 16, GovTheme.BORDER_SOFT);

        final int bodyTop = this.detailBodyRect.y() > 0 ? this.detailBodyRect.y()
                : this.detailRect.y() + 18;
        final int bodyBottom = this.detailBodyRect.h() > 0 ? this.detailBodyRect.bottom()
                : this.detailRect.bottom() - 20;
        if (bodyBottom <= bodyTop) {
            return;
        }
        final int labelW = GovMetrics.span(this.detailRect.w(), this.detailRect.w() / 3, 40, 80);
        final int valueX = this.detailRect.x() + 6 + labelW + 3;
        graphics.enableScissor(this.detailRect.x() + 1, bodyTop,
                this.detailRect.right() - 1, bodyBottom);
        try {
            int y = bodyTop - this.detailScroll;
            for (int i = 0; i < this.detailLines.size(); i++) {
                final DetailLine line = this.detailLines.get(i);
                if (y + DETAIL_LINE > bodyTop && y < bodyBottom) {
                    if (line.wrapped() != null) {
                        graphics.drawString(this.font, line.wrapped(), this.detailRect.x() + 8, y,
                                line.color(), false);
                    } else {
                        if (line.label() != null && !line.label().isEmpty()) {
                            graphics.drawString(this.font, line.label(), this.detailRect.x() + 6,
                                    y, GovTheme.MUTED, false);
                        }
                        if (line.value() != null && !line.value().isEmpty()) {
                            graphics.drawString(this.font, line.value(), valueX, y, line.color(),
                                    false);
                        }
                    }
                }
                y += DETAIL_LINE;
            }
        } finally {
            graphics.disableScissor();
        }

        if (this.detailScrollMax > 0) {
            final GovRect track = rect(this.detailRect.right() - 4, bodyTop, 2,
                    Math.max(0, bodyBottom - bodyTop));
            GovWidgets.scrollbar(graphics, track, this.detailScroll, this.detailScrollMax);
        }
    }

    void drawBottomBar(final GuiGraphics graphics) {
        if (this.bottomBar.w() <= 0) {
            return;
        }
        graphics.fill(this.bottomBar.x(), this.bottomBar.y(), this.bottomBar.right(),
                this.bottomBar.y() + 1, GovTheme.BORDER_SOFT);
        final boolean showStatus = !this.status.isEmpty()
                && this.subtab == TAB_REGISTRY
                && this.detailRect.w() > 0 && this.pageTextRect.w() > 60;
        final String barText = showStatus ? this.statusView : this.pageView;
        final int barColor = showStatus
                ? (this.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE)
                : GovTheme.MUTED;
        GovText.clipped(graphics, this.font, barText, this.pageTextRect.x(),
                this.pageTextRect.y() + (this.pageTextRect.h() - ROW_TEXT) / 2,
                this.pageTextRect.w(), barColor);
    }

    void drawScan(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        drawFieldRows(graphics);
        if (!this.canScan) {
            drawText(graphics, this.scanForbiddenView,
                    this.scanFieldRect.x(), this.scanFieldRect.y() + 3,
                    this.scanFieldRect.w(), GovTheme.FAINT);
        }
        if (this.scanCardRect.w() > 0 && this.scanCardRect.h() > 0) {
            graphics.fill(this.scanCardRect.x(), this.scanCardRect.y(),
                    this.scanCardRect.right(), this.scanCardRect.bottom(), GovTheme.FIELD);
            GovTheme.border(graphics, this.scanCardRect, GovTheme.BORDER);
            if (this.scanLines.isEmpty()) {
                drawText(graphics, this.scanPromptView,
                        this.scanCardRect.x() + 6, this.scanCardRect.y() + 4,
                        this.scanCardRect.w() - 12, GovTheme.MUTED);
            } else {
                int y = this.scanCardRect.y() + 4;
                for (int i = 0; i < this.scanLines.size(); i++) {
                    if (y + ROW_TEXT > this.scanCardRect.bottom()) {
                        break;
                    }
                    graphics.drawString(this.font, this.scanLines.get(i),
                            this.scanCardRect.x() + 6, y, GovTheme.BODY, false);
                    y += DETAIL_LINE;
                }
            }
        }
        if (!this.status.isEmpty()) {
            drawText(graphics, this.scanStatusView, this.scanStatusRect.x(),
                    this.scanStatusRect.y(), this.scanStatusRect.w(),
                    this.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE);
        }
        drawButtons(graphics, mouseX, mouseY);
    }

    void drawRegister(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        drawFieldRows(graphics);
        graphics.drawString(this.font, this.registerOwnerView, this.ownerValueRect.x(),
                this.ownerValueRect.y() + (this.ownerValueRect.h() - ROW_TEXT) / 2,
                this.registerOwner.isBlank() ? GovTheme.WARN : GovTheme.TEXT, false);
        drawText(graphics, label("gui.gov.vehicles.field.class"), this.classLabelRect.x(),
                this.classLabelRect.y() + (this.classLabelRect.h() - ROW_TEXT) / 2,
                this.classLabelRect.w(), GovTheme.MUTED);
        if (this.registerRuleRect.w() > 0 && this.registerRuleRect.h() > 0) {
            graphics.drawString(this.font, this.registerRuleView,
                    this.registerRuleRect.x(), this.registerRuleRect.y(), GovTheme.FAINT, false);
        }
        if (!this.status.isEmpty()) {
            drawText(graphics, this.registerStatusView, this.registerStatusRect.x(),
                    this.registerStatusRect.y() + (this.registerStatusRect.h() - ROW_TEXT) / 2,
                    this.registerStatusRect.w(),
                    this.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE);
        }
        drawButtons(graphics, mouseX, mouseY);
    }

    void drawImpound(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        drawText(graphics, this.impoundSummaryView, this.impoundSummaryRect.x(),
                this.impoundSummaryRect.y(), this.impoundSummaryRect.w(),
                this.selectedPlate.isEmpty() ? GovTheme.WARN : GovTheme.TEXT);
        drawFieldRows(graphics);
        drawText(graphics, this.impoundWarningView,
                this.impoundRuleRect.x(), this.impoundRuleRect.y(),
                this.impoundRuleRect.w(), GovTheme.FAINT);
        graphics.drawString(this.font, this.releaseRuleView, this.impoundRuleRect.x(),
                this.impoundRuleRect.y() + DETAIL_LINE, GovTheme.FAINT, false);
        if (!this.status.isEmpty()) {
            drawText(graphics, this.impoundStatusView, this.impoundStatusRect.x(),
                    this.impoundStatusRect.y() + (this.impoundStatusRect.h() - ROW_TEXT) / 2,
                    this.impoundStatusRect.w(),
                    this.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE);
        }
        drawButtons(graphics, mouseX, mouseY);
    }

    void drawLicences(final GuiGraphics graphics, final int mouseX, final int mouseY,
            final boolean manage) {
        if (this.detailRect.w() > 0) {
            drawList(graphics, mouseX, mouseY);
            drawBottomBar(graphics);
        }
        drawText(graphics, this.licenseSummaryView, this.licenseSummaryRect.x(),
                this.licenseSummaryRect.y() + (this.licenseSummaryRect.h() - ROW_TEXT) / 2,
                this.licenseSummaryRect.w(),
                this.selectedPlate.isEmpty() ? GovTheme.WARN : GovTheme.TEXT);
        final boolean narrowListState = this.detailRect.w() <= 0
                && (this.listState == LoadState.LOADING || this.listState == LoadState.ERROR);
        if (!narrowListState) {
            graphics.drawString(this.font, this.licenseInfoView, this.licenseInfoRect.x(),
                    this.licenseInfoRect.y(), GovTheme.MUTED, false);
        }
        if (this.detailRect.w() <= 0 && this.listState == LoadState.LOADING) {
            drawText(graphics, label("gui.gov.vehicles.loading"),
                    this.licenseInfoRect.x(), this.licenseInfoRect.y(),
                    this.licenseInfoRect.w(), GovTheme.MUTED);
        }
        if (this.detailRect.w() <= 0 && this.listState == LoadState.ERROR) {
            drawText(graphics, this.listErrorView,
                    this.licenseInfoRect.x(), this.licenseInfoRect.y(),
                    this.licenseInfoRect.w(), GovTheme.DANGER);
        }
        drawText(graphics, label("gui.gov.vehicles.field.points"),
                this.licensePointsLabelRect.x(),
                this.licensePointsLabelRect.y() + (this.licensePointsLabelRect.h() - ROW_TEXT) / 2,
                this.licensePointsLabelRect.w(), GovTheme.MUTED);
        GovText.clipped(graphics, this.font, this.licenseHintView,
                this.licensePointsHintRect.x(),
                this.licensePointsHintRect.y() + (this.licensePointsHintRect.h() - ROW_TEXT) / 2,
                this.licensePointsHintRect.w(), GovTheme.FAINT);
        drawFieldRows(graphics);
        if (this.status.isEmpty()) {
            graphics.drawString(this.font, this.licenseRuleView,
                    this.licenseRuleRect.x(), this.licenseRuleRect.y(), GovTheme.FAINT, false);
        } else {
            drawText(graphics, this.licenseStatusFullView, this.licenseRuleRect.x(),
                    this.licenseRuleRect.y(), this.licenseRuleRect.w(),
                    this.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE);
        }
        if (manage) {
            drawButtons(graphics, mouseX, mouseY);
        }
    }

    void drawFieldRows(final GuiGraphics graphics) {
        for (int i = 0; i < this.fieldRows.size(); i++) {
            final FieldRow row = this.fieldRows.get(i);
            if (row.tab() != this.subtab || row.box() == null || !row.box().visible) {
                continue;
            }
            graphics.drawString(this.font, row.label(), row.labelRect().x(),
                    row.labelRect().y() + (row.labelRect().h() - ROW_TEXT) / 2,
                    GovTheme.MUTED, false);
            GovWidgets.fieldFrame(graphics, row.fieldRect(), row.box().isFocused());
        }
    }

    void drawButtons(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final long now = System.currentTimeMillis();
        for (int i = 0; i < this.buttons.size(); i++) {
            final Button button = this.buttons.get(i);
            final boolean armed = button.confirm() != null && isArmedVisual(button.click(), now);
            final String text = armed ? button.confirm() : button.label();
            final boolean hovered = button.enabled() && button.rect().contains(mouseX, mouseY);
            GovWidgets.button(graphics, this.font, button.rect(), text, button.enabled(),
                    hovered, button.primary() || armed);
        }
    }

    void drawText(final GuiGraphics graphics, final String text, final int x, final int y,
            final int width, final int color) {
        if (text == null || text.isEmpty() || width <= 0) {
            return;
        }
        GovText.clipped(graphics, this.font, text, x, y, width, color);
    }

    // --------------------------------------------------------------------- input

}
