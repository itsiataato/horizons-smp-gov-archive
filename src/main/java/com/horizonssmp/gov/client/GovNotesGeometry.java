package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

class GovNotesGeometry extends GovNotesWidgets {
    GovNotesGeometry(final GovDashboardScreen host) {
        super(host);
    }

    protected void layout(final int left, final int top, final int right, final int bottom,
            final Font font) {
        this.font = font;
        final int l = Math.min(left, right);
        final int t = Math.min(top, bottom);
        final int r = Math.max(left, right);
        final int b = Math.max(top, bottom);
        this.bounds = new GovRect(l, t, Math.max(0, r - l), Math.max(0, b - t));

        final int width = this.bounds.w();
        final int pad = width >= 480 ? GovMetrics.GAP_XS + 2 : width >= 340 ? 4 : 3;
        this.inner = this.bounds.inset(pad).clamp(this.bounds);

        final int innerH = this.inner.h();
        int headerH = GovMetrics.clamp(innerH / 12, 9, 14);
        headerH = Math.min(headerH, innerH);
        this.headerRect = new GovRect(this.inner.x(), this.inner.y(), this.inner.w(), headerH)
                .clamp(this.bounds);

        int footerH = GovMetrics.clamp(innerH / 10, 10, 16);
        footerH = Math.min(footerH, Math.max(0, innerH - headerH));
        this.footerRect = new GovRect(this.inner.x(), this.inner.bottom() - footerH,
                this.inner.w(), footerH).clamp(this.bounds);

        final int gap = innerH >= 120 ? 4 : 1;
        final int bodyTop = Math.min(this.inner.bottom(), this.headerRect.bottom() + gap);
        final int bodyBottom = Math.max(bodyTop, this.footerRect.y() - gap);
        this.bodyRect = new GovRect(this.inner.x(), bodyTop, this.inner.w(),
                Math.max(0, bodyBottom - bodyTop)).clamp(this.bounds);

        this.wide = this.bounds.w() >= 340;
        if (this.wide) {
            int scopeW = GovMetrics.clamp(this.inner.w() * 22 / 100, 110, 170);
            scopeW = Math.min(scopeW, Math.max(0, this.inner.w() - 80));
            scopeW = Math.min(scopeW, this.bodyRect.w());
            this.scopeRect = new GovRect(this.bodyRect.x(), this.bodyRect.y(), scopeW,
                    this.bodyRect.h()).clamp(this.bodyRect);
            final int mainX = this.scopeRect.right()
                    + (this.bodyRect.w() > scopeW ? 4 : 0);
            this.mainRect = new GovRect(mainX, this.bodyRect.y(),
                    Math.max(0, this.bodyRect.right() - mainX), this.bodyRect.h())
                    .clamp(this.bodyRect);
            this.scopeBarRect = EMPTY;
            this.scopeToggleRect = EMPTY;
        } else {
            final int barH = Math.min(10, this.bodyRect.h());
            this.scopeBarRect = new GovRect(this.bodyRect.x(), this.bodyRect.y(),
                    this.bodyRect.w(), barH).clamp(this.bodyRect);
            final int mainTop = Math.min(this.bodyRect.bottom(),
                    this.scopeBarRect.bottom() + (this.bodyRect.h() > barH ? 2 : 0));
            this.mainRect = new GovRect(this.bodyRect.x(), mainTop, this.bodyRect.w(),
                    Math.max(0, this.bodyRect.bottom() - mainTop)).clamp(this.bodyRect);
            this.scopeRect = EMPTY;
            final int toggleW = Math.min(48, this.scopeBarRect.w());
            this.scopeToggleRect = new GovRect(this.scopeBarRect.right() - toggleW,
                    this.scopeBarRect.y(), toggleW, this.scopeBarRect.h())
                    .clamp(this.scopeBarRect);
        }

        this.layoutModeBands();
        layoutFooter();
        layoutComposer();
        syncWidgets();
    }

