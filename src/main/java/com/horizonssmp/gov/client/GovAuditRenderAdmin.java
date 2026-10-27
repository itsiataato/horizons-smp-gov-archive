package com.horizonssmp.gov.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

final class GovAuditRenderAdmin {
    static void drawAdmin(final GovAuditPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        GovText.clipped(graphics, p.font, p.adminHeaderComponent,
                p.adminHeaderRect.x(), p.adminHeaderRect.y(), p.adminHeaderRect.w(),
                GovTheme.TEXT);
        GovWidgets.rule(graphics, p.adminHeaderRect.x(),
                p.adminHeaderRect.bottom() - 1, p.adminHeaderRect.w(),
                GovTheme.BORDER);
        if (!p.canAdmin) {
            drawWrappedList(p, graphics, p.adminLockedLines, p.adminBodyRect);
            return;
        }
        if (p.adminState == GovAuditPanel.LoadState.LOADING && p.admin.isEmpty()) {
            GovAuditRender.drawButton(p, graphics, p.adminRefreshRect, adminRefreshLabel(p), false, mouseX,
                    mouseY, false);
            GovWidgets.emptyState(graphics, p.font, p.adminStateRect, p.loadingText,
                    null);
            return;
        }
        if (p.adminState == GovAuditPanel.LoadState.ERROR && p.admin.isEmpty()) {
            GovAuditRender.drawButton(p, graphics, p.adminRefreshRect, adminRefreshLabel(p), true, mouseX,
                    mouseY, false);
            GovWidgets.emptyState(graphics, p.font, p.adminStateRect, p.errorText,
                    p.adminError);
            return;
        }

        GovAuditRender.drawButton(p, graphics, p.adminBeginRect, p.beginText, !GovAuditCaches.mutationPending(p),
                mouseX, mouseY, false);
        GovAuditRender.drawButton(p, graphics, p.adminCommitRect,
                p.commitArmed ? p.confirmCommitText : p.commitText,
                !GovAuditCaches.mutationPending(p), mouseX, mouseY, true);
        GovAuditRender.drawButton(p, graphics, p.adminRefreshRect, adminRefreshLabel(p), true, mouseX, mouseY,
                false);
        GovWidgets.fieldFrame(graphics, p.tokenRect,
                p.tokenBox != null && p.tokenBox.isFocused());
        GovText.clipped(graphics, p.font, p.sectionText, p.sectionLabelRect.x(),
                p.sectionLabelRect.y() + 4, p.sectionLabelRect.w(), GovTheme.BODY);
        GovAuditRender.drawButton(p, graphics, p.sectionPrevRect, p.arrowLeftText, !GovAuditCaches.mutationPending(p),
                mouseX, mouseY, false);
        GovAuditRender.drawButton(p, graphics, p.sectionNextRect, p.arrowRightText, !GovAuditCaches.mutationPending(p),
                mouseX, mouseY, false);
        GovAuditRender.drawButton(p, graphics, p.sectionExportRect,
                p.exportArm == GovAuditPanel.ExportArm.SECTION ? p.confirmExportText
                        : p.exportSectionText,
                !GovAuditCaches.mutationPending(p), mouseX, mouseY, false);
        drawAdminInfo(p, graphics);
    }

    static String adminRefreshLabel(final GovAuditPanel p) {
        return p.adminState == GovAuditPanel.LoadState.ERROR ? p.retryText : p.refreshText;
    }

    static void drawAdminInfo(final GovAuditPanel p, final GuiGraphics graphics) {
        if (p.adminInfoRect.w() <= 0 || p.adminInfoRect.h() <= 0) {
            return;
        }
        int y = p.adminInfoRect.y();
        int lines = Math.max(0, p.adminInfoRect.h() / GovMetrics.LINE_BODY);
        if (!p.status.isEmpty() && lines > 0) {
            GovText.clipped(graphics, p.font, p.status, p.adminInfoRect.x(), y,
                    p.adminInfoRect.w(),
                    p.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE);
            y += GovMetrics.LINE_BODY;
            lines--;
        }
        for (int index = 0; index < p.adminWarningLines.size() && lines > 0; index++) {
            if (y + p.font.lineHeight <= p.adminInfoRect.bottom()) {
                graphics.drawString(p.font, p.adminWarningLines.get(index),
                        p.adminInfoRect.x(), y, GovTheme.WARN, false);
            }
            y += p.font.lineHeight;
            lines--;
        }
        for (int index = 0; index < p.adminInfoLines.size() && lines > 0; index++) {
            GovText.clipped(graphics, p.font, p.adminInfoLines.get(index),
                    p.adminInfoRect.x(), y, p.adminInfoRect.w(), GovTheme.MUTED);
            y += GovMetrics.LINE_BODY;
            lines--;
        }
    }

    static void drawWrappedList(final GovAuditPanel p, final GuiGraphics graphics,
            final List<FormattedCharSequence> lines, final GovRect rect) {
        if (rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        int y = rect.y();
        for (int index = 0; index < lines.size(); index++) {
            if (y + p.font.lineHeight > rect.bottom()) {
                break;
            }
            graphics.drawString(p.font, lines.get(index), rect.x(), y, GovTheme.MUTED,
                    false);
            y += p.font.lineHeight;
        }
    }
}