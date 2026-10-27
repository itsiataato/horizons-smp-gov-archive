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

final class GovBusinessesWidgets {

    static     List<EditBox> createWidgets(final GovBusinessesPanel p, final Font font) {
        p.font = font;
        if (!p.widgetList.isEmpty()) {
            return p.widgetList;
        }
        p.nameBox = GovBusinessesWidgets.box(p, font, 64, "gui.gov.businesses.name_hint");
        p.ownerBox = GovBusinessesWidgets.box(p, font, 32, "gui.gov.businesses.staff_player_hint");
        p.transferBox = GovBusinessesWidgets.box(p, font, 32, "gui.gov.businesses.staff_player_hint");
        p.suspendReasonBox = GovBusinessesWidgets.box(p, font, 200, "gui.gov.businesses.reason_hint");
        p.staffPlayerBox = GovBusinessesWidgets.box(p, font, 32, "gui.gov.businesses.staff_player_hint");
        p.linkJobBox = GovBusinessesWidgets.box(p, font, 48, "gui.gov.businesses.link_job_hint");
        p.rateBox = GovBusinessesWidgets.box(p, font, 12, "gui.gov.businesses.rate_hint");
        p.scheduleBox = GovBusinessesWidgets.box(p, font, 120, "gui.gov.businesses.schedule_hint");
        p.amountBox = GovBusinessesWidgets.box(p, font, 12, "gui.gov.businesses.amount_hint");
        GovBusinessesWidgets.digitsOnly(p.rateBox);
        GovBusinessesWidgets.amountOnly(p, p.amountBox);
        p.paymentNoteBox = GovBusinessesWidgets.box(p, font, 160, "gui.gov.businesses.payment_hint");
        p.paymentNoteBox.setResponder(value -> {
            if (p.payrollRecordArmedAt > 0L) {
                GovBusinessesArm.disarm(p, Click.PAYROLL_RECORD);
            }
        });
        p.positionTitleBox = GovBusinessesWidgets.box(p, font, 64, "gui.gov.businesses.position_title_hint");
        p.positionWageBox = GovBusinessesWidgets.box(p, font, 12, "gui.gov.businesses.position_wage_hint");
        GovBusinessesWidgets.digitsOnly(p.positionWageBox);
        p.widgetList.add(p.nameBox);
        p.widgetList.add(p.ownerBox);
        p.widgetList.add(p.transferBox);
        p.widgetList.add(p.suspendReasonBox);
        p.widgetList.add(p.staffPlayerBox);
        p.widgetList.add(p.linkJobBox);
        p.widgetList.add(p.rateBox);
        p.widgetList.add(p.scheduleBox);
        p.widgetList.add(p.amountBox);
        p.widgetList.add(p.paymentNoteBox);
        p.widgetList.add(p.positionTitleBox);
        p.widgetList.add(p.positionWageBox);
        return p.widgetList;
    }

        static void digitsOnly(final EditBox box) {
        if (box == null) {
            return;
        }
        box.setResponder(value -> {
            final String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
            if (!digits.equals(value)) {
                box.setValue(digits);
            }
        });
    }

    static     void amountOnly(final GovBusinessesPanel p, final EditBox box) {
        if (box == null) {
            return;
        }
        box.setResponder(value -> {
            final String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
            if (!digits.equals(value)) {
                box.setValue(digits);
            }
            if (p.payrollRecordArmedAt > 0L) {
                GovBusinessesArm.disarm(p, Click.PAYROLL_RECORD);
            }
        });
    }

    static     EditBox box(final GovBusinessesPanel p, final Font font, final int maxLength, final String hintKey) {
        final EditBox box = GovWidgets.field(font, 0, 0, 10, 9,
                Component.translatable(hintKey));
        box.setMaxLength(maxLength);
        box.setVisible(false);
        return box;
    }

