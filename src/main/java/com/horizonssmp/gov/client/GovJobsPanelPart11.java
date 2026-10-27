package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovDashboardScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.JobKeys;
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

import static com.horizonssmp.gov.client.GovJobsTypes.*;
import static com.horizonssmp.gov.client.GovJobsConstants.*;
abstract class GovJobsPanelPart11 extends GovJobsPanelPart10 {
    GovJobsPanelPart11(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    boolean armDelete() {
        final long now = System.currentTimeMillis();
        if (this.deleteArmedAt > 0 && now - this.deleteArmedAt < ARM_FLOOR_MS) {
            // A confirm inside the floor is ignored without restarting the timer, so neither a
            // double click nor Enter can extend or shortcut the arm.
            return false;
        }
        if (this.deleteArmedAt > 0 && now - this.deleteArmedAt <= ARM_MS) {
            this.deleteArmedAt = 0;
            return true;
        }
        disarmAll();
        this.deleteArmedAt = now;
        setStatus(this.deleteAction + ". " + this.armConfirmText, false);
        return false;
    }


    boolean armMember(final String uuid) {
        final long now = System.currentTimeMillis();
        if (this.memberArmedAt > 0 && uuid.equals(this.memberArmedTarget)
                && now - this.memberArmedAt < ARM_FLOOR_MS) {
            return false;
        }
        if (this.memberArmedAt > 0 && uuid.equals(this.memberArmedTarget)
                && now - this.memberArmedAt <= ARM_MS) {
            this.memberArmedAt = 0;
            this.memberArmedTarget = "";
            return true;
        }
        disarmAll();
        this.memberArmedAt = now;
        this.memberArmedTarget = uuid;
        setStatus(this.removeAction + ". " + this.armConfirmText, false);
        return false;
    }


    boolean armCap(final String capability) {
        final long now = System.currentTimeMillis();
        if (this.capArmedAt > 0 && capability.equals(this.capArmedTarget)
                && now - this.capArmedAt < ARM_FLOOR_MS) {
            return false;
        }
        if (this.capArmedAt > 0 && capability.equals(this.capArmedTarget)
                && now - this.capArmedAt <= ARM_MS) {
            this.capArmedAt = 0;
            this.capArmedTarget = "";
            return true;
        }
        disarmAll();
        this.capArmedAt = now;
        this.capArmedTarget = capability;
        setStatus(this.removeAction + ". " + this.armConfirmText, false);
        return false;
    }


    boolean armAssign(final String playerName) {
        final long now = System.currentTimeMillis();
        if (this.assignArmedAt > 0 && playerName.equals(this.assignArmedTarget)
                && now - this.assignArmedAt < ARM_FLOOR_MS) {
            return false;
        }
        if (this.assignArmedAt > 0 && playerName.equals(this.assignArmedTarget)
                && now - this.assignArmedAt <= ARM_MS) {
            this.assignArmedAt = 0;
            this.assignArmedTarget = "";
            return true;
        }
        disarmAll();
        this.assignArmedAt = now;
        this.assignArmedTarget = playerName;
        setStatus(this.assignAction + ". " + this.armConfirmText, false);
        rebuildPickerRects();
        rebuildHits();
        return false;
    }


    boolean isDeleteArmed() {
        return this.deleteArmedAt > 0
                && System.currentTimeMillis() - this.deleteArmedAt <= ARM_MS;
    }


    boolean isMemberArmed(final String uuid) {
        return this.memberArmedAt > 0 && uuid != null
                && uuid.equals(this.memberArmedTarget)
                && System.currentTimeMillis() - this.memberArmedAt <= ARM_MS;
    }


    boolean isCapArmed(final String capability) {
        return this.capArmedAt > 0 && capability != null
                && capability.equals(this.capArmedTarget)
                && System.currentTimeMillis() - this.capArmedAt <= ARM_MS;
    }


    boolean isAssignArmed(final String playerName) {
        return this.assignArmedAt > 0 && playerName != null
                && playerName.equals(this.assignArmedTarget)
                && System.currentTimeMillis() - this.assignArmedAt <= ARM_MS;
    }


    boolean anyArmed() {
        return isDeleteArmed() || isMemberArmed(this.memberArmedTarget)
                || isCapArmed(this.capArmedTarget) || isAssignArmed(this.assignArmedTarget);
    }


    void disarmAll() {
        this.deleteArmedAt = 0;
        this.memberArmedAt = 0;
        this.memberArmedTarget = "";
        this.capArmedAt = 0;
        this.capArmedTarget = "";
        this.assignArmedAt = 0;
        this.assignArmedTarget = "";
    }

    /** Drops every armed action and the prompt that described it. */

    void resetArmStatus() {
        disarmAll();
        this.status = "";
        this.statusIsError = false;
    }


    boolean createEnabled() {
        return this.canManage && !mutationPending();
    }


    boolean deleteEnabled() {
        return this.canManage && !this.selectedJobId.isEmpty()
                && this.detailState == DetailState.READY
                && !JobKeys.TYPE_BUILTIN.equals(this.selectedJobType)
                && !mutationPending();
    }


    boolean memberActionsEnabled() {
        return this.canAssign && !this.selectedJobId.isEmpty()
                && this.detailState == DetailState.READY && !mutationPending();
    }


    void setStatus(final String text, final boolean error) {
        this.status = text == null ? "" : text;
        this.statusIsError = error;
        rebuildHeaderTexts();
        rebuildStatusCache();
    }

    // ------------------------------------------------------------------- helpers


    String typeLabel(final String type) {
        if (JobKeys.TYPE_BUILTIN.equals(type)) {
            return this.builtInTypeText;
        }
        if (JobKeys.TYPE_TEMPLATE.equals(type)) {
            return this.templateTypeText;
        }
        return this.customTypeText;
    }


}
