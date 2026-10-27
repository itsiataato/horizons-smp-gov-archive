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

final class GovBusinessesRender2 {

    static     void drawDetail(final GovBusinessesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.registryDetail.w() <= 0 || p.registryDetail.h() <= 0) {
            return;
        }
        graphics.fill(p.registryDetail.x(), p.registryDetail.y(),
                p.registryDetail.right(), p.registryDetail.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, p.registryDetail, GovTheme.BORDER);

        if (p.selectedId.isEmpty() || p.detailState == LoadState.NONE) {
            GovBusinessesRender2.drawEmpty(p, graphics, p.detailBody, p.noSelectionText, "");
            return;
        }
        if (p.detailState == LoadState.LOADING) {
            GovBusinessesRender2.drawEmpty(p, graphics, p.detailBody, p.loadingText, "");
            return;
        }
        if (p.detailState == LoadState.ERROR) {
            GovBusinessesRender2.drawEmpty(p, graphics, p.detailBody,
                    p.detailErrorTitle, p.detailErrorText);
            if (p.detailRetry.w() > 0) {
                GovWidgets.button(graphics, p.font, p.detailRetry, p.retryText,
                        true, p.detailRetry.contains(mouseX, mouseY), false);
            }
            return;
        }

        graphics.drawString(p.font, p.detailNameComponent,
                p.detailHeader.x(), p.detailHeader.y() + 1, GovTheme.TEXT, false);
        if (p.detailStatusWidth > 0) {
            final int badgeX = p.detailHeader.right() - p.detailStatusWidth;
            graphics.fill(badgeX, p.detailHeader.y(), badgeX + p.detailStatusWidth,
                    p.detailHeader.y() + 12, GovTheme.PANEL_ALT);
            GovTheme.border(graphics, badgeX, p.detailHeader.y(),
                    p.detailStatusWidth, 12, GovTheme.BORDER);
            GovText.centered(graphics, p.font, p.detailStatusText, badgeX + 2,
                    p.detailHeader.y() + 2, p.detailStatusWidth - 4, GovTheme.TEXT);
        }

