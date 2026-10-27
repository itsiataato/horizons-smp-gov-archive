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

abstract class GovWeaponsPanelPart12 extends GovWeaponsPanelPart11 {
    GovWeaponsPanelPart12(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }


    boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.active || button != 0) {
            return false;
        }
        // A click anywhere outside the focused field leaves it, before the click is routed.
        if (this.host.getFocused() instanceof EditBox focused
                && this.widgets.contains(focused)
                && !fieldContains(focused, mouseX, mouseY)) {
            this.host.setFocused(null);
        }
        if (this.modal != Modal.NONE) {
            for (final Button entry : this.modalButtons) {
                if (entry.enabled() && entry.rect().contains(mouseX, mouseY)) {
                    handleClick(entry.click(), entry.value());
                    return true;
                }
            }
            focusModalField(mouseX, mouseY);
            if (System.currentTimeMillis() - this.modalOpenedAt >= 250L
                    && !this.modalRect.contains(mouseX, mouseY)) {
                this.status = "";
                this.statusIsError = false;
                closeModal();
            }
            return true;
        }
        for (final Button entry : this.buttons) {
            if (entry.enabled() && entry.rect().contains(mouseX, mouseY)) {
                handleClick(entry.click(), entry.value());
                return true;
            }
        }
        for (final Hit hit : this.rowHits) {
            if (hit.rect().contains(mouseX, mouseY)) {
                handleClick(hit.click(), hit.value());
                return true;
            }
        }
        focusMainField(mouseX, mouseY);
        return false;
    }

    /** True when the mouse is over the visible frame that owns the field. */

    boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollY) {
        if (!this.active) {
            return false;
        }
        if (this.modal != Modal.NONE) {
            return true;
        }
        final int direction = scrollY < 0 ? 1 : -1;
        final GovRect list = this.listBodies[this.subtab];
        if (list.contains(mouseX, mouseY) && this.listScrollMaxRows > 0) {
            this.listScrollRows[this.subtab] = GovMetrics.clamp(
                    this.listScrollRows[this.subtab] + direction, 0, this.listScrollMaxRows);
            rebuildRowHits();
            return true;
        }
        final GovRect detail = this.details[this.subtab];
        if (detail.contains(mouseX, mouseY) && this.detailScrollMaxRows > 0) {
            this.detailScrollRows[this.subtab] = GovMetrics.clamp(
                    this.detailScrollRows[this.subtab] + direction, 0, this.detailScrollMaxRows);
            return true;
        }
        return false;
    }

    void ensureSelectionVisible(final int index) {
        final int scroll = this.listScrollRows[this.subtab];
        if (index < scroll) {
            this.listScrollRows[this.subtab] = index;
        } else if (index >= scroll + this.visibleRows) {
            this.listScrollRows[this.subtab] = Math.max(0,
                    index - this.visibleRows + 1);
        }
        rebuildRowHits();
    }

    // ------------------------------------------------------------------- helpers

    void moveSelection(final int delta) {
        final int count = listRowCount();
        if (count == 0) {
            return;
        }
        int index = -1;
        for (int i = 0; i < count; i++) {
            if (rowId(i).equals(selectionKey())) {
                index = i;
                break;
            }
        }
        final int next = GovMetrics.clamp(index + delta, 0, count - 1);
        if (this.subtab == 0) {
            selectWeapon(rowId(next));
        } else if (this.subtab == 1) {
            selectPermit(rowId(next));
        } else if (this.subtab == 2) {
            selectArea(rowId(next));
        }
        ensureSelectionVisible(next);
    }

    boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.host.getFocused() instanceof EditBox box && box.isVisible()) {
                this.host.setFocused(null);
                return true;
            }
            if (this.modal != Modal.NONE) {
                closeModal();
                return true;
            }
            if (this.armedAction != Click.NONE) {
                disarm();
                this.status = "";
                return true;
            }
            return false;
        }
        if (GovKeyRepeat.isEnter(keyCode)) {
            if (this.enterRepeat.accepts(System.currentTimeMillis())) {
                submitEnter();
            }
            return true;
        }
        if (this.host.getFocused() != null) {
            return false;
        }
        if (this.modal != Modal.NONE) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            if (this.subtab == 3) {
                return false;
            }
            final int delta = keyCode == GLFW.GLFW_KEY_UP ? -1 : 1;
            moveSelection(delta);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            if (this.subtab == 3) {
                return false;
            }
            changePage(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            if (this.subtab == 3) {
                return false;
            }
            changePage(1);
            return true;
        }
        return false;
    }

    boolean charTyped(final char codePoint, final int modifiers) {
        return false;
    }

}
