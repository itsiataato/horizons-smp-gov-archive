package com.horizonssmp.gov.client;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.horizonssmp.gov.client.GovRect;

final class GovJobsTypes {

    private GovJobsTypes() {
    }

    enum RequestKind {
        LIST, INFO, MUTATE
    }

    enum ListState {
        LOADING, READY, ERROR
    }

    enum DetailState {
        NONE, LOADING, READY, ERROR
    }

    enum Modal {
        NONE, CREATE, PICKER
    }

    enum Click {
        ROW, PAGE_PREV, PAGE_NEXT, REFRESH, RETRY,
        CREATE_OPEN, CREATE_SUBMIT, CREATE_CANCEL, FOCUS_CREATE,
        DELETE,
        MEMBER_ADD_OPEN, MEMBER_REMOVE,
        PICKER_PICK, PICKER_CANCEL,
        CAP_REMOVE, CAP_GRANT,
        CLOSE_MODAL
    }

    record JobRow(String id, String name, String type, int members, int caps) {
    }

    record MemberRow(String name, String uuid, boolean online) {
    }

    record Hit(GovRect rect, Click click, String value) {
    }

    record RosterView(String id, String name, String meta, String type, int typeWidth) {
    }

    record MemberView(String uuid, String name, String meta, boolean removable) {
    }

    record CapView(
            String id, String line1, String line2, String action, boolean editable,
            boolean granted, boolean headingRow) {
    }

    record DetailLine(
            String label, String value, List<FormattedCharSequence> wrapped, int height,
            boolean heading, Component bold) {
    }
}