    protected void layoutModeBands() {
        int contentTop = this.mainRect.y();
        if (this.mode == 2) {
            int switchH = GovMetrics.clamp(this.mainRect.h() / 10, 10, 16);
            switchH = Math.min(switchH, this.mainRect.h());
            this.bizSwitchRect = new GovRect(this.mainRect.x(), this.mainRect.y(),
                    this.mainRect.w(), switchH).clamp(this.mainRect);
            final int half = Math.max(0, (this.mainRect.w() - 2) / 2);
            this.boardRect = new GovRect(this.mainRect.x(), this.mainRect.y(), half, switchH)
                    .clamp(this.bizSwitchRect);
            this.personalRect = new GovRect(this.boardRect.right() + 2, this.mainRect.y(),
                    Math.max(0, this.mainRect.w() - half - 2), switchH)
                    .clamp(this.bizSwitchRect);
            contentTop = Math.min(this.mainRect.bottom(),
                    this.bizSwitchRect.bottom() + (this.mainRect.h() > switchH ? 2 : 0));
        } else {
            this.bizSwitchRect = EMPTY;
            this.boardRect = EMPTY;
            this.personalRect = EMPTY;
        }

        int composerH = GovMetrics.clamp(this.mainRect.h() / 9, 13, 18);
        composerH = Math.min(composerH, Math.max(0, this.mainRect.bottom() - contentTop));
        this.composerRect = new GovRect(this.mainRect.x(),
                this.mainRect.bottom() - composerH, this.mainRect.w(), composerH)
                .clamp(this.mainRect);
        final int listBottom = Math.max(contentTop, this.composerRect.y() - 2);
        this.listRect = new GovRect(this.mainRect.x(), contentTop, this.mainRect.w(),
                Math.max(0, listBottom - contentTop)).clamp(this.mainRect);

        final int retryH = Math.min(14, this.listRect.h());
        final int retryW = Math.min(52, this.listRect.w());
        this.retryRect = new GovRect(this.listRect.x()
                + Math.max(0, (this.listRect.w() - retryW) / 2),
                this.listRect.bottom() - retryH, retryW, retryH).clamp(this.listRect);
    }

    protected void layoutFooter() {
        if (this.font == null || this.footerRect.w() <= 0 || this.footerRect.h() <= 0) {
            this.prevRect = EMPTY;
            this.nextRect = EMPTY;
            this.refreshRect = EMPTY;
            this.pageW = 0;
            this.statusW = 0;
            return;
        }
        final int fh = Math.min(12, this.footerRect.h());
        final int fy = this.footerRect.y() + Math.max(0, (this.footerRect.h() - fh) / 2);
        this.footerTextY = this.footerRect.y() + Math.max(0, (this.footerRect.h() - 9) / 2);
        final int gap = 3;
        final int refreshLabelW = Math.max(this.font.width(this.refreshText),
                this.font.width(this.loadText));
        int refreshW = Math.min(this.footerRect.w(),
                GovMetrics.clamp(refreshLabelW + 8, 38, 70));
        int nextW = Math.min(this.footerRect.w(),
                GovMetrics.clamp(this.font.width(this.nextText) + 8, 28, 50));
        int pageWanted = Math.min(this.footerRect.w(),
                GovMetrics.clamp(this.font.width("PAGE 88 OF 88") + 6, 40, 78));
        int prevW = Math.min(this.footerRect.w(),
                GovMetrics.clamp(this.font.width(this.previousText) + 8, 30, 56));

        int total = prevW + nextW + pageWanted + refreshW + gap * 3;
        if (total > this.footerRect.w()) {
            int overflow = total - this.footerRect.w();
            int cut = Math.min(overflow, Math.max(0, refreshW - 24));
            refreshW -= cut;
            overflow -= cut;
            cut = Math.min(overflow, Math.max(0, pageWanted - 28));
            pageWanted -= cut;
            overflow -= cut;
            cut = Math.min(overflow, Math.max(0, prevW - 22));
            prevW -= cut;
            overflow -= cut;
            cut = Math.min(overflow, Math.max(0, nextW - 22));
            nextW -= cut;
        }

        int x = this.footerRect.right();
        this.refreshRect = new GovRect(x - refreshW, fy, refreshW, fh)
                .clamp(this.footerRect);
        x = this.refreshRect.x() - gap;
        this.nextRect = new GovRect(x - nextW, fy, nextW, fh).clamp(this.footerRect);
        x = this.nextRect.x() - gap;
        final int pageXWanted = Math.max(0, x - this.footerRect.x());
        this.pageX = Math.max(this.footerRect.x(), x - pageWanted);
        this.pageW = Math.max(0, Math.min(pageWanted, pageXWanted));
        x = this.pageX - gap;
        this.prevRect = new GovRect(x - prevW, fy, prevW, fh).clamp(this.footerRect);
        this.statusX = this.footerRect.x();
        this.statusW = Math.max(0, this.prevRect.x() - this.footerRect.x() - 4);
    }

