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
import org.lwjgl.glfw.GLFW;
import com.horizonssmp.gov.client.GovBusinessesTypes.*;
import static com.horizonssmp.gov.client.GovBusinessesTypes.*;

final class GovBusinessesInput2 {

    static     boolean submitEnter(final GovBusinessesPanel p) {
        if (p.host.getFocused() instanceof MultiLineEditBox) {
            return false;
        }
        if (p.modal != Modal.NONE) {
            switch (p.modal) {
                case SUSPEND -> {
                    if (GovBusinessesArm.isArmed(p, Click.SUSPEND_SUBMIT)) {
                        GovBusinessesActions.submitSuspend(p);
                    }
                }
                case STAFF_ADD -> GovBusinessesActions.submitStaffAdd(p);
                case LINK -> GovBusinessesActions.submitLink(p);
                default -> {
                }
            }
            return true;
        }
        switch (p.subtab) {
            case MODE_NEW -> {
                GovBusinessesActions.submitRegister(p);
                return true;
            }
            case MODE_OWNERSHIP -> {
                if (GovBusinessesArm.isArmed(p, Click.TRANSFER_SUBMIT)) {
                    GovBusinessesActions.submitTransfer(p);
                    return true;
                }
                if (GovBusinessesArm.isArmed(p, Click.CLOSE_SUBMIT)) {
                    GovBusinessesActions.submitClose(p);
                    return true;
                }
                return false;
            }
            case MODE_PAYROLL -> {
                if (GovBusinessesArm.isArmed(p, Click.PAYROLL_RECORD)) {
                    GovBusinessesActions.submitPayrollRecord(p);
                } else {
                    GovBusinessesActions.submitPayrollSet(p);
                }
                return true;
            }
            default -> {
                // Registry and Staff have no non-destructive primary action.
                return false;
            }
        }
    }

    static     void cycleRegisterType(final GovBusinessesPanel p) {
        int index = 0;
        for (int slot = 0; slot < TYPE_IDS.length; slot++) {
            if (TYPE_IDS[slot].equals(p.registerType)) {
                index = slot;
                break;
            }
        }
        p.registerType = TYPE_IDS[(index + 1) % TYPE_IDS.length];
        GovBusinessesCaches2.rebuildFormValues(p);
    }

    static     void cycleEmployeeRole(final GovBusinessesPanel p) {
        int index = 0;
        for (int slot = 0; slot < ROLE_IDS.length; slot++) {
            if (ROLE_IDS[slot].equals(p.employeeRole)) {
                index = slot;
                break;
            }
        }
        p.employeeRole = ROLE_IDS[(index + 1) % ROLE_IDS.length];
        GovBusinessesCaches2.rebuildFormValues(p);
    }

    static     void openSuspendModal(final GovBusinessesPanel p) {
        if (p.selectedId.isEmpty() || p.detailState != LoadState.READY
                || !GovBusinessesStatus.canManageBusiness(p)) {
            return;
        }
        p.modal = Modal.SUSPEND;
        p.modalOpenedAt = System.currentTimeMillis();
        p.modalTitle = GovBusinessesUtil.tr("gui.gov.businesses.suspend_title", GovBusinessesStatus.businessName(p));
        p.modalNoteText = p.suspendNoteText;
        p.status = "";
        p.statusIsError = false;
        GovBusinessesArm.disarmAll(p);
        GovBusinessesWidgets.syncWidgets(p);
        GovBusinessesCaches.rebuildCaches(p);
    }

    static     void openStaffAddModal(final GovBusinessesPanel p) {
        if (p.selectedId.isEmpty() || p.detailState != LoadState.READY
                || !GovBusinessesStatus.canEdit(p)) {
            return;
        }
        p.modal = Modal.STAFF_ADD;
        p.modalOpenedAt = System.currentTimeMillis();
        p.modalTitle = GovBusinessesUtil.tr("gui.gov.businesses.staff.add_title", GovBusinessesStatus.businessName(p));
        p.modalNoteText = "";
        p.status = "";
        p.statusIsError = false;
        GovBusinessesArm.disarmAll(p);
        GovBusinessesWidgets.syncWidgets(p);
        GovBusinessesCaches.rebuildCaches(p);
    }

    static     void openLinkModal(final GovBusinessesPanel p) {
        if (p.selectedId.isEmpty() || p.detailState != LoadState.READY
                || !GovBusinessesStatus.canEdit(p)) {
            return;
        }
        p.modal = Modal.LINK;
        p.modalOpenedAt = System.currentTimeMillis();
        p.modalTitle = GovBusinessesUtil.tr("gui.gov.businesses.link_title", GovBusinessesStatus.businessName(p));
        p.modalNoteText = p.linkNoteText;
        p.status = "";
        p.statusIsError = false;
        GovBusinessesArm.disarmAll(p);
        if (p.linkJobBox != null) {
            p.linkJobBox.setValue(p.detail.getOrDefault(BusinessKeys.JOB, ""));
        }
        GovBusinessesWidgets.syncWidgets(p);
        GovBusinessesCaches.rebuildCaches(p);
    }

    static     void closeModal(final GovBusinessesPanel p) {
        p.modal = Modal.NONE;
        GovBusinessesArm.disarmAll(p);
        GovBusinessesWidgets.syncWidgets(p);
        GovBusinessesCaches.rebuildCaches(p);
    }

}
