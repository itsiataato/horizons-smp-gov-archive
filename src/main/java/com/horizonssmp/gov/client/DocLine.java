package com.horizonssmp.gov.client;

import net.minecraft.util.FormattedCharSequence;

record DocLine(LineKind kind, FormattedCharSequence text, String id, int height, int color, int keep) {

    static DocLine text(final FormattedCharSequence text, final int height, final int color) {
        return new DocLine(LineKind.TEXT, text, null, Math.max(1, height), color, 0);
    }

    static DocLine mono(final String id, final int color, final int keep) {
        return new DocLine(LineKind.MONO, null, id, 11, color, Math.max(0, keep));
    }

    static DocLine rule() {
        return new DocLine(LineKind.RULE, null, null, 1, GovTheme.BORDER_SOFT, 0);
    }

    static DocLine doubleRule() {
        return new DocLine(LineKind.DOUBLE_RULE, null, null, 3, GovTheme.BORDER, 0);
    }

    static DocLine spacer(final int height) {
        return new DocLine(LineKind.SPACER, null, null, Math.max(0, height), 0, 0);
    }
}