    protected void layoutComposer() {
        if (this.font == null || this.composerRect.w() <= 0 || this.composerRect.h() <= 0) {
            this.postRect = EMPTY;
            this.cancelRect = EMPTY;
            this.targetFieldRect = EMPTY;
            this.textFieldRect = EMPTY;
            this.editTargetW = 0;
            layoutFields();
            return;
        }
        final int fh = Math.min(16, this.composerRect.h());
        final int fy = this.composerRect.y() + Math.max(0, (this.composerRect.h() - fh) / 2);
        final int gap = 3;
        final boolean editing = !this.editingNoteId.isEmpty();
        final int postW = Math.min(this.composerRect.w(),
                GovMetrics.clamp(this.font.width(this.postText) + 10, 34, 64));
        final int cancelW = Math.min(this.composerRect.w(),
                GovMetrics.clamp(this.font.width(this.cancelText) + 10, 36, 64));
        final int right = this.composerRect.right();
        this.postRect = new GovRect(right - postW, fy, postW, fh).clamp(this.composerRect);
        this.cancelRect = editing
                ? new GovRect(this.postRect.x() - gap - cancelW, fy, cancelW, fh)
                        .clamp(this.composerRect)
                : EMPTY;

        final int textRight = editing ? this.cancelRect.x() - gap : this.postRect.x() - gap;
        if (editing) {
            this.editTargetW = GovMetrics.span(this.composerRect.w(),
                    this.composerRect.w() / 5, 36, 80);
            this.targetFieldRect = EMPTY;
            final int textLeft = this.composerRect.x() + this.editTargetW + gap;
            final int textW = Math.max(0, textRight - textLeft);
            this.textFieldRect = new GovRect(textLeft, fy, textW, fh)
                    .clamp(this.composerRect);
            layoutFields();
            return;
        }

        this.editTargetW = 0;
        final boolean target = targetRelevant();
        int targetW = target
                ? Math.min(this.composerRect.w(),
                        GovMetrics.clamp(this.composerRect.w() * 26 / 100, 54, 150))
                : 0;
        this.targetFieldRect = target
                ? new GovRect(this.composerRect.x(), fy, targetW, fh).clamp(this.composerRect)
                : EMPTY;
        final int textLeft = this.composerRect.x() + (target ? targetW + gap : 0);
        final int textW = Math.max(0, textRight - textLeft);
        this.textFieldRect = new GovRect(textLeft, fy, textW, fh).clamp(this.composerRect);
        layoutFields();
    }

    protected void layoutFields() {
        if (this.targetBox != null && this.targetFieldRect.w() > 0
                && this.targetFieldRect.h() > 0) {
            final int offset = this.targetFieldRect.h() >= 16 ? 4 : 2;
            this.targetBox.setX(this.targetFieldRect.x() + 4);
            this.targetBox.setY(this.targetFieldRect.y() + offset);
            this.targetBox.setWidth(Math.max(1, this.targetFieldRect.w() - 8));
            this.targetBox.setHeight(9);
        }
        if (this.textBox != null && this.textFieldRect.w() > 0
                && this.textFieldRect.h() > 0) {
            final int offset = this.textFieldRect.h() >= 16 ? 4 : 2;
            this.textBox.setX(this.textFieldRect.x() + 4);
            this.textBox.setY(this.textFieldRect.y() + offset);
            this.textBox.setWidth(Math.max(1, this.textFieldRect.w() - 8));
            this.textBox.setHeight(9);
        }
    }
}
