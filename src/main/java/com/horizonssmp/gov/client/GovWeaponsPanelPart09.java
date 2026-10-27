package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.common.WeaponKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import com.horizonssmp.gov.client.GovWeaponsTypes.*;
import static com.horizonssmp.gov.client.GovWeaponsConstants.*;

abstract class GovWeaponsPanelPart09 extends GovWeaponsPanelPart08 {
    GovWeaponsPanelPart09(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }


    void drawDetail(final GuiGraphics graphics, final int mouseX, final int mouseY,
            final GovRect detail) {
        graphics.fill(detail.x(), detail.y(), detail.right(), detail.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, detail, GovTheme.BORDER_SOFT);
        final int actionH = this.subtab == 0 ? 18 : 0;
        final int bodyTop = detail.y() + 16;
        final int bodyBottom = detail.bottom() - actionH - 2;
        if (!this.detailTitle.isEmpty()) {
            GovText.clipped(graphics, this.font, this.detailTitle, detail.x() + 5, detail.y() + 3,
                    Math.max(0, detail.w() - 10), GovTheme.TEXT);
        }
        GovWidgets.rule(graphics, detail.x(), detail.y() + 14, detail.w(), GovTheme.BORDER_SOFT);
        final int bodyH = Math.max(0, bodyBottom - bodyTop);
        final int visible = this.detailPitch > 0 ? bodyH / this.detailPitch : 0;
        if (visible <= 0) {
            return;
        }
        final int maxScroll = Math.max(0, this.detailLines.size() - visible);
        final int scroll = GovMetrics.clamp(this.detailScrollRows[this.subtab], 0, maxScroll);
        final int labelW = Math.min(54, Math.max(0, detail.w() / 3));
        final int valueX = detail.x() + 6 + labelW;
        final int valueW = Math.max(0, detail.right() - 6 - valueX);
        graphics.enableScissor(detail.x() + 1, bodyTop, detail.right() - 1, bodyBottom);
        try {
            for (int i = 0; i < visible; i++) {
                final int index = scroll + i;
                if (index >= this.detailLines.size()) {
                    break;
                }
                final int y = bodyTop + i * this.detailPitch;
                if (y + this.detailPitch > bodyBottom) {
                    break;
                }
                final DetailLine line = this.detailLines.get(index);
                if (line.rule()) {
                    GovWidgets.rule(graphics, detail.x() + 4, y, detail.w() - 8,
                            GovTheme.BORDER_SOFT);
                } else if (line.sequence() != null) {
                    graphics.drawString(this.font, line.sequence(), detail.x() + 6, y,
                            line.color(), false);
                } else if (!line.label().isEmpty()) {
                    GovText.clipped(graphics, this.font, GovText.upperBoldCached(line.label()),
                            detail.x() + 6, y, Math.max(0, labelW - 4), GovTheme.MUTED);
                    GovText.clipped(graphics, this.font, line.value(), valueX, y, valueW,
                            line.color());
                } else {
                    GovText.clipped(graphics, this.font, line.value(), detail.x() + 6, y,
                            Math.max(0, detail.w() - 12), line.color());
                }
            }
        } finally {
            graphics.disableScissor();
        }
        if (maxScroll > 0) {
            GovWidgets.scrollbar(graphics, this.detailScrollbarRect,
                    scroll * this.detailPitch, maxScroll * this.detailPitch);
        }
    }

    String summaryText() {
        return switch (this.subtab) {
            case 0 -> this.weaponSummary.isEmpty() ? this.textNoSelection : this.weaponSummary;
            case 1 -> this.permitSummary.isEmpty() ? this.textNoSelection : this.permitSummary;
            case 2 -> this.areaSummary.isEmpty() ? this.textNoSelection : this.areaSummary;
            default -> "";
        };
    }

    void drawDetailOrSummary(final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        final GovRect detail = this.details[this.subtab];
        if (detail.w() > 0 && detail.h() > 0) {
            drawDetail(graphics, mouseX, mouseY, detail);
            return;
        }
        final GovRect summary = this.summaries[this.subtab];
        if (summary.w() > 0 && summary.h() > 0) {
            GovText.clipped(graphics, this.font, summaryText(), summary.x() + 2,
                    summary.y() + 1, Math.max(0, summary.w() - 4), GovTheme.MUTED);
        }
    }

    int classifierRow(final GuiGraphics graphics, final String label, final String value,
            final int y, final int valueX, final int valueW, final int labelW,
            final GovRect body) {
        if (y + 10 > body.bottom()) {
            return y;
        }
        GovText.clipped(graphics, this.font, GovText.upperBoldCached(label), body.x() + 4, y,
                Math.max(0, labelW - 2), GovTheme.MUTED);
        GovText.clipped(graphics, this.font, value, valueX, y, valueW,
                this.classifyBlocked && this.labelCitizenAllowed.equals(label)
                        ? GovTheme.TEXT : GovTheme.BODY);
        return y + 11;
    }

