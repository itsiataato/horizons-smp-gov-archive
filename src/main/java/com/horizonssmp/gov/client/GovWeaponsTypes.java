package com.horizonssmp.gov.client;

import net.minecraft.util.FormattedCharSequence;

final class GovWeaponsTypes {

    private GovWeaponsTypes() {
    }

    enum RequestKind {
        LIST, DETAIL, CLASSIFY, PERMIT_LIST, AREA_LIST, MUTATE
    }

    enum LoadState {
        NONE, LOADING, READY, ERROR
    }

    enum Modal {
        NONE, RECORD, REGISTER, TRANSFER, REVOKE, LOSS,
        PERMIT_ISSUE, PERMIT_REVOKE, PERMIT_SUSPEND,
        AREA_CREATE, AREA_REVOKE
    }

    enum Click {
        NONE, FILTER, ROW, PREV, NEXT, REFRESH,
        REGISTER_OPEN, RECORD_OPEN, TRANSFER_OPEN, REVOKE_OPEN, LOSS_OPEN,
        PERMIT_LOAD, PERMIT_ISSUE_OPEN, PERMIT_REVOKE_OPEN, PERMIT_SUSPEND_OPEN,
        AREA_CREATE_OPEN, AREA_REVOKE_OPEN,
        CLASSIFY, HELD, CLASS_PICK, SUBMIT, CANCEL, CLOSE_MODAL, RETRY
    }

    record WeaponRow(
            String id, String serial, String classId, String classText, String type, String owner,
            String ownerUuid, String status, String statusText, String authorityText,
            String permit, String permitText, boolean military, long registeredAt,
            String lineOne, String lineTwo, boolean strongStatus, int statusWidth) {
    }

    record PermitRow(
            String id, String holder, String holderUuid, String permitClass, String classLabel,
            String status, String statusText, String issuedBy, long issuedAt, long expiresAt,
            String expiryText, String reason, boolean military, boolean canManage,
            String lineOne, String lineTwo, boolean strongStatus, int statusWidth) {
    }

    record AreaRow(
            String id, String dimension, int x, int y, int z, int radius, String status,
            String statusText, long validFrom, long validUntil, String windowText, String reason,
            String issuedBy, String box, String lineOne, String lineTwo, boolean strongStatus,
            int statusWidth) {
    }

    record Button(
            GovRect rect, Click click, String value, String label, boolean enabled,
            boolean primary, boolean selected) {
    }

    record Hit(GovRect rect, Click click, String value) {
    }

    record DetailLine(
            String label, String value, FormattedCharSequence sequence, int color, boolean rule) {

        static DetailLine row(final String label, final String value, final int color) {
            return new DetailLine(label, value, null, color, false);
        }

        static DetailLine text(final FormattedCharSequence sequence, final int color) {
            return new DetailLine(null, null, sequence, color, false);
        }

        static DetailLine separator() {
            return new DetailLine(null, null, null, GovTheme.BORDER_SOFT, true);
        }
    }
}
