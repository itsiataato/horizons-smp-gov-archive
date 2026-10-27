package com.horizonssmp.gov.client;

import net.minecraft.client.gui.GuiGraphics;

final class GovPeopleRender {
    static void render(final GovPeoplePanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (!p.active || p.font == null) {
            return;
        }
        switch (p.mode) {
            case GovPeoplePanel.SUBTAB_SEARCH -> drawSearch(p, graphics, mouseX, mouseY);
            case GovPeoplePanel.SUBTAB_PERSON -> drawPerson(p, graphics, mouseX, mouseY);
            case GovPeoplePanel.SUBTAB_NOTES -> GovPeopleRenderNotes.drawNotes(p, graphics, mouseX, mouseY);
            default -> {
            }
        }
    }

    static void drawSearch(final GovPeoplePanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        GovWidgets.fieldFrame(graphics, p.searchFieldRect,
                p.searchBox != null && p.searchBox.isFocused());
        drawButton(p, graphics, p.searchButtonRect, p.searchButtonText, GovPeopleHits.searchEnabled(p),
                mouseX, mouseY, true);
        if (!p.searchCountText.isEmpty()) {
            GovText.clipped(graphics, p.font, p.searchCountText, p.countRect.x(),
                    p.countRect.y(), p.countRect.w(), GovTheme.MUTED);
        }
        drawListFrame(graphics, p.listRect);
        if (p.searchState == GovPeoplePanelModel.LoadState.IDLE) {
            GovWidgets.emptyState(graphics, p.font, p.listInnerRect, p.idleText, null);
        } else if (p.searchState == GovPeoplePanelModel.LoadState.LOADING) {
            GovWidgets.emptyState(graphics, p.font, p.listInnerRect, p.loadingText, null);
        } else if (p.searchState == GovPeoplePanelModel.LoadState.ERROR) {
            GovWidgets.emptyState(graphics, p.font, p.listInnerRect, p.searchErrorText,
                    p.searchError);
        } else if (p.personViews.isEmpty()) {
            GovWidgets.emptyState(graphics, p.font, p.listInnerRect, p.noResultsText, null);
        } else {
            drawPersonRows(p, graphics, mouseX, mouseY);
        }
        drawBottomBar(p, graphics, mouseX, mouseY);
    }

    static void drawPersonRows(final GovPeoplePanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (p.listInnerRect.w() <= 0 || p.listInnerRect.h() <= 0) {
            return;
        }
        graphics.enableScissor(p.listInnerRect.x(), p.listInnerRect.y(),
                p.listInnerRect.right(), p.listInnerRect.bottom());
        final int textW = Math.max(1, p.listRect.w() - 12);
        int y = p.listRect.y() + 1 - p.searchScroll;
        for (int index = 0; index < p.personViews.size(); index++) {
            final GovPeoplePanelModel.PersonView view = p.personViews.get(index);
            if (y + p.searchRowH >= p.listInnerRect.y()
                    && y >= p.listInnerRect.y()
                    && y + p.searchRowH <= p.listInnerRect.bottom()) {
                final boolean selected = view.uuid().equals(p.selectedUuid);
                final boolean hovered = GovTheme.inside(mouseX, mouseY,
                        p.listRect.x() + 2, y, p.listRect.w() - 6,
                        p.searchRowH - 1);
                if (selected) {
                    graphics.fill(p.listRect.x() + 2, y, p.listRect.right() - 2,
                            y + p.searchRowH - 1, GovTheme.SELECTED);
                    graphics.fill(p.listRect.x() + 2, y, p.listRect.x() + 5,
                            y + p.searchRowH - 1, GovTheme.ACCENT);
                } else if (hovered) {
                    graphics.fill(p.listRect.x() + 2, y, p.listRect.right() - 2,
                            y + p.searchRowH - 1, GovTheme.HOVER);
                }
                GovText.clipped(graphics, p.font, view.line1(), p.listRect.x() + 8, y,
                        textW, selected ? GovTheme.TEXT : GovTheme.BODY);
                GovText.clipped(graphics, p.font, view.line2(), p.listRect.x() + 8, y + 9,
                        textW, GovTheme.FAINT);
                if (!view.line3().isEmpty()) {
                    GovText.clipped(graphics, p.font, view.line3(),
                            p.listRect.x() + 8, y + 18, textW, GovTheme.MUTED);
                }
            }
            y += p.searchRowH;
        }
        graphics.disableScissor();
        if (p.searchScrollMax > 0) {
            GovTheme.scrollbar(graphics, p.listRect.right() - 4, p.listRect.y() + 2,
                    Math.max(1, p.listRect.h() - 4), p.searchScroll, p.searchScrollMax);
        }
    }