    void drawClassifier(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final GovRect body = this.bodies[3];
        if (body.w() <= 0 || body.h() <= 0) {
            return;
        }
        GovWidgets.rule(graphics, body.x(), body.y(), body.w(), GovTheme.BORDER_SOFT);
        int y = body.y() + 2;
        final int labelW = Math.min(86, Math.max(0, body.w() / 3));
        final int valueX = body.x() + labelW + 4;
        final int valueW = Math.max(0, body.right() - valueX - 4);
        y = classifierRow(graphics, this.labelCategory, this.classifyCategory, y, valueX,
                valueW, labelW, body);
        y = classifierRow(graphics, this.labelPermitRequired, this.classifyPermit, y, valueX,
                valueW, labelW, body);
        y = classifierRow(graphics, this.labelCitizenAllowed, this.classifyCitizen, y, valueX,
                valueW, labelW, body);
        if (y + 10 <= body.bottom() && !this.classifyReason.isEmpty()) {
            GovText.clipped(graphics, this.font, this.labelReason, body.x() + 4, y,
                    Math.max(0, labelW - 2), GovTheme.MUTED);
            final int lines = Math.min(this.classifyReasonLines.size(),
                    Math.max(0, (body.bottom() - y) / 10));
            for (int i = 0; i < lines; i++) {
                graphics.drawString(this.font, this.classifyReasonLines.get(i), valueX,
                        y + i * 10, this.classifyBlocked ? GovTheme.TEXT : GovTheme.BODY, false);
            }
        }
    }

    void drawFieldFrame(final GuiGraphics graphics, final GovRect frame,
            final EditBox box) {
        if (frame.w() <= 0 || frame.h() <= 0 || box == null || !box.visible) {
            return;
        }
        GovWidgets.fieldFrame(graphics, frame, box.isFocused());
    }

    // --------------------------------------------------------------------- input

    void drawTopBar(final GuiGraphics graphics) {
        final GovRect top = this.topBars[this.subtab];
        if (top.w() <= 0 || top.h() <= 0) {
            return;
        }
        GovWidgets.rule(graphics, top.x(), top.bottom(), top.w(), GovTheme.BORDER_SOFT);
        if (this.subtab == 1) {
            final int labelW = Math.min(48, Math.max(0, top.w() / 5));
            GovText.clipped(graphics, this.font, GovText.upperBoldCached(this.labelCitizen),
                    top.x() + 4, top.y() + Math.max(0, (top.h() - 9) / 2),
                    Math.max(0, labelW - 2), GovTheme.MUTED);
            drawFieldFrame(graphics, this.permitFilterFrame, this.permitTargetBox);
        } else if (this.subtab == 3) {
            drawFieldFrame(graphics, this.classifyFrame, this.classifyBox);
        }
    }

    void drawActions(final GuiGraphics graphics) {
        final GovRect action = this.actions[this.subtab];
        if (action.w() <= 0 || action.h() <= 0) {
            return;
        }
        GovWidgets.rule(graphics, action.x(), action.bottom(), action.w(),
                GovTheme.BORDER_SOFT);
    }

    void drawModalContext(final GuiGraphics graphics, final int y) {
        GovText.clipped(graphics, this.font, this.modalContext, this.modalRect.x() + 6, y,
                Math.max(0, this.modalRect.w() - 12), GovTheme.MUTED);
    }

    void drawModalLabel(final GuiGraphics graphics, final String label, final int x,
            final int y) {
        GovText.clipped(graphics, this.font, GovText.upperBoldCached(label), x, y + 4,
                Math.max(0, this.modalLabelW - 2), GovTheme.MUTED);
    }

    void drawModalClose(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (this.modalCloseRect.w() <= 0 || this.modalCloseRect.h() <= 0) {
            return;
        }
        if (this.modalCloseRect.contains(mouseX, mouseY)) {
            graphics.fill(this.modalCloseRect.x(), this.modalCloseRect.y(),
                    this.modalCloseRect.right(), this.modalCloseRect.bottom(), GovTheme.SELECTED);
        }
        GovTheme.border(graphics, this.modalCloseRect, GovTheme.BORDER);
        GovText.centered(graphics, this.font, "X", this.modalCloseRect.x()
                + this.modalCloseRect.w() / 2, this.modalCloseRect.y(), GovTheme.TEXT);
    }

    void drawRecordModal(final GuiGraphics graphics) {
        final WeaponRow row = selectedWeapon();
        if (row == null) {
            GovText.clipped(graphics, this.font, this.textNoSelection, this.modalRect.x() + 6,
                    this.modalRow1, Math.max(0, this.modalRect.w() - 12),
                    GovTheme.MUTED);
            return;
        }
        final int y = this.modalRow1;
        final int width = Math.max(0, this.modalRect.w() - 12);
        GovText.clipped(graphics, this.font, row.lineOne(),
                this.modalRect.x() + 6, y, width, GovTheme.TEXT);
        GovText.clipped(graphics, this.font, row.lineTwo(),
                this.modalRect.x() + 6, y + 11, width, GovTheme.BODY);
        if (this.modalRect.h() >= 120) {
            GovText.clipped(graphics, this.font, row.statusText(),
                    this.modalRect.x() + 6, y + 22, width, GovTheme.MUTED);
        }
    }

}
