package com.horizonssmp.gov.client;

import net.minecraft.util.FormattedCharSequence;

final class GovJailTypes {

    private GovJailTypes() {
    }

    enum RequestKind {
        LIST, DETAIL, MUTATE
    }

    enum LoadState {
        NONE, LOADING, READY, ERROR
    }

    enum Click {
        NONE, FILTER, ROW, PREV, NEXT, REFRESH, RETRY_LIST, RETRY_DETAIL,
        BASIS, CONSENT, DETAIN, RELEASE, EXTEND, CUFF, UNCUFF, CELL, CELL_CONFIRM,
        CELL_CANCEL, CELL_HERE, CELL_CREATE, CELL_DELETE, CELL_DELETE_ROW
    }

    record JailRow(
            String id, String target, String targetUuid, String basis, String basisText,
            String officer, long started, long remainingMs, String remainingText, boolean cuffed,
            String status, String statusText, String warrant, boolean active, String lineOne,
            String lineTwo, boolean strongStatus, int statusWidth, String summary) {
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
