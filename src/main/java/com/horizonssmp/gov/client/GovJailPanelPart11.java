package com.horizonssmp.gov.client;

import java.util.Map;

import com.horizonssmp.gov.common.OpCode;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import static com.horizonssmp.gov.client.GovJailTypes.*;
import static com.horizonssmp.gov.client.GovJailConstants.*;

/**
 * The Cells subtab (4): input handling and submit paths for the cell form.
 *
 * <p>The Here button resolves the viewer's block position and dimension into the coordinate
 * fields, mirroring {@code /gov jail new <name> here}; delete is a two-step armed confirm like
 * the other destructive jail actions, with the same floor and expiry windows. Each rendered row
 * carries its own delete button sharing that arm state by name, while a row-body click selects
 * the row and mirrors its name into the delete field. Enter confirms only an already-armed
 * delete, and ESC unwinds the armed delete before anything else.
 */
abstract class GovJailPanelPart11 extends GovJailPanelPart10 {
    GovJailPanelPart11(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    @Override
    boolean focusField(final double mouseX, final double mouseY) {
        if (this.subtab != 4) {
            return super.focusField(mouseX, mouseY);
        }
        boolean focused = false;
        focused |= focusIf(this.cellNameBox, this.cellNameFrame, mouseX, mouseY);
        focused |= focusIf(this.cellXBox, this.cellXFrame, mouseX, mouseY);
        focused |= focusIf(this.cellYBox, this.cellYFrame, mouseX, mouseY);
        focused |= focusIf(this.cellZBox, this.cellZFrame, mouseX, mouseY);
        focused |= focusIf(this.cellDimBox, this.cellDimFrame, mouseX, mouseY);
        focused |= focusIf(this.cellDeleteBox, this.cellDeleteFrame, mouseX, mouseY);
        return focused;
    }

    @Override
    boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.active || button != 0 || this.subtab != 4) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (!focusField(mouseX, mouseY)) {
            clearFocus();
        }
        for (final Button entry : this.buttons) {
            if (entry.enabled() && entry.rect().contains(mouseX, mouseY)) {
                if (entry.click() == Click.CELL_DELETE_ROW) {
                    submitCellDeleteRow(entry.value());
                } else {
                    handleClick(entry.click());
                }
                return true;
            }
        }
        for (final Hit hit : this.cellHits) {
            if (hit.rect().contains(mouseX, mouseY)) {
                selectCell(hit.value());
                return true;
            }
        }
        return false;
    }

    @Override
    boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollY) {
        if (this.active && this.subtab == 4 && this.cellListRect.contains(mouseX, mouseY)
                && this.cellScrollMax > 0) {
            final int direction = scrollY < 0 ? 1 : -1;
            this.cellScroll = GovMetrics.clamp(this.cellScroll + direction, 0,
                    this.cellScrollMax);
            rebuildButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    void moveSelection(final int delta) {
        if (this.subtab != 4) {
            super.moveSelection(delta);
            return;
        }
        if (this.cellRows.isEmpty()) {
            return;
        }
        final int next = GovMetrics.clamp(this.cellSelected + delta, 0,
                this.cellRows.size() - 1);
        selectCell(this.cellRows.get(next).name());
    }

    void selectCell(final String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        final int index = GovJailCellList.indexOf(this.cellRows, name);
        if (index < 0) {
            return;
        }
        this.cellSelected = index;
        setValue(this.cellDeleteBox, name);
        if (this.cellDeleteArmed && !name.equals(this.cellDeleteArmedName)) {
            this.cellDeleteArmed = false;
            this.cellDeleteArmedName = "";
            this.cellDeleteArmedAt = 0L;
            this.status = "";
            this.statusIsError = false;
        }
        GovJailCellList.reveal(this);
        rebuildButtons();
    }

    @Override
    void handleClick(final Click click) {
        if (click == Click.CELL_HERE) {
            fillCellHere();
            return;
        }
        if (click == Click.CELL_CREATE) {
            submitCellCreate();
            return;
        }
        if (click == Click.CELL_DELETE) {
            submitCellDelete();
            return;
        }
        super.handleClick(click);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (this.active && keyCode == GLFW.GLFW_KEY_ESCAPE && this.cellDeleteArmed) {
            disarm();
            this.status = "";
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    void submitEnter() {
        if (this.subtab == 4 && this.cellDeleteArmed) {
            submitCellDelete();
            return;
        }
        super.submitEnter();
    }

    void fillCellHere() {
        // The dashboard requires a player, so a null viewer is unreachable; without one there
        // is no position to fill from (previously this filled 0,64,0 defaults).
        final var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        setValue(this.cellXBox, Integer.toString(player.getBlockX()));
        setValue(this.cellYBox, Integer.toString(player.getBlockY()));
        setValue(this.cellZBox, Integer.toString(player.getBlockZ()));
        setValue(this.cellDimBox, player.level().dimension().location().toString());
    }

    void submitCellCreate() {
        if (mutationPending()) {
            return;
        }
        final String name = value(this.cellNameBox);
        if (name.isEmpty()) {
            fail(this.textNameRequired);
            return;
        }
        final Map<String, String> payload = GovJailCellRequests.createPayload(name,
                value(this.cellXBox), value(this.cellYBox), value(this.cellZBox),
                value(this.cellDimBox));
        if (payload == null) {
            fail(this.textCoordsRequired);
            return;
        }
        mutate(OpCode.JAIL_CELL_CREATE, payload);
    }

    /**
     * Shared two-step armed delete for the typed name field and the per-row delete buttons.
     *
     * <p>Note: the typed path previously submitted on a name mismatch inside the arm window
     * (it re-armed only on expiry); it now re-arms on a mismatch like the row path does, so a
     * retyped name always confirms what the delete field shows.
     */
    void submitCellDeleteArmed(final String target) {
        if (mutationPending()) {
            return;
        }
        final String name = target == null ? "" : target.strip();
        if (name.isEmpty()) {
            fail(this.textNameRequired);
            return;
        }
        final long now = System.currentTimeMillis();
        if (!this.cellDeleteArmed || !name.equals(this.cellDeleteArmedName)
                || now - this.cellDeleteArmedAt > ARM_MS) {
            this.cellDeleteArmed = true;
            this.cellDeleteArmedAt = now;
            this.cellDeleteArmedName = name;
            this.status = confirmLabel(this.buttonDelete);
            this.statusIsError = false;
            rebuildButtons();
            return;
        }
        if (now - this.cellDeleteArmedAt < ARM_FLOOR_MS) {
            return;
        }
        this.cellDeleteArmed = false;
        this.cellDeleteArmedAt = 0L;
        this.cellDeleteArmedName = "";
        final Map<String, String> payload = GovJailCellRequests.deletePayload(name);
        if (payload == null) {
            fail(this.textNameRequired);
            return;
        }
        mutate(OpCode.JAIL_CELL_DELETE, payload);
    }

    void submitCellDelete() {
        submitCellDeleteArmed(value(this.cellDeleteBox));
    }

    void submitCellDeleteRow(final String name) {
        if (mutationPending()) {
            return;
        }
        final String target = name == null ? "" : name.strip();
        if (!target.isEmpty()) {
            setValue(this.cellDeleteBox, target);
        }
        submitCellDeleteArmed(target);
    }
}