    static void drawPerson(final GovPeoplePanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (p.headerRect.w() > 0 && p.headerRect.h() > 0) {
            GovText.clipped(graphics, p.font, p.personTitleComponent, p.headerRect.x(),
                    p.headerRect.y(), p.personTitleWidth, GovTheme.TEXT);
            GovText.right(graphics, p.font, p.cardStatusText, p.headerRect.right(),
                    p.headerRect.y(), GovTheme.MUTED);
            GovText.clipped(graphics, p.font, p.cardUuidText, p.headerRect.x(),
                    p.headerRect.y() + 9, p.headerRect.w(), GovTheme.FAINT);
            GovWidgets.rule(graphics, p.headerRect.x(), p.headerRect.bottom() - 1,
                    p.headerRect.w(), GovTheme.BORDER);
        }
        drawListFrame(graphics, p.personFormRect);
        if (p.selectedUuid.isEmpty()) {
            GovWidgets.emptyState(graphics, p.font, p.personFormInnerRect,
                    p.noCitizenText, p.noSelectionText);
        } else if (p.detailState == GovPeoplePanelModel.LoadState.LOADING) {
            GovWidgets.emptyState(graphics, p.font, p.personFormInnerRect,
                    p.loadingText, null);
        } else if (p.detailState == GovPeoplePanelModel.LoadState.ERROR) {
            GovWidgets.emptyState(graphics, p.font, p.personFormInnerRect,
                    p.detailErrorText, p.detailError);
        } else if (p.detailState == GovPeoplePanelModel.LoadState.READY) {
            drawDetailLines(p, graphics);
        } else {
            GovWidgets.emptyState(graphics, p.font, p.personFormInnerRect,
                    p.loadingText, null);
        }
        drawBottomBar(p, graphics, mouseX, mouseY);
    }

    static void drawDetailLines(final GovPeoplePanel p, final GuiGraphics graphics) {
        if (p.detailLines.isEmpty() || p.personFormInnerRect.w() <= 0) {
            return;
        }
        graphics.enableScissor(p.personFormInnerRect.x(), p.personFormInnerRect.y(),
                p.personFormInnerRect.right(), p.personFormInnerRect.bottom());
        if (p.personTwoColumn) {
            final int columnW = Math.max(1,
                    (p.personFormInnerRect.w() - GovMetrics.GAP_S) / 2);
            final int rightX = p.personFormInnerRect.x() + columnW + GovMetrics.GAP_S;
            int y = p.personFormInnerRect.y() - p.personScroll;
            for (int row = 0; row < p.personRowsPerColumn; row++) {
                final int leftIndex = row;
                final int rightIndex = row + p.personRowsPerColumn;
                if (leftIndex < p.detailLines.size()) {
                    drawDetailLine(p, graphics, p.detailLines.get(leftIndex),
                            p.personFormInnerRect.x(), y, p.personLabelW,
                            p.personValueW);
                }
                if (rightIndex < p.detailLines.size()) {
                    drawDetailLine(p, graphics, p.detailLines.get(rightIndex), rightX, y,
                            p.personLabelW, p.personValueW);
                }
                y += p.personPitch;
            }
            final int summaryIndex = p.personRowsPerColumn * 2;
            if (summaryIndex < p.detailLines.size()) {
                drawDetailLine(p, graphics, p.detailLines.get(summaryIndex),
                        p.personFormInnerRect.x(), y, p.personLabelW,
                        p.personFullValueW);
            }
        } else {
            int y = p.personFormInnerRect.y() - p.personScroll;
            for (final GovPeoplePanelModel.DetailLine line : p.detailLines) {
                drawDetailLine(p, graphics, line, p.personFormInnerRect.x(), y,
                        p.personLabelW, p.personValueW);
                y += line.wrapped().isEmpty() ? p.personPitch
                        : Math.max(p.personPitch,
                                line.wrapped().size() * p.font.lineHeight);
            }
        }
        graphics.disableScissor();
        if (p.personScrollMax > 0) {
            GovTheme.scrollbar(graphics, p.personFormRect.right() - 4,
                    p.personFormInnerRect.y(), Math.max(1, p.personFormInnerRect.h()),
                    p.personScroll, p.personScrollMax);
        }
    }