    static     EditBox boxFor(final GovBusinessesPanel p, final FieldKind kind) {
        return switch (kind) {
            case NAME -> p.nameBox;
            case OWNER -> p.ownerBox;
            case TRANSFER_OWNER -> p.transferBox;
            case REASON -> p.suspendReasonBox;
            case STAFF_PLAYER -> p.staffPlayerBox;
            case LINK_JOB -> p.linkJobBox;
            case RATE -> p.rateBox;
            case SCHEDULE -> p.scheduleBox;
            case AMOUNT -> p.amountBox;
            case PAYMENT_NOTE -> p.paymentNoteBox;
            default -> null;
        };
    }

    static     void syncWidgets(final GovBusinessesPanel p) {
        final boolean on = p.active && p.modal == Modal.NONE;
        GovBusinessesWidgets.setVisible(p, p.nameBox, on && p.subtab == MODE_NEW);
        GovBusinessesWidgets.setVisible(p, p.ownerBox, on && p.subtab == MODE_NEW);
        GovBusinessesWidgets.setVisible(p, p.transferBox, on && p.subtab == MODE_OWNERSHIP);
        GovBusinessesWidgets.setVisible(p, p.rateBox, on && p.subtab == MODE_PAYROLL);
        GovBusinessesWidgets.setVisible(p, p.scheduleBox, on && p.subtab == MODE_PAYROLL);
        GovBusinessesWidgets.setVisible(p, p.amountBox, on && p.subtab == MODE_PAYROLL);
        GovBusinessesWidgets.setVisible(p, p.paymentNoteBox, on && p.subtab == MODE_PAYROLL);
        GovBusinessesWidgets.setVisible(p, p.transferBox, on && (p.subtab == MODE_OWNERSHIP || p.subtab == MODE_EMPLOYMENT));
        GovBusinessesWidgets.setVisible(p, p.rateBox, on && (p.subtab == MODE_PAYROLL || p.subtab == MODE_EMPLOYMENT));
        GovBusinessesWidgets.setVisible(p, p.scheduleBox, on && (p.subtab == MODE_PAYROLL || p.subtab == MODE_EMPLOYMENT));
        GovBusinessesWidgets.setVisible(p, p.amountBox, on && (p.subtab == MODE_PAYROLL || p.subtab == MODE_EMPLOYMENT));
        GovBusinessesWidgets.setVisible(p, p.positionTitleBox, on && p.subtab == MODE_EMPLOYMENT);
        GovBusinessesWidgets.setVisible(p, p.positionWageBox, on && p.subtab == MODE_EMPLOYMENT);
        GovBusinessesWidgets.setVisible(p, p.suspendReasonBox, p.active && p.modal == Modal.SUSPEND);
        GovBusinessesWidgets.setVisible(p, p.staffPlayerBox, p.active && p.modal == Modal.STAFF_ADD);
        GovBusinessesWidgets.setVisible(p, p.linkJobBox, p.active && p.modal == Modal.LINK);
    }

    static     void setVisible(final GovBusinessesPanel p, final EditBox box, final boolean visible) {
        if (box == null) {
            return;
        }
        box.setVisible(visible);
        if (!visible && p.host.getFocused() == box) {
            p.host.setFocused(null);
        }
    }

    static     void clearFocus(final GovBusinessesPanel p) {
        for (final EditBox box : p.widgetList) {
            if (p.host.getFocused() == box) {
                p.host.setFocused(null);
                return;
            }
        }
    }

    static     boolean blurVisibleField(final GovBusinessesPanel p) {
        if (p.host.getFocused() instanceof EditBox box
                && p.widgetList.contains(box) && box.isVisible()) {
            p.host.setFocused(null);
            return true;
        }
        return false;
    }

    static     void blurFieldOnMiss(final GovBusinessesPanel p, final double mouseX, final double mouseY) {
        if (p.host.getFocused() instanceof EditBox box
                && p.widgetList.contains(box) && box.isVisible()
                && !box.isMouseOver(mouseX, mouseY)) {
            p.host.setFocused(null);
        }
    }

    static     void clearFormFields(final GovBusinessesPanel p) {
        for (final EditBox box : p.widgetList) {
            if (box != null) {
                box.setValue("");
            }
        }
    }

}