        final boolean scissored = GovBusinessesGeometry.clipRows(graphics, p.detailBody);
        try {
            int y = p.detailBody.y() - p.detailScroll;
            for (final DetailLine line : p.detailLines) {
                // Lines that do not fit wholly are skipped, so no text is half-painted at the
                // viewport edge even though the scissor would hide the overflow.
                if (y >= p.detailBody.y()
                        && y + line.height() <= p.detailBody.bottom()) {
                    GovBusinessesRender2.drawDetailLine(p, graphics, line, y);
                }
                y += line.height();
            }
        } finally {
            if (scissored) {
                graphics.disableScissor();
            }
        }
        GovWidgets.scrollbar(graphics, p.detailScrollTrack, p.detailScroll,
                p.detailScrollMax);
    }

    static     void drawDetailLine(final GovBusinessesPanel p, final GuiGraphics graphics, final DetailLine line, final int y) {
        if (line.wrapped() != null) {
            for (int index = 0; index < line.wrapped().size(); index++) {
                final int lineY = y + index * 10;
                if (lineY >= p.detailBody.y() && lineY + 10 <= p.detailBody.bottom()) {
                    graphics.drawString(p.font, line.wrapped().get(index),
                            p.detailBody.x(), lineY, GovTheme.BODY, false);
                }
            }
            return;
        }
        if (line.heading()) {
            if (line.bold() != null) {
                graphics.drawString(p.font, line.bold(),
                        p.detailBody.x(), y + 2, GovTheme.MUTED, false);
            }
            return;
        }
        if (line.label() != null) {
            graphics.drawString(p.font, line.label(), p.detailBody.x(), y + 2,
                    GovTheme.MUTED, false);
            graphics.drawString(p.font, line.value(), p.detailBody.x()
                    + GovMetrics.clamp(p.detailBody.w() / 3, 54, 110), y + 2,
                    GovTheme.TEXT, false);
        } else if (line.value() != null) {
            graphics.drawString(p.font, line.value(), p.detailBody.x(), y + 1,
                    GovTheme.BODY, false);
        }
    }

    static void drawEmpty(final GovBusinessesPanel p, final GuiGraphics graphics, final GovRect rect,
            final String title, final String detail) {
        if (rect == null || rect.w() <= 0 || rect.h() <= 0 || p.font == null) {
            return;
        }
        final int innerW = rect.w() - GovMetrics.GAP_S * 2;
        if (innerW <= 0) {
            return;
        }
        final String titleText = GovText.sanitize(title);
        final String detailText = GovText.sanitize(detail);
        final boolean hasTitle = !titleText.isEmpty();
        final boolean hasDetail = !detailText.isEmpty();
        if (!hasTitle && !hasDetail) {
            return;
        }
        final int contentH = (hasTitle ? p.font.lineHeight : 0)
                + (hasDetail ? (hasTitle ? GovMetrics.GAP_XS : 0) + p.font.lineHeight : 0);
        int textY = rect.y() + Math.max(0, (rect.h() - contentH) / 2);
        if (hasTitle && textY + p.font.lineHeight <= rect.bottom()) {
            // The title draws bold, which renders wider than the plain width the
            // clip measures, so it goes through the shared bold-fit helper.
            final String visible = GovWidgetPanels.fitBold(p.font, titleText, innerW);
            final int boldW = p.font.width(GovText.boldCached(visible));
            if (!visible.isEmpty()) {
                graphics.drawString(p.font, GovText.boldCached(visible),
                        rect.x() + GovMetrics.GAP_S + Math.max(0, (innerW - boldW) / 2),
                        textY, GovTheme.BODY, false);
            }
            textY += p.font.lineHeight + (hasDetail ? GovMetrics.GAP_XS : 0);
        }
        if (hasDetail && textY + p.font.lineHeight <= rect.bottom()) {
            GovText.centered(graphics, p.font, detailText,
                    rect.x() + GovMetrics.GAP_S, textY, innerW, GovTheme.FAINT);
        }
    }

    static void drawBottomBar(final GovBusinessesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.bar.w() <= 0) {
            return;
        }
        GovWidgets.rule(graphics, p.bar.x(), p.bar.y(), p.bar.w(),
                GovTheme.BORDER_SOFT);
        switch (p.subtab) {
            case MODE_REGISTRY -> {
                final boolean prev = p.page > 0 && !GovBusinessesRequests.mutationPending(p);
                final boolean next = p.page < p.pages - 1 && !GovBusinessesRequests.mutationPending(p);
                GovWidgets.button(graphics, p.font, p.registryPrev, "<",
                        prev, prev && p.registryPrev.contains(mouseX, mouseY), false);
                GovWidgets.button(graphics, p.font, p.registryNext, ">",
                        next, next && p.registryNext.contains(mouseX, mouseY), false);
                graphics.drawString(p.font, p.pageLabel, p.registryPage.x() + 2,
                        p.registryPage.y() + 4, GovTheme.MUTED, false);
            }
            case MODE_STAFF -> {
                final boolean enabled = GovBusinessesStatus.staffActionsEnabled(p);
                GovWidgets.button(graphics, p.font, p.staffAdd, p.addStaffAction,
                        enabled, enabled && p.staffAdd.contains(mouseX, mouseY), false);
                GovWidgets.button(graphics, p.font, p.staffLink, p.linkStaffAction,
                        enabled, enabled && p.staffLink.contains(mouseX, mouseY), false);
            }
            default -> {
            }
        }
        GovWidgets.button(graphics, p.font, p.refreshRect, p.refreshAction,
                !GovBusinessesRequests.mutationPending(p), p.refreshRect.contains(mouseX, mouseY), false);
        final GovRect statusArea = p.statusRect.h() > 0 ? p.statusRect : GovBusinessesCaches2.statusBar(p);
        if (p.modal == Modal.NONE && statusArea.w() > 0 && !p.statusText.isEmpty()) {
            final int statusY = p.statusRect.h() > 0
                    ? statusArea.y() + 1 : statusArea.y() + 4;
            graphics.drawString(p.font, p.statusText, statusArea.x(), statusY,
                    p.statusIsError ? GovTheme.DANGER : GovTheme.MUTED, false);
        }
    }

    static     void drawModal(final GovBusinessesPanel p, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (p.modalRect.w() <= 0 || p.modalRect.h() <= 0) {
            return;
        }
        GovWidgets.modalBackdrop(graphics, p.rect);
        final String title = p.modalClose.w() > 0
                ? GovText.clip(p.font, p.modalTitle,
                        Math.max(0, p.modalClose.x() - p.modalRect.x() - 8))
                : p.modalTitle;
        GovWidgets.modalFrame(graphics, p.font, p.modalRect, title);
        if (p.modalClose.w() > 0) {
            final boolean closeHover = p.modalClose.contains(mouseX, mouseY);
            if (closeHover) {
                graphics.fill(p.modalClose.x(), p.modalClose.y(),
                        p.modalClose.right(), p.modalClose.bottom(),
                        GovTheme.SELECTED);
            }
            GovTheme.border(graphics, p.modalClose, GovTheme.BORDER);
            GovText.centered(graphics, p.font, p.closeLetter,
                    p.modalClose.x() + p.modalClose.w() / 2,
                    p.modalClose.y(), GovTheme.TEXT);
        }
        final FormGeometry form = GovBusinessesHits.modalForm(p);
        if (form != null) {
            final String note = !p.status.isEmpty()
                    ? p.statusText : p.modalNoteText;
            GovBusinessesRenderStaff.drawForm(p, graphics, form, note, mouseX, mouseY);
        }
    }

}
