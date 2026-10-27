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

import com.horizonssmp.gov.client.GovBusinessesTypes.*;
import static com.horizonssmp.gov.client.GovBusinessesTypes.*;

final class GovBusinessesArm {

    static     boolean arm(final GovBusinessesPanel p, final Click click) {
        final long now = System.currentTimeMillis();
        final long armedAt = GovBusinessesArm.armedAt(p, click);
        if (armedAt > 0 && now - armedAt <= ARM_MS) {
            if (now - armedAt < ARM_FLOOR_MS) {
                return false;
            }
            GovBusinessesArm.clearArmed(p, click);
            return true;
        }
        GovBusinessesArm.disarmAll(p);
        GovBusinessesArm.setArmed(p, click, now);
        if (click == Click.PAYROLL_RECORD) {
            p.payrollRecordArmedAmount = p.amountBox == null
                    ? "" : p.amountBox.getValue().strip();
            p.payrollRecordArmedRecipient = GovBusinessesStatus.businessName(p);
        }
        GovBusinessesStatus.setStatus(p, GovBusinessesArm.armMessage(p, click), false);
        GovBusinessesCaches2.rebuildFormValues(p);
        return false;
    }

    static     void disarm(final GovBusinessesPanel p, final Click click) {
        final boolean wasArmed = GovBusinessesArm.isArmed(p, click);
        GovBusinessesArm.clearArmed(p, click);
        if (click == Click.PAYROLL_RECORD) {
            p.payrollRecordArmedAmount = "";
            p.payrollRecordArmedRecipient = "";
            p.payrollRecordArmedLabel = "";
        }
        if (wasArmed && !p.statusIsError) {
            p.status = "";
        }
        GovBusinessesCaches2.rebuildStatusCache(p);
        if (p.font != null) {
            GovBusinessesCaches2.rebuildFormValues(p);
        }
    }

    static     boolean armStaffRemove(final GovBusinessesPanel p, final String uuid) {
        final long now = System.currentTimeMillis();
        if (p.staffRemoveArmedAt > 0 && uuid.equals(p.staffRemoveTarget)
                && now - p.staffRemoveArmedAt <= ARM_MS) {
            if (now - p.staffRemoveArmedAt < ARM_FLOOR_MS) {
                return false;
            }
            p.staffRemoveArmedAt = 0;
            p.staffRemoveTarget = "";
            return true;
        }
        GovBusinessesArm.disarmAll(p);
        p.staffRemoveArmedAt = now;
        p.staffRemoveTarget = uuid;
        GovBusinessesStatus.setStatus(p, p.armConfirmText, false);
        return false;
    }

    static     boolean isStaffRemoveArmed(final GovBusinessesPanel p, final String uuid) {
        return p.staffRemoveArmedAt > 0 && uuid != null
                && uuid.equals(p.staffRemoveTarget)
                && System.currentTimeMillis() - p.staffRemoveArmedAt <= ARM_MS;
    }

    static     boolean isArmed(final GovBusinessesPanel p, final Click click) {
        final long armedAt = GovBusinessesArm.armedAt(p, click);
        return armedAt > 0
                && System.currentTimeMillis() - armedAt <= ARM_MS;
    }

    static     long armedAt(final GovBusinessesPanel p, final Click click) {
        return switch (click) {
            case TRANSFER_SUBMIT -> p.transferArmedAt;
            case SUSPEND_SUBMIT -> p.suspendArmedAt;
            case CLOSE_SUBMIT -> p.closeArmedAt;
            case DELETE_SUBMIT -> p.deleteArmedAt;
            case PAYROLL_RECORD -> p.payrollRecordArmedAt;
            default -> 0L;
        };
    }

    static     void setArmed(final GovBusinessesPanel p, final Click click, final long at) {
        switch (click) {
            case TRANSFER_SUBMIT -> p.transferArmedAt = at;
            case SUSPEND_SUBMIT -> p.suspendArmedAt = at;
            case CLOSE_SUBMIT -> p.closeArmedAt = at;
            case DELETE_SUBMIT -> p.deleteArmedAt = at;
            case PAYROLL_RECORD -> p.payrollRecordArmedAt = at;
            default -> {
            }
        }
    }

    static     void clearArmed(final GovBusinessesPanel p, final Click click) {
        GovBusinessesArm.setArmed(p, click, 0L);
    }

    static     String armMessage(final GovBusinessesPanel p, final Click click) {
        return switch (click) {
            case TRANSFER_SUBMIT -> p.transferAction + ". " + p.armConfirmText;
            case SUSPEND_SUBMIT -> p.suspendAction + ". " + p.armConfirmText;
            case CLOSE_SUBMIT -> p.closeAction + ". " + p.armConfirmText;
            case DELETE_SUBMIT -> p.deleteAction + ". " + p.armConfirmText;
            case PAYROLL_RECORD -> p.recordAction + " "
                    + GovBusinessesArm.payrollArmSummary(p) + ". " + p.armConfirmText;
            default -> p.armConfirmText;
        };
    }

    static     String payrollArmSummary(final GovBusinessesPanel p) {
        final String amount = p.payrollRecordArmedAmount.isEmpty()
                ? "-" : p.payrollRecordArmedAmount;
        final String recipient = p.payrollRecordArmedRecipient.isBlank()
                ? GovBusinessesStatus.businessName(p) : p.payrollRecordArmedRecipient;
        return amount + " / " + recipient;
    }

    static     boolean anyArmed(final GovBusinessesPanel p) {
        return GovBusinessesArm.isArmed(p, Click.TRANSFER_SUBMIT) || GovBusinessesArm.isArmed(p, Click.SUSPEND_SUBMIT)
                || GovBusinessesArm.isArmed(p, Click.CLOSE_SUBMIT) || GovBusinessesArm.isArmed(p, Click.DELETE_SUBMIT)
                || GovBusinessesArm.isArmed(p, Click.PAYROLL_RECORD)
                || GovBusinessesArm.isStaffRemoveArmed(p, p.staffRemoveTarget);
    }

    static     void disarmAll(final GovBusinessesPanel p) {
        p.transferArmedAt = 0;
        p.suspendArmedAt = 0;
        p.closeArmedAt = 0;
        p.payrollRecordArmedAt = 0;
        p.payrollRecordArmedAmount = "";
        p.payrollRecordArmedRecipient = "";
        p.payrollRecordArmedLabel = "";
        p.staffRemoveArmedAt = 0;
        p.staffRemoveTarget = "";
    }

}
