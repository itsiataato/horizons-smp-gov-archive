package com.horizonssmp.gov.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.JailKeys;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import static com.horizonssmp.gov.client.GovJailTypes.*;
import static com.horizonssmp.gov.client.GovJailConstants.*;

/**
 * The Cells subtab (4) runtime-cell rows: parsing, layout, buttons and rendering.
 *
 * <p>The JAIL_LIST delta carries the true total in {@code cellCount} plus up to
 * {@link JailKeys#MAX_LISTED_CELLS} dense rows. Part03 feeds this helper through the
 * {@code applyCells} hook; Part10 delegates list layout, row buttons and drawing; Part11
 * drives per-row armed deletes through the same ARM windows as the typed-name fallback.
 * The count line is built in code ("1 cell" / "N cells"); the empty state reuses no-cell text.
 */
final class GovJailCellList {

    private GovJailCellList() {
    }

    /** One runtime cell row: the name plus the verbatim dimension and block coordinates. */
    record CellRow(String name, String dimension, int x, int y, int z) {

        String title() {
            return this.name == null ? "" : this.name;
        }

        String detail() {
            final String dim = this.dimension == null ? "" : this.dimension;
            final String coords = this.x + " " + this.y + " " + this.z;
            return dim.isEmpty() ? coords : dim + "  " + coords;
        }

        String single() {
            final String tail = detail();
            return tail.isEmpty() ? title() : title() + "  " + tail;
        }
    }

    static void signedOnly(final EditBox box) {
        if (box == null) {
            return;
        }
        box.setResponder(value -> {
            final String raw = value == null ? "" : value;
            final String digits = raw.replaceAll("[^0-9]", "");
            final String next = raw.startsWith("-") ? "-" + digits : digits;
            if (!next.equals(raw)) {
                box.setValue(next);
            }
        });
    }

    /** Reads {@code cellCount} plus the {@code cell.<i>.*} rows; skips rows without a name. */
    static void parse(final GovJailPanelPart10 panel, final Map<String, String> fields) {
        final List<CellRow> rows = new ArrayList<>();
        for (int i = 0; i < JailKeys.MAX_LISTED_CELLS; i++) {
            final String prefix = JailKeys.CELL_ROW_PREFIX + i;
            final String name = fields.get(prefix + JailKeys.CELL_ROW_NAME);
            if (name == null || name.isBlank()) {
                continue;
            }
            rows.add(new CellRow(name,
                    fields.getOrDefault(prefix + JailKeys.CELL_ROW_DIMENSION, ""),
                    intField(fields, prefix + JailKeys.CELL_ROW_X, 0),
                    intField(fields, prefix + JailKeys.CELL_ROW_Y, 0),
                    intField(fields, prefix + JailKeys.CELL_ROW_Z, 0)));
        }
        panel.cellRows.clear();
        panel.cellRows.addAll(rows);
        panel.cellCount = Math.max(0, intField(fields, JailKeys.CELL_LIST_COUNT, 0));
        if (panel.cellSelected >= rows.size()) {
            panel.cellSelected = rows.isEmpty() ? -1 : rows.size() - 1;
        }
        if (panel.cellDeleteArmed && !contains(panel.cellRows, panel.cellDeleteArmedName)) {
            panel.cellDeleteArmed = false;
            panel.cellDeleteArmedName = "";
            panel.cellDeleteArmedAt = 0L;
            panel.status = "";
            panel.statusIsError = false;
        }
        clampScroll(panel);
    }

