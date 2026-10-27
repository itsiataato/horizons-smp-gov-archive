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
abstract class GovJobsPanelPart10 extends GovJobsPanelPart09 {
    GovJobsPanelPart10(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Escape consumes one layer only: the field, then the modal. With neither open the
            // panel declines, so the shell closes the dashboard. Disarming is not a layer here;
            // closing a picker cancels its arm through closeModal().
            if (blurVisibleField()) {
                return true;
            }
            if (this.modal != Modal.NONE) {
                closeModal();
                return true;
            }
            return false;
        }
        if (GovKeyRepeat.isEnter(keyCode)) {
            if (this.enterRepeat.accepts(System.currentTimeMillis())) {
                return submitEnter();
            }
            // A rate-limited Enter is still swallowed while a modal or an armed action is up, so
            // it cannot leak into the shell or a vanilla widget.
            return this.modal != Modal.NONE || anyArmed();
        }
        if (this.modal != Modal.NONE) {
            return false;
        }
        if (this.host.getFocused() instanceof EditBox) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            cycleJob(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            cycleJob(1);
            return true;
        }
        return false;
    }

    /** The focused EditBox owns character input; the panel never consumes it. */

    boolean charTyped(final char codePoint, final int modifiers) {
        return false;
    }

    /**
     * Enter submits the focused form's primary non-destructive action once per 250 ms, and may
     * confirm an action that is already armed. It never arms one: an arm needs a pointer click so
     * a key repeat cannot start a destructive sequence.
     */

    boolean submitEnter() {
        if (this.modal == Modal.CREATE) {
            createJob();
            return true;
        }
        if (this.modal == Modal.PICKER) {
            if (isAssignArmed(this.assignArmedTarget)) {
                assignTo(this.assignArmedTarget);
            }
            return true;
        }
        if (isDeleteArmed()) {
            deleteJob();
            return true;
        }
        if (isMemberArmed(this.memberArmedTarget)) {
            removeMember(this.memberArmedTarget);
            return true;
        }
        if (isCapArmed(this.capArmedTarget)) {
            removeCapability(this.capArmedTarget);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------- actions


    void openCreateModal() {
        if (!this.canManage) {
            return;
        }
        this.modal = Modal.CREATE;
        this.modalOpenedAt = System.currentTimeMillis();
        this.modalTitle = this.createTitle;
        this.status = "";
        this.statusIsError = false;
        disarmAll();
        syncCreateBox();
        rebuildCaches();
    }


    void openPicker() {
        if (!this.canAssign || this.selectedJobId.isEmpty()) {
            return;
        }
        this.modal = Modal.PICKER;
        this.modalOpenedAt = System.currentTimeMillis();
        this.modalTitle = this.pickerTitle;
        this.status = "";
        this.statusIsError = false;
        this.pickerScroll = 0;
        disarmAll();
        syncCreateBox();
        rebuildCaches();
    }


    void closeModal() {
        this.modal = Modal.NONE;
        disarmAll();
        // The arm prompt described a modal action; leaving it in the roster status bar after the
        // modal is gone would claim something is armed when nothing is.
        if (this.status.contains(this.armConfirmText)) {
            this.status = "";
            this.statusIsError = false;
        }
        syncCreateBox();
        rebuildCaches();
    }


    void createJob() {
        if (mutationPending() || !this.canManage || this.createBox == null) {
            return;
        }
        final String name = this.createBox.getValue().strip();
        if (name.isEmpty()) {
            setStatus(this.nameRequiredText, true);
            return;
        }
        mutate(OpCode.JOB_CREATE, Map.of(JobKeys.KEY_NAME, name));
    }


    void deleteJob() {
        if (!deleteEnabled()) {
            return;
        }
        if (!armDelete()) {
            return;
        }
        mutate(OpCode.JOB_DELETE, Map.of(JobKeys.KEY_JOB, this.selectedJobId));
    }


    void removeMember(final String uuid) {
        if (!memberActionsEnabled() || uuid == null || uuid.isBlank()) {
            return;
        }
        if (!armMember(uuid)) {
            return;
        }
        mutate(OpCode.JOB_ASSIGN, Map.of(
                "target", uuid,
                JobKeys.KEY_JOB, ""));
    }


    void assignTo(final String playerName) {
        if (!this.canAssign || this.selectedJobId.isEmpty()
                || playerName == null || playerName.isBlank()) {
            rebuildCaches();
            return;
        }
        if (mutationPending()) {
            return;
        }
        if (!armAssign(playerName)) {
            // The picker stays open on the first click so the operator can see the armed row and
            // confirm deliberately instead of dismissing the only context that showed the target.
            return;
        }
        this.modal = Modal.NONE;
        syncCreateBox();
        mutate(OpCode.JOB_ASSIGN, Map.of(
                "target", playerName,
                JobKeys.KEY_JOB, this.selectedJobId));
    }


    void grantCapability(final String capability) {
        if (capability == null || capability.isBlank() || mutationPending()
                || this.selectedJobId.isEmpty()
                || this.detailState != DetailState.READY
                || !this.canManage
                || JobKeys.TYPE_BUILTIN.equals(this.selectedJobType)) {
            return;
        }
        mutate(OpCode.JOB_EDIT, Map.of(
                JobKeys.KEY_JOB, this.selectedJobId,
                JobKeys.KEY_CAPABILITY, capability,
                JobKeys.KEY_MODE, JobKeys.MODE_ADD));
    }


    void removeCapability(final String capability) {
        if (capability == null || capability.isBlank() || mutationPending()
                || this.selectedJobId.isEmpty()
                || this.detailState != DetailState.READY
                || !this.canManage
                || JobKeys.TYPE_BUILTIN.equals(this.selectedJobType)) {
            return;
        }
        if (!armCap(capability)) {
            return;
        }
        mutate(OpCode.JOB_EDIT, Map.of(
                JobKeys.KEY_JOB, this.selectedJobId,
                JobKeys.KEY_CAPABILITY, capability,
                JobKeys.KEY_MODE, JobKeys.MODE_REMOVE));
    }

    // -------------------------------------------------------------------- arming


}
