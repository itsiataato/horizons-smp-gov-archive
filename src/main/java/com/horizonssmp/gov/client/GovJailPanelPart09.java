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
import com.horizonssmp.gov.common.JailKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovActionResultPayload;
import com.horizonssmp.gov.network.GovNetwork;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import static com.horizonssmp.gov.client.GovJailTypes.*;
import static com.horizonssmp.gov.client.GovJailConstants.*;

abstract class GovJailPanelPart09 extends GovJailPanelPart08 {
    GovJailPanelPart09(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active) {
            return false;
        }
        if (this.cellPromptOpen) {
            // The cell prompt is modal: Enter confirms it, Esc cancels it, and list
            // navigation is swallowed so the rows behind the modal stay inert. This
            // branch runs before the armed-action paths so neither key falls through
            // to the dashboard's onClose while the prompt is open.
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                GovJailCellPrompt.close(this);
                return true;
            }
            if (GovKeyRepeat.isEnter(keyCode)) {
                if (this.enterRepeat.accepts(System.currentTimeMillis())) {
                    GovJailCellPrompt.confirm(this);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                    || keyCode == GLFW.GLFW_KEY_PAGE_UP
                    || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // ESC unwinds one layer at a time: focused field, then an armed confirmation, and
            // only then does it fall through to the dashboard.
            if (clearFocus()) {
                return true;
            }
            if (this.detainArmed || this.releaseArmed || this.extendArmed) {
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
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            if (this.subtab == 1) {
                return false;
            }
            moveSelection(keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            if (this.subtab != 0) {
                return false;
            }
            changePage(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            if (this.subtab != 0) {
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

    /**
     * Enter confirms only an action that a click already armed, and only while that arm is live.
     * It never starts an arm itself, so a held Enter cannot arm and confirm the same action.
     */

    void submitEnter() {
        final long now = System.currentTimeMillis();
        if (this.armedAt <= 0 || now - this.armedAt > ARM_MS) {
            return;
        }
        if (this.subtab == 1 && this.detainArmed) {
            submitDetain();
        } else if (this.subtab == 2 && this.releaseArmed) {
            submitRelease();
        } else if (this.subtab == 3 && this.extendArmed) {
            submitExtend();
        }
    }


    void moveSelection(final int delta) {
        final int count = listRowCount();
        if (count == 0) {
            return;
        }
        int index = 0;
        for (int i = 0; i < count; i++) {
            if (rowId(i).equals(this.selectedId)) {
                index = i;
                break;
            }
        }
        final int next = GovMetrics.clamp(index + delta, 0, count - 1);
        selectRow(rowId(next));
        ensureSelectionVisible(next);
    }


    void ensureSelectionVisible(final int index) {
        final int scroll = this.listScrollRows[this.subtab];
        if (index < scroll) {
            this.listScrollRows[this.subtab] = index;
        } else if (index >= scroll + this.visibleRows) {
            this.listScrollRows[this.subtab] = Math.max(0, index - this.visibleRows + 1);
        }
        rebuildRowHits();
    }

    // ------------------------------------------------------------------- helpers


    String value(final EditBox box) {
        return box == null ? "" : box.getValue().strip();
    }


}