    /** Row index of the named cell, or {@code -1} when blank or absent. */
    static int indexOf(final List<CellRow> rows, final String name) {
        if (name != null && !name.isBlank()) {
            for (int i = 0; i < rows.size(); i++) {
                if (name.equals(rows.get(i).name())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean contains(final List<CellRow> rows, final String name) {
        return indexOf(rows, name) >= 0;
    }

    private static void clampScroll(final GovJailPanelPart10 panel) {
        panel.cellScrollMax = Math.max(0,
                panel.cellRows.size() - Math.max(0, panel.cellVisibleRows));
        panel.cellScroll = GovMetrics.clamp(panel.cellScroll, 0, panel.cellScrollMax);
    }

    /** Keeps the keyboard cursor inside the visible window after it moves. */
    static void reveal(final GovJailPanelPart10 panel) {
        if (panel.cellSelected < 0 || panel.cellVisibleRows <= 0) {
            return;
        }
        if (panel.cellSelected < panel.cellScroll) {
            panel.cellScroll = panel.cellSelected;
        } else if (panel.cellSelected >= panel.cellScroll + panel.cellVisibleRows) {
            panel.cellScroll = panel.cellSelected - panel.cellVisibleRows + 1;
        }
        clampScroll(panel);
    }

    /**
     * Places the count line and the scrollable list below the create/delete form. When the body
     * is too short for even that row, both stay empty and the form keeps the existing behaviour.
     */
    static void layout(final GovJailPanelPart10 panel) {
        final GovRect body = panel.bodies[4];
        if (body.w() <= 0 || body.h() <= 0) {
            panel.cellCountRect = EMPTY;
            panel.cellListRect = EMPTY;
            panel.cellHits.clear();
            panel.cellVisibleRows = 0;
            clampScroll(panel);
            return;
        }
        final int rowY = panel.cellDeleteRect.h() > 0
                ? panel.cellDeleteRect.bottom() + FORM_GAP : body.y();
        final int rowH = Math.max(10, panel.cellCreateRect.h());
        if (rowY >= body.bottom()) {
            panel.cellCountRect = EMPTY;
            panel.cellListRect = EMPTY;
            panel.cellHits.clear();
            panel.cellVisibleRows = 0;
            clampScroll(panel);
            return;
        }
        final int countH = Math.min(rowH, body.bottom() - rowY);
        final int refreshRight = panel.cellCreateRect.w() > 0
                ? panel.cellCreateRect.x() : body.right() - 4;
        panel.cellCountRect = panel.rect(body.x() + 2, rowY,
                Math.max(0, refreshRight - 4 - body.x() - 2), countH);
        final int listY = rowY + countH + 1;
        panel.cellListRect = listY >= body.bottom() ? EMPTY
                : panel.rect(body.x(), listY, body.w(), body.bottom() - listY);
        final GovRect list = panel.cellListRect;
        panel.cellVisibleRows = list.h() > 0 && panel.rowPitch > 0
                ? list.h() / panel.rowPitch : 0;
        clampScroll(panel);
        if (panel.cellSelected >= 0) {
            reveal(panel);
        }
    }

    /**
     * Adds the Refresh control beside the count line plus one delete button per visible row, and
     * rebuilds the row-body hits. Row buttons stay inert until the list is READY, mirroring the
     * shared row-hit guard, so a click can never act on a stale result set.
     */
    static void addCellButtons(final GovJailPanelPart10 panel) {
        panel.cellHits.clear();
        final boolean free = !panel.mutationPending();
        if (panel.cellCountRect.w() > 0 && panel.cellCountRect.h() > 0
                && panel.cellCreateRect.w() > 0 && panel.cellCreateRect.h() > 0) {
            panel.addButton(panel.cellCreateRect.x(), panel.cellCountRect.y(),
                    panel.cellCreateRect.w(),
                    Math.min(panel.cellCreateRect.h(), panel.cellCountRect.h()),
                    Click.REFRESH, "", panel.refreshLabel(), free, false, false);
        }
        if (panel.listState != LoadState.READY) {
            return;
        }
        final GovRect list = panel.cellListRect;
        final GovRect body = panel.bodies[4];
        if (list.w() <= 0 || list.h() <= 0 || body.w() <= 0 || body.h() <= 0
                || panel.rowPitch <= 0) {
            return;
        }
        for (int i = 0; i < panel.cellVisibleRows; i++) {
            final int index = panel.cellScroll + i;
            if (index >= panel.cellRows.size()) {
                break;
            }
            final CellRow row = panel.cellRows.get(index);
            final int y = list.y() + i * panel.rowPitch;
            final GovRect hit = panel.rect(list.x() + 1, y, Math.max(0, list.w() - 2),
                    Math.max(0, panel.rowPitch - 1));
            if (hit.h() <= 0) {
                continue;
            }
            panel.cellHits.add(new Hit(hit, Click.ROW, row.name()));
            final GovRect delete = deleteRect(panel, hit);
            final boolean armed = panel.cellDeleteArmed
                    && row.name().equals(panel.cellDeleteArmedName);
            panel.addButton(delete.x(), delete.y(), delete.w(), delete.h(),
                    Click.CELL_DELETE_ROW, row.name(), "X", panel.canDetain && free, false,
                    armed);
        }
    }

    private static GovRect deleteRect(final GovJailPanelPart10 panel, final GovRect hit) {
        final int deleteW = GovMetrics.span(hit.w(), 30, 26, 40);
        return panel.rect(Math.max(hit.x(), hit.right() - deleteW - 2), hit.y() + 1, deleteW,
                Math.max(0, hit.h() - 2));
    }

    private static String countLine(final GovJailPanelPart10 panel) {
        final int total = Math.max(0, panel.cellCount);
        final String head = total == 1 ? "1 cell" : total + " cells";
        if (total > panel.cellRows.size() && !panel.cellRows.isEmpty()) {
            return head + " (" + panel.cellRows.size() + " shown)";
        }
        return head;
    }

    /**
     * Draws the count line, then one row per cell with its delete button; the empty state reuses
     * the existing no-cell text. Loading and error keep the panel's early returns in
     * {@code drawCells}, so they are not repeated here.
     */
    static void draw(final GovJailPanelPart10 panel, final GuiGraphics graphics,
            final int mouseX, final int mouseY) {
        final Font font = panel.font;
        if (font == null) {
            return;
        }
        if (panel.cellCountRect.w() > 0 && panel.cellCountRect.h() > 0) {
            GovText.clipped(graphics, font, countLine(panel),
                    panel.cellCountRect.x() + 2, panel.cellCountRect.y() + 1,
                    Math.max(0, panel.cellCountRect.w() - 4), GovTheme.MUTED);
        }
        final GovRect list = panel.cellListRect;
        if (list.w() <= 0 || list.h() <= 0) {
            return;
        }
        graphics.fill(list.x(), list.y(), list.right(), list.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, list, GovTheme.BORDER_SOFT);
        if (panel.cellRows.isEmpty()) {
            GovText.clipped(graphics, font, panel.textNoCell, list.x() + 6, list.y() + 4,
                    Math.max(0, list.w() - 12), GovTheme.MUTED);
            return;
        }
        for (int i = 0; i < panel.cellVisibleRows; i++) {
            final int index = panel.cellScroll + i;
            if (index >= panel.cellRows.size()) {
                break;
            }
            final CellRow row = panel.cellRows.get(index);
            final int y = list.y() + i * panel.rowPitch;
            final boolean armed = panel.cellDeleteArmed
                    && row.name().equals(panel.cellDeleteArmedName);
            final boolean hovered = i < panel.cellHits.size()
                    && panel.cellHits.get(i).rect().contains(mouseX, mouseY);
            if (armed || index == panel.cellSelected) {
                graphics.fill(list.x() + 1, y, list.right() - 1, y + panel.rowPitch - 1,
                        GovTheme.SELECTED);
                graphics.fill(list.x() + 1, y, list.x() + 4, y + panel.rowPitch - 1,
                        GovTheme.ACCENT);
            } else if (hovered) {
                graphics.fill(list.x() + 1, y, list.right() - 1, y + panel.rowPitch - 1,
                        GovTheme.HOVER);
            }
            final GovRect hit = i < panel.cellHits.size() ? panel.cellHits.get(i).rect() : null;
            final int textRight = hit == null ? list.right() - 6
                    : deleteRect(panel, hit).x() - 4;
            final int textX = list.x() + 7;
            final int textW = Math.max(0, textRight - textX);
            if (panel.rowPitch >= 17) {
                GovText.clipped(graphics, font, row.title(), textX, y + 1, textW,
                        GovTheme.TEXT);
                GovText.clipped(graphics, font, row.detail(), textX, y + 10, textW,
                        GovTheme.MUTED);
            } else {
                GovText.clipped(graphics, font, row.single(), textX, y + 2, textW,
                        GovTheme.TEXT);
            }
        }
        if (panel.cellScrollMax > 0) {
            GovWidgets.scrollbar(graphics,
                    panel.rect(list.right() - 4, list.y(), 3, list.h()),
                    panel.cellScroll * panel.rowPitch, panel.cellScrollMax * panel.rowPitch);
        }
    }
}