    static void drawDetailLine(final GovPeoplePanel p, final GuiGraphics graphics,
            final GovPeoplePanelModel.DetailLine line, final int x, final int y, final int labelW,
            final int valueW) {
        if (y + p.font.lineHeight <= p.personFormInnerRect.y()
                || y >= p.personFormInnerRect.bottom()) {
            return;
        }
        GovText.clipped(graphics, p.font, line.label(), x, y, labelW, GovTheme.MUTED);
        final int valueX = x + labelW + GovMetrics.GAP_XS;
        if (!line.wrapped().isEmpty()) {
            for (int index = 0; index < line.wrapped().size(); index++) {
                final int lineY = y + index * p.font.lineHeight;
                if (lineY + p.font.lineHeight <= p.personFormInnerRect.bottom()) {
                    graphics.drawString(p.font, line.wrapped().get(index), valueX, lineY,
                            line.color(), false);
                }
            }
        } else {
            GovText.clipped(graphics, p.font, line.value(), valueX, y, valueW, line.color());
        }
    }

    static void drawListFrame(final GuiGraphics graphics, final GovRect rect) {
        if (rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, rect, GovTheme.BORDER);
    }

    static void drawButton(final GovPeoplePanel p, final GuiGraphics graphics, final GovRect rect,
            final String label, final boolean enabled, final int mouseX, final int mouseY,
            final boolean primary) {
        if (rect.w() <= 0 || rect.h() <= 0) {
            return;
        }
        GovWidgets.button(graphics, p.font, rect, label, enabled,
                enabled && rect.contains(mouseX, mouseY), primary);
    }

    static void drawStatus(final GovPeoplePanel p, final GuiGraphics graphics, final GovRect rect) {
        if (rect.w() <= 0 || p.status.isEmpty()) {
            return;
        }
        GovText.clipped(graphics, p.font, p.status, rect.x(), rect.y() + 4, rect.w(),
                p.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE);
    }

    static void drawBottomBar(final GovPeoplePanel p, final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        GovWidgets.rule(graphics, p.barRect.x(), p.barRect.y() - 1, p.barRect.w(),
                GovTheme.BORDER_SOFT);
        int statusX = p.barRect.x();
        int statusW = Math.max(0, p.refreshRect.x() - statusX - GovMetrics.GAP_XS);
        if (p.mode == GovPeoplePanel.SUBTAB_PERSON && p.selectedUuid.isEmpty()) {
            drawButton(p, graphics, p.openSearchRect, p.openSearchText, true,
                    mouseX, mouseY, false);
            statusX = p.openSearchRect.right() + GovMetrics.GAP_XS;
            statusW = Math.max(0, p.refreshRect.x() - statusX - GovMetrics.GAP_XS);
        }
        if (statusW > 0 && !p.status.isEmpty()) {
            GovText.clipped(graphics, p.font, p.status, statusX, p.barRect.y() + 4,
                    statusW, p.statusIsError ? GovTheme.DANGER : GovTheme.POSITIVE);
        } else if (statusW > 0 && p.mode == GovPeoplePanel.SUBTAB_SEARCH && p.countRect.h() <= 0
                && !p.searchCountText.isEmpty()) {
            GovText.clipped(graphics, p.font, p.searchCountText, statusX,
                    p.barRect.y() + 4, statusW, GovTheme.MUTED);
        }
        final boolean refreshEnabled = p.mode == GovPeoplePanel.SUBTAB_SEARCH
                ? p.searchBox != null && !p.searchBox.getValue().isBlank()
                        && !GovPeopleUtil.mutationPending(p)
                : !p.selectedUuid.isEmpty() && !GovPeopleUtil.mutationPending(p);
        drawButton(p, graphics, p.refreshRect, p.refreshText, refreshEnabled,
                mouseX, mouseY, false);
    }
}
