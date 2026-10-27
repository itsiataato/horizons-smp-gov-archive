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


final class GovBusinessesTypes {
    static final GovRect EMPTY = new GovRect(0, 0, 0, 0);

    static final int MODE_REGISTRY = 0;
    static final int MODE_NEW = 1;
    static final int MODE_OWNERSHIP = 2;
    static final int MODE_STAFF = 3;
    static final int MODE_PAYROLL = 4;
    static final int MODE_EMPLOYMENT = 5;

    static final long ARM_FLOOR_MS = 600L;
    /** Every armed action shares the 5 s expiry from the GUI contract, notes included. */
    static final long ARM_MS = 5000L;
    static final int MODAL_GUARD_MS = 250;
    static final int CLOSE_W = 11;
    static final int CLOSE_H = 9;
    static final int RETRY_W = 52;
    static final int RETRY_H = 14;
    static final String CLOSE_LETTER = "X";

    static final String[] FILTER_IDS = {
        BusinessKeys.FILTER_ALL, BusinessKeys.FILTER_ACTIVE,
        BusinessKeys.FILTER_SUSPENDED, BusinessKeys.FILTER_CLOSED
    };
    static final String[] TYPE_IDS = {
        "shop", "bank", "factory", "farm", "mine", "service", "other"
    };
    static final String[] ROLE_IDS = {
        "manager", "cashier", "worker", "guard", "other"
    };

    enum RequestKind {
        LIST, DETAIL, MUTATE, PAYROLL_INFO,
        POSITION_LIST, POSITION_EDIT, OFFER_LIST, CONTRACT_LIST, COMPLAINT_LIST
    }

    enum LoadState {
        NONE, LOADING, READY, ERROR
    }

    enum Modal {
        NONE, SUSPEND, STAFF_ADD, LINK
    }

    enum Click {
        FILTER, ROW, PAGE_PREV, PAGE_NEXT, REFRESH, RETRY, CLOSE_MODAL,
        FOCUS_FIELD,
        REGISTER_TYPE, REGISTER_SUBMIT,
        BUSINESS_PREV, BUSINESS_NEXT,
        TRANSFER_SUBMIT, SUSPEND_OPEN, CLOSE_SUBMIT, DELETE_SUBMIT,
        SUSPEND_SUBMIT, SUSPEND_CANCEL,
        STAFF_ADD_OPEN, STAFF_LINK_OPEN, STAFF_ROLE,
        STAFF_ADD_SUBMIT, STAFF_ADD_CANCEL, STAFF_REMOVE,
        LINK_SUBMIT, LINK_UNLINK, LINK_CANCEL,
        PAYROLL_SET, PAYROLL_RECORD,
        RENAME_SUBMIT, PAYROLL_RUN_SUBMIT,
        POSITION_CREATE_SUBMIT, POSITION_DELETE_SUBMIT, POSITION_MOVE_SUBMIT,
        POSITION_EDIT_SUBMIT,
        OFFER_SEND_SUBMIT, OFFER_RESPOND_SUBMIT,
        CONTRACT_END_SUBMIT, CONTRACT_PROMOTE_SUBMIT,
        COMPLAINT_FILE_SUBMIT,
        CLOSE_REQUEST_SUBMIT, CLOSE_APPROVE_SUBMIT, CLOSE_REJECT_SUBMIT
    }

    enum FieldKind {
        NAME, OWNER, TYPE, TRANSFER_OWNER, REASON, STAFF_PLAYER, LINK_JOB,
        RATE, SCHEDULE, AMOUNT, PAYMENT_NOTE,
        BUSINESS, CURRENT_OWNER, STATUS, ROLE
    }

    record BizRow(
            String id, String name, String owner, String ownerUuid, String type, String status,
            int employees, String job, String jobName) {
    }

    record Hit(GovRect rect, Click click, String value) {
    }

    record RowView(String id, String name, String meta, String status, int statusWidth) {
    }

    record StaffView(String uuid, String line1, String line2, boolean removable) {
    }

    record DetailLine(
            String label, String value, List<FormattedCharSequence> wrapped, int height,
            boolean heading, Component bold) {
    }

    record FieldSpec(FieldKind kind, String label, Click click, boolean readonly) {
    }

    record ActionSpec(
            String label, String armedLabel, Click click, boolean primary) {
    }

    record FormRow(
            FieldKind kind, String label, GovRect labelRect, GovRect controlRect, Click click,
            boolean readonly) {
    }

    record FormAction(
            String label, String armedLabel, Click click, GovRect rect, boolean primary) {
    }

    static final class FormGeometry {
        public final GovRect area;
        public final List<FormRow> rows;
        public final List<FormAction> actions;
        public final GovRect noteRect;

        FormGeometry(final GovRect area, final List<FormRow> rows,
                final List<FormAction> actions, final GovRect noteRect) {
            this.area = area;
            this.rows = rows;
            this.actions = actions;
            this.noteRect = noteRect;
        }

        GovRect area() {
            return this.area;
        }

        List<FormRow> rows() {
            return this.rows;
        }

        List<FormAction> actions() {
            return this.actions;
        }

        GovRect noteRect() {
            return this.noteRect;
        }
    }

}
