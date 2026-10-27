package com.horizonssmp.gov.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.OpCode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;

import static com.horizonssmp.gov.client.GovJailTypes.*;
import static com.horizonssmp.gov.client.GovJailConstants.*;

/**
 * The Cells subtab (4): create a runtime jail cell and delete one by name.
 *
 * <p>Command parity with {@code /gov jail new <name> <here|x,y,z>} and
 * {@code /gov jail delete <name>}: the Here button resolves the viewer's block position and
 * dimension into the coordinate fields, a blank dimension sends {@code minecraft:overworld},
 * and delete is a two-step armed confirm like the other destructive jail actions. Both buttons
 * stay disabled without {@code jail.commit} ({@code canDetain}); the server enforces the same
 * capability. A create or delete success flows through the shared mutate path, which refreshes
 * the list and surfaces the server's own message on failure.
 */
class GovJailPanelPart10 extends GovJailPanelPart09 {
    GovJailPanelPart10(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    EditBox cellNameBox;
    EditBox cellXBox;
    EditBox cellYBox;
    EditBox cellZBox;
    EditBox cellDimBox;
    EditBox cellDeleteBox;

    GovRect cellInfoRect = EMPTY;
    GovRect cellNameFrame = EMPTY;
    GovRect cellXFrame = EMPTY;
    GovRect cellYFrame = EMPTY;
    GovRect cellZFrame = EMPTY;
    GovRect cellDimFrame = EMPTY;
    GovRect cellHereRect = EMPTY;
    GovRect cellCreateRect = EMPTY;
    GovRect cellDeleteFrame = EMPTY;
    GovRect cellDeleteRect = EMPTY;

    boolean cellDeleteArmed;
    long cellDeleteArmedAt;
    String cellDeleteArmedName = "";

    final List<GovJailCellList.CellRow> cellRows = new ArrayList<>();
    int cellCount;
    int cellSelected = -1;
    int cellScroll;
    int cellScrollMax;
    int cellVisibleRows;
    GovRect cellCountRect = EMPTY;
    GovRect cellListRect = EMPTY;
    final List<Hit> cellHits = new ArrayList<>();

    final String labelName = tr("gui.gov.jail.cell_name");
    final String labelPosition = tr("gui.gov.jail.cell_position");
    final String labelDimension = tr("gui.gov.jail.cell_dimension");
    final String labelDelete = tr("gui.gov.jail.cell_delete_label");
    final String buttonHere = caps(tr("gui.gov.jail.cell_here"));
    final String buttonCreate = caps(tr("gui.gov.jail.cell_create"));
    final String buttonDelete = caps(tr("gui.gov.jail.cell_delete"));
    final String textNameRequired = tr("gui.gov.jail.cell_name_required");
    final String textCoordsRequired = tr("gui.gov.jail.cell_coords_required");

    @Override
    List<EditBox> createWidgets(final Font font) {
        final List<EditBox> base = super.createWidgets(font);
        if (this.cellNameBox != null) {
            return this.widgets;
        }
        this.cellNameBox = box(font, 32, "gui.gov.jail.cell_name_hint");
        this.cellXBox = box(font, 10, "gui.gov.jail.cell_x_hint");
        this.cellYBox = box(font, 10, "gui.gov.jail.cell_y_hint");
        this.cellZBox = box(font, 10, "gui.gov.jail.cell_z_hint");
        this.cellDimBox = box(font, 64, "gui.gov.jail.cell_dimension_hint");
        this.cellDeleteBox = box(font, 32, "gui.gov.jail.cell_name_hint");
        GovJailCellList.signedOnly(this.cellXBox);
        GovJailCellList.signedOnly(this.cellYBox);
        GovJailCellList.signedOnly(this.cellZBox);
        final List<EditBox> all = new ArrayList<>(base);
        all.addAll(List.of(this.cellNameBox, this.cellXBox, this.cellYBox, this.cellZBox,
                this.cellDimBox, this.cellDeleteBox));
        this.widgets = List.copyOf(all);
        return this.widgets;
    }

    @Override
    void syncWidgets() {
        super.syncWidgets();
        final boolean on = this.active && this.subtab == 4;
        setVisible(this.cellNameBox, on);
        setVisible(this.cellXBox, on);
        setVisible(this.cellYBox, on);
        setVisible(this.cellZBox, on);
        setVisible(this.cellDimBox, on);
        setVisible(this.cellDeleteBox, on);
    }

    @Override
    void disarm() {
        super.disarm();
        if (this.cellDeleteArmed) {
            this.cellDeleteArmed = false;
            this.cellDeleteArmedAt = 0L;
            this.cellDeleteArmedName = "";
            rebuildButtons();
        }
    }

    @Override
    void clearFields() {
        super.clearFields();
        setValue(this.cellNameBox, "");
        setValue(this.cellXBox, "");
        setValue(this.cellYBox, "");
        setValue(this.cellZBox, "");
        setValue(this.cellDimBox, "");
        setValue(this.cellDeleteBox, "");
        this.cellDeleteArmed = false;
        this.cellDeleteArmedAt = 0L;
        this.cellDeleteArmedName = "";
    }

    @Override
    void applyCells(final Map<String, String> fields) {
        GovJailCellList.parse(this, fields);
    }

    @Override
    void updateSummaryTexts() {
        if (this.subtab != 4) {
            super.updateSummaryTexts();
            return;
        }
        this.detailTitle = "";
        this.summaryText = this.cellConfigured && !this.cell.isBlank()
                ? this.cellText : this.textNoCell;
        this.extendCeiling = "";
    }

    // ------------------------------------------------------------------ layout

    @Override
    void layout(final int left, final int top, final int right, final int bottom,
            final Font font) {
        super.layout(left, top, right, bottom, font);
        layoutCells(left, top, right, bottom,
                this.bounds.h() < 130 ? 10 : 12);
        // layoutCells assigns the cell rects after the shared layout already rebuilt the
        // buttons, so rebuild once more: on a resize the buttons would otherwise keep the
        // previous geometry while the fields move.
        rebuildButtons();
    }

    void layoutCells(final int left, final int top, final int right, final int bottom,
            final int statusH) {
        this.topBars[4] = EMPTY;
        this.bottoms[4] = EMPTY;
        this.statuses[4] = rect(left, bottom - statusH, Math.max(0, right - left), statusH);
        this.bodies[4] = rect(left, top + 2, Math.max(0, right - left),
                this.statuses[4].y() - top - 2);
        this.lists[4] = EMPTY;
        this.listBodies[4] = EMPTY;
        this.details[4] = EMPTY;
        this.summaries[4] = EMPTY;
        final GovRect body = this.bodies[4];
        if (body.w() <= 0 || body.h() <= 0) {
            return;
        }
        final int labelW = Math.min(52, Math.max(0, body.w() / 5));
        final int fieldX = body.x() + labelW + 4;
        final int fieldW = Math.max(0, body.right() - 8 - fieldX);
        final int rowH = Math.min(FORM_ROW, Math.max(0, (body.h() - FORM_GAP * 5) / 6));
        final int row1 = body.y();
        final int row2 = row1 + rowH + FORM_GAP;
        final int row3 = row2 + rowH + FORM_GAP;
        final int row4 = row3 + rowH + FORM_GAP;
        final int row5 = row4 + rowH + FORM_GAP;
        final int row6 = row5 + rowH + FORM_GAP;
        this.cellInfoRect = rect(fieldX, row1, fieldW, rowH);
        this.cellNameFrame = rect(fieldX, row2, fieldW, rowH);
        final int coordW = Math.max(0, (fieldW - 8) / 3);
        this.cellXFrame = rect(fieldX, row3, coordW, rowH);
        this.cellYFrame = rect(fieldX + coordW + 4, row3, coordW, rowH);
        this.cellZFrame = rect(fieldX + coordW * 2 + 8, row3,
                Math.max(0, fieldW - coordW * 2 - 8), rowH);
        final int hereW = GovMetrics.span(fieldW, fieldW / 4, 44, 64);
        this.cellDimFrame = rect(fieldX, row4, Math.max(0, fieldW - hereW - 4), rowH);
        this.cellHereRect = rect(fieldX + fieldW - hereW, row4, hereW, rowH);
        final int createW = GovMetrics.span(body.w() - 8, fieldW * 55 / 100, 84, 120);
        this.cellCreateRect = rect(body.right() - 4 - createW, row5, createW, rowH);
        final int deleteW = GovMetrics.span(fieldW, fieldW / 3, 60, 84);
        this.cellDeleteFrame = rect(fieldX, row6, Math.max(0, fieldW - deleteW - 4), rowH);
        this.cellDeleteRect = rect(fieldX + fieldW - deleteW, row6, deleteW, rowH);
        placeBox(this.cellNameBox, this.cellNameFrame);
        placeBox(this.cellXBox, this.cellXFrame);
        placeBox(this.cellYBox, this.cellYFrame);
        placeBox(this.cellZBox, this.cellZFrame);
        placeBox(this.cellDimBox, this.cellDimFrame);
        placeBox(this.cellDeleteBox, this.cellDeleteFrame);
        GovJailCellList.layout(this);
    }

    // ----------------------------------------------------------------- buttons

    @Override
    void rebuildButtons() {
        if (this.subtab != 4) {
            super.rebuildButtons();
            return;
        }
        this.buttons.clear();
        final boolean free = !mutationPending();
        addButton(this.cellHereRect.x(), this.cellHereRect.y(), this.cellHereRect.w(),
                this.cellHereRect.h(), Click.CELL_HERE, "", this.buttonHere, free, false,
                false);
        addButton(this.cellCreateRect.x(), this.cellCreateRect.y(), this.cellCreateRect.w(),
                this.cellCreateRect.h(), Click.CELL_CREATE, "",
                this.buttonCreate, this.canDetain && free, true, false);
        addButton(this.cellDeleteRect.x(), this.cellDeleteRect.y(), this.cellDeleteRect.w(),
                this.cellDeleteRect.h(), Click.CELL_DELETE, "",
                this.cellDeleteArmed ? confirmLabel(this.buttonDelete) : this.buttonDelete,
                this.canDetain && free, true, this.cellDeleteArmed);
        GovJailCellList.addCellButtons(this);
    }

    // ------------------------------------------------------------------ render

    @Override
    void render(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (!this.active || this.font == null) {
            return;
        }
        if (this.subtab != 4) {
            super.render(graphics, mouseX, mouseY);
            return;
        }
        drawCells(graphics, mouseX, mouseY);
        drawButtons(graphics, mouseX, mouseY);
        drawStatus(graphics);
    }

    void drawCells(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final GovRect body = this.bodies[4];
        if (body.w() <= 0 || body.h() <= 0) {
            return;
        }
        if (this.listState == LoadState.LOADING) {
            GovText.clipped(graphics, this.font, this.textLoading, body.x() + 6, body.y() + 4,
                    Math.max(0, body.w() - 12), GovTheme.MUTED);
            return;
        }
        if (this.listState == LoadState.ERROR) {
            GovText.clipped(graphics, this.font, this.textError, body.x() + 6, body.y() + 4,
                    Math.max(0, body.w() - 12), GovTheme.TEXT);
            if (!this.listError.isEmpty()) {
                GovText.clipped(graphics, this.font, this.listError, body.x() + 6,
                        body.y() + 15, Math.max(0, body.w() - 12), GovTheme.MUTED);
            }
            return;
        }
        final int labelW = Math.min(52, Math.max(0, body.w() / 5));
        final int labelX = body.x() + 2;
        GovText.clipped(graphics, this.font, this.summaryText, this.cellInfoRect.x() + 2,
                this.cellInfoRect.y() + 1, Math.max(0, this.cellInfoRect.w() - 4),
                GovTheme.MUTED);
        drawFormLabel(graphics, this.labelName, labelX, this.cellNameFrame.y(), labelW);
        drawFieldFrame(graphics, this.cellNameFrame, this.cellNameBox);
        drawFormLabel(graphics, this.labelPosition, labelX, this.cellXFrame.y(), labelW);
        drawFieldFrame(graphics, this.cellXFrame, this.cellXBox);
        drawFieldFrame(graphics, this.cellYFrame, this.cellYBox);
        drawFieldFrame(graphics, this.cellZFrame, this.cellZBox);
        drawFormLabel(graphics, this.labelDimension, labelX, this.cellDimFrame.y(), labelW);
        drawFieldFrame(graphics, this.cellDimFrame, this.cellDimBox);
        drawFormLabel(graphics, this.labelDelete, labelX, this.cellDeleteFrame.y(), labelW);
        drawFieldFrame(graphics, this.cellDeleteFrame, this.cellDeleteBox);
        GovJailCellList.draw(this, graphics, mouseX, mouseY);
    }
}
