package com.horizonssmp.gov.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;

final class GovDashboardRender {
    private GovDashboardRender() {}

     static void drawHeader(final GovDashboardScreen s, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final GovRect header = s.layout.header();
        // Two rules under the header, inside its ink band so no paper or tab can overpaint them.
        GovWidgets.rule(graphics, header.x() + 1, header.bottom() - 2, header.w() - 2,
                GovTheme.BORDER);
        GovWidgets.rule(graphics, header.x() + 1, header.bottom() - 1, header.w() - 2,
                GovTheme.BORDER_SOFT);

        graphics.blit(GovDashboardScreen.SEAL_TEXTURE, s.sealRect.x(), s.sealRect.y(), 0.0F, 0.0F,
                s.sealRect.w(), s.sealRect.h(), 18, 18);

        if (s.titleWidth > 0) {
            GovText.clipped(graphics, s.font(), s.systemLine, s.titleX,
                    s.headerBlockY, s.titleWidth, GovTheme.HEADER_MUTED);
            GovText.clipped(graphics, s.font(), s.agencyLine, s.titleX,
                    s.headerBlockY + 9, s.titleWidth, GovTheme.HEADER_TEXT);
        }
        if (s.seasonX >= 0) {
            GovText.clipped(graphics, s.font(), s.seasonText, s.seasonX,
                    s.layout.header().y() + (s.layout.header().h() - 9) / 2,
                    s.seasonWidth, GovTheme.HEADER_MUTED);
        }
        if (s.stampRect.w() > 0) {
            GovWidgets.stamp(graphics, s.font(), s.stampRect, s.officialText, true);
        }

        final boolean hover = s.closeRect.contains(mouseX, mouseY);
        if (hover) {
            graphics.fill(s.closeRect.x(), s.closeRect.y(), s.closeRect.right(),
                    s.closeRect.bottom(), GovTheme.HEADER_TEXT);
        }
        GovTheme.border(graphics, s.closeRect,
                hover ? GovTheme.HEADER_TEXT : GovTheme.HEADER_MUTED);
        GovText.centered(graphics, s.font(), s.closeLetter,
                s.closeRect.x() + s.closeRect.w() / 2,
                s.closeRect.y() + 3,
                hover ? GovTheme.HEADER : GovTheme.HEADER_TEXT);
    }
     static void drawRail(final GovDashboardScreen s, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final GovRect rail = s.layout.rail();
        final int visibleCount = s.visibleTabs.size();
        for (int index = 0; index < visibleCount; index++) {
            final GovRect row = s.railRects[index];
            if (row == null || row.w() <= 0 || row.h() <= 0) {
                continue;
            }
            final GovDashboardScreen.Tab tab = s.visibleTabs.get(index);
            final int tabIndex = tab.ordinal();
            final boolean selected = tab == s.activeTab;
            final boolean hovered = row.contains(mouseX, mouseY);
            if (hovered) {
                s.hoveredRail = index;
            }
            if (selected) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), GovTheme.SELECTED);
            } else if (hovered) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), GovTheme.HOVER);
            }
            if (selected) {
                final boolean rightEdge = s.railTwoColumn
                        && index >= (visibleCount + 1) / 2;
                final int barX = rightEdge ? rail.right() - 4 : rail.x();
                graphics.fill(barX, row.y(), barX + 3, row.bottom(), GovTheme.ACCENT);
            }

            final int iconSize = Math.min(Math.min(16, row.h()),
                    Math.max(14, s.railRowHeight - 4));
            final int iconX = s.layout.breakpoint() == GovLayout.Breakpoint.WIDE
                    ? row.x() + 4 : row.x() + (row.w() - iconSize) / 2;
            final int iconY = row.y() + (row.h() - iconSize) / 2;
            GovIcons.draw(graphics, GovDashboardScreen.SECTION_ICONS[tabIndex], iconX, iconY, iconSize);

            if (s.layout.breakpoint() == GovLayout.Breakpoint.WIDE) {
                final int labelX = iconX + iconSize + 4;
                final int labelWidth = row.right() - 4 - labelX;
                if (labelWidth > 0 && row.h() >= 9) {
                    final Component label = selected ? s.sectionBold[tabIndex]
                            : s.sectionLabels[tabIndex];
                    GovText.clipped(graphics, s.font(), label, labelX,
                            row.y() + (row.h() - 9) / 2, labelWidth,
                            selected ? GovTheme.TEXT : hovered ? GovTheme.BODY : GovTheme.MUTED);
                }
            }
        }

        drawAppointmentStrip(s, graphics);
    }
     static void drawAppointmentStrip(final GovDashboardScreen s, final GuiGraphics graphics) {
        final GovRect rail = s.layout.rail();
        if (s.railStripHeight <= 0) {
            return;
        }
        GovWidgets.rule(graphics, rail.x() + 1, s.railStripTop, rail.w() - 2,
                GovTheme.BORDER_SOFT);
        final int gap = GovMetrics.clamp((s.railStripHeight - 27) / 4, 0, 4);
        final int textX = rail.x() + 4;
        final int textW = rail.w() - 8;
        final int stripBottom = s.railStripTop + s.railStripHeight;
        int y = s.railStripTop + gap;
        if (y + 9 <= stripBottom) {
            GovText.clipped(graphics, s.font(),
                    s.layout.breakpoint() == GovLayout.Breakpoint.WIDE
                            ? s.appointmentWide : s.appointmentShort,
                    textX, y, textW, GovTheme.FAINT);
        }
        y += 9 + gap;
        if (y + 9 <= stripBottom) {
            GovText.clipped(graphics, s.font(), s.railJob, textX, y, textW, GovTheme.BODY);
        }
        y += 9 + gap;
        if (y + 9 <= stripBottom) {
            GovText.clipped(graphics, s.font(), s.railDuty, textX, y, textW,
                    GovTheme.MUTED);
        }
    }
     static void drawSubtabs(final GovDashboardScreen s, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final int selectedOriginal = s.subtabs[s.activeTab.ordinal()];
        final List<Integer> visible = s.visibleSubtabs[s.activeTab.ordinal()];
        final Component[] labels = s.subtabLabels[s.activeTab.ordinal()];
        final Component[] bold = s.subtabBold[s.activeTab.ordinal()];
        for (int slot = 0; slot < s.subtabRects.length; slot++) {
            final GovRect rect = s.subtabRects[slot];
            if (rect.w() <= 0 || rect.h() <= 0) {
                continue;
            }
            final int original = visible.get(slot);
            final boolean selected = original == selectedOriginal;
            final boolean hovered = rect.contains(mouseX, mouseY);
            if (selected) {
                graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), GovTheme.FIELD);
                graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + 2, GovTheme.ACCENT);
            } else {
                graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), GovTheme.PANEL_ALT);
                if (hovered) {
                    graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), GovTheme.HOVER);
                }
                if (slot > 0) {
                    graphics.fill(rect.x(), rect.y(), rect.x() + 1, rect.bottom(),
                            GovTheme.BORDER_SOFT);
                }
            }
            if (rect.h() >= 9) {
                GovText.clipped(graphics, s.font(),
                        (Component) (selected ? bold[original] : labels[original]),
                        rect.x() + 4, rect.y() + (rect.h() - 9) / 2,
                        rect.w() - 8, selected ? GovTheme.TEXT
                                : hovered ? GovTheme.BODY : GovTheme.MUTED);
            }
        }
        if (s.subtabOverflow) {
            GovWidgets.button(graphics, s.font(), s.subtabPrevRect, s.tabPrevText,
                    s.subtabPrevEnabled,
                    s.subtabPrevEnabled && s.subtabPrevRect.contains(mouseX, mouseY),
                    false);
            GovWidgets.button(graphics, s.font(), s.subtabNextRect, s.tabNextText,
                    s.subtabNextEnabled,
                    s.subtabNextEnabled && s.subtabNextRect.contains(mouseX, mouseY),
                    false);
        }
    }
     static void drawContent(final GovDashboardScreen s, final GuiGraphics graphics, final int mouseX, final int mouseY) {
        switch (s.activeTab) {
            case LAW -> s.lawPanel.render(graphics, mouseX, mouseY);
            case JOBS -> s.jobsPanel.render(graphics, mouseX, mouseY);
            case PEOPLE -> s.peoplePanel.render(graphics, mouseX, mouseY);
            case NOTES -> s.notesPanel.render(graphics, mouseX, mouseY);
            case FINES -> s.finesPanel.render(graphics, mouseX, mouseY);
            case VEHICLES -> s.vehiclesPanel.render(graphics, mouseX, mouseY);
            case WEAPONS -> s.weaponsPanel.render(graphics, mouseX, mouseY);
            case BUSINESSES -> s.businessesPanel.render(graphics, mouseX, mouseY);
            case JAIL -> s.jailPanel.render(graphics, mouseX, mouseY);
            case AUDIT -> s.auditPanel.render(graphics, mouseX, mouseY);
            case TREASURY -> s.treasuryPanel.render(graphics, mouseX, mouseY);
            case STORE -> s.treasuryPanel.render(graphics, mouseX, mouseY);
            default -> GovDashboardCardRender.drawCard(s, graphics, mouseX, mouseY);
        }
    }
     static void drawFooter(final GovDashboardScreen s, final GuiGraphics graphics) {
        final GovRect footer = s.layout.footer();
        final int pad = s.layout.breakpoint() == GovLayout.Breakpoint.COMPACT ? 6 : 10;
        final int y = footer.y() + (footer.h() - 9) / 2;
        GovWidgets.rule(graphics, footer.x() + 1, footer.y(), footer.w() - 2,
                GovTheme.BORDER_SOFT);

        if (s.layout.breakpoint() == GovLayout.Breakpoint.COMPACT) {
            final int leftWidth = Math.min(s.font().width(s.footerLeft),
                    Math.max(0, footer.w() / 2 - pad));
            GovText.clipped(graphics, s.font(), s.footerLeft, footer.x() + pad, y,
                    leftWidth, GovTheme.FAINT);
            final int rightWidth = s.font().width(s.footerRight);
            final int rightX = Math.max(footer.x() + pad + leftWidth + 6,
                    footer.right() - pad - rightWidth);
            final int rightAvailable = Math.max(0, footer.right() - pad - rightX);
            GovText.clipped(graphics, s.font(), s.footerRight, rightX, y,
                    Math.min(rightWidth, rightAvailable), GovTheme.FAINT);
            return;
        }

        // The centre mark is reserved first at MEDIUM/WIDE; the side strings clip around it.
        final int centerWidth = s.font().width(s.footerOnly);
        final int centerX = footer.x() + (footer.w() - centerWidth) / 2;
        final int leftX = footer.x() + pad;
        final int leftLimit = Math.max(leftX, centerX - 8);
        final int leftWidth = Math.max(0,
                Math.min(s.font().width(s.footerLeft), leftLimit - leftX));
        GovText.clipped(graphics, s.font(), s.footerLeft, leftX, y, leftWidth,
                GovTheme.FAINT);

        final int rightEdge = footer.right() - pad;
        final int rightStart = centerX + centerWidth + 8;
        final int rightX = Math.max(rightStart, rightEdge - s.font().width(s.footerRight));
        final int rightWidth = Math.max(0,
                Math.min(s.font().width(s.footerRight), rightEdge - rightX));
        GovText.clipped(graphics, s.font(), s.footerRight, rightX, y, rightWidth,
                GovTheme.FAINT);
        GovText.clipped(graphics, s.font(), s.footerOnly, centerX, y, centerWidth,
                GovTheme.MUTED);
    }
}
