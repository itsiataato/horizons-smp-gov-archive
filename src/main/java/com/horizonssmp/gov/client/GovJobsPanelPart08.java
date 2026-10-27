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
abstract class GovJobsPanelPart08 extends GovJobsPanelPart07 {
    GovJobsPanelPart08(final net.minecraft.client.gui.screens.Screen host) {
        super(host);
    }

    void drawMembers(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (this.memberList.w() <= 0 || this.memberList.h() <= 0) {
            return;
        }
        graphics.fill(this.memberList.x(), this.memberList.y(), this.memberList.right(),
                this.memberList.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, this.memberList, GovTheme.BORDER);
        graphics.drawString(this.font, this.memberHeaderText, this.memberHeader.x(),
                this.memberHeader.y() + 1, GovTheme.MUTED, false);
        GovWidgets.rule(graphics, this.memberHeader.x(), this.memberHeader.bottom() - 1,
                this.memberHeader.w(), GovTheme.BORDER_SOFT);

        if (this.selectedJobId.isEmpty() || this.detailState == DetailState.NONE) {
            GovWidgets.emptyState(graphics, this.font, this.memberRows, this.noSelectionText,
                    "");
        } else if (this.detailState == DetailState.LOADING) {
            GovWidgets.emptyState(graphics, this.font, this.memberRows, this.loadingText, "");
        } else if (this.detailState == DetailState.ERROR) {
            GovWidgets.emptyState(graphics, this.font, this.memberRows, this.errorText,
                    this.memberErrorText);
            if (this.memberRetry.w() > 0) {
                GovWidgets.button(graphics, this.font, this.memberRetry, this.retryText,
                        true, this.memberRetry.contains(mouseX, mouseY), false);
            }
        } else if (this.memberViews.isEmpty()) {
            GovWidgets.emptyState(graphics, this.font, this.memberRows, this.noMembersText, "");
        } else {
            final boolean scissored = clipRows(graphics, this.memberRows);
            try {
                final int first = Math.max(0, this.memberScroll / this.memberPitch);
                for (int index = first; index < this.memberViews.size(); index++) {
                    final int y = this.memberRows.y() + index * this.memberPitch
                            - this.memberScroll;
                    if (y >= this.memberRows.bottom()) {
                        break;
                    }
                    if (y < this.memberRows.y()
                            || y + this.memberPitch > this.memberRows.bottom()) {
                        continue;
                    }
                    final MemberView view = this.memberViews.get(index);
                    final boolean hovered = GovTheme.inside(mouseX, mouseY,
                            this.memberList.x() + 2, y,
                            Math.max(0, this.memberList.w() - 4), this.memberPitch);
                    if (hovered) {
                        graphics.fill(this.memberList.x() + 2, y,
                                this.memberList.right() - 2, y + this.memberPitch,
                                GovTheme.HOVER);
                    }
                    graphics.drawString(this.font, view.name(), this.memberList.x() + 8,
                            y + 2, GovTheme.BODY, false);
                    graphics.drawString(this.font, view.meta(), this.memberList.x() + 8,
                            y + 9, GovTheme.FAINT, false);
                    if (this.canAssign && view.removable()
                            && index < this.memberRemoveRects.size()) {
                        final GovRect remove = this.memberRemoveRects.get(index);
                        if (remove.w() > 0) {
                            final boolean armed = isMemberArmed(view.uuid());
                            GovWidgets.button(graphics, this.font, remove,
                                    armed ? this.removeConfirm : this.removeAction,
                                    !mutationPending(), remove.contains(mouseX, mouseY), armed);
                        }
                    }
                }
            } finally {
                if (scissored) {
                    graphics.disableScissor();
                }
            }
        }
        GovWidgets.scrollbar(graphics, this.memberScrollTrack, this.memberScroll,
                this.memberScrollMax);
    }

    void drawCapabilities(final GuiGraphics graphics, final int mouseX,
            final int mouseY) {
        if (this.capList.w() <= 0 || this.capList.h() <= 0) {
            return;
        }
        graphics.fill(this.capList.x(), this.capList.y(), this.capList.right(),
                this.capList.bottom(), GovTheme.FIELD);
        GovTheme.border(graphics, this.capList, GovTheme.BORDER);
        graphics.drawString(this.font, this.capHeaderText, this.capHeader.x(),
                this.capHeader.y() + 1, GovTheme.MUTED, false);
        GovWidgets.rule(graphics, this.capHeader.x(), this.capHeader.bottom() - 1,
                this.capHeader.w(), GovTheme.BORDER_SOFT);

        if (this.selectedJobId.isEmpty() || this.detailState == DetailState.NONE) {
            GovWidgets.emptyState(graphics, this.font, this.capRows, this.noSelectionText, "");
        } else if (this.detailState == DetailState.LOADING) {
            GovWidgets.emptyState(graphics, this.font, this.capRows, this.loadingText, "");
        } else if (this.detailState == DetailState.ERROR) {
            GovWidgets.emptyState(graphics, this.font, this.capRows, this.errorText,
                    this.capErrorText);
            if (this.capRetry.w() > 0) {
                GovWidgets.button(graphics, this.font, this.capRetry, this.retryText,
                        true, this.capRetry.contains(mouseX, mouseY), false);
            }
        } else if (this.capViews.isEmpty()) {
            GovWidgets.emptyState(graphics, this.font, this.capRows, this.noCapsText, "");
        } else {
            final boolean scissored = clipRows(graphics, this.capRows);
            try {
                final int first = Math.max(0, this.capScroll / this.capPitch);
                for (int index = first; index < this.capViews.size(); index++) {
                    final int y = this.capRows.y() + index * this.capPitch - this.capScroll;
                    if (y >= this.capRows.bottom()) {
                        break;
                    }
                    if (y < this.capRows.y()
                            || y + this.capPitch > this.capRows.bottom()) {
                        continue;
                    }
                    final CapView view = this.capViews.get(index);
                    if (view.headingRow()) {
                        graphics.fill(this.capList.x() + 2, y, this.capList.right() - 2,
                                y + this.capPitch, GovTheme.PANEL_ALT);
                        graphics.drawString(this.font, GovText.boldCached(view.line1()),
                                this.capList.x() + 8, y + 5, GovTheme.MUTED, false);
                        continue;
                    }
                    final boolean hovered = GovTheme.inside(mouseX, mouseY,
                            this.capList.x() + 2, y, Math.max(0, this.capList.w() - 4),
                            this.capPitch);
                    if (hovered) {
                        graphics.fill(this.capList.x() + 2, y, this.capList.right() - 2,
                                y + this.capPitch, GovTheme.HOVER);
                    }
                    graphics.drawString(this.font, view.line1(), this.capList.x() + 8,
                            y + 2, view.granted() ? GovTheme.BODY : GovTheme.FAINT, false);
                    graphics.drawString(this.font, view.line2(), this.capList.x() + 8,
                            y + 9, GovTheme.FAINT, false);
                    if (view.editable() && view.action() != null
                            && index < this.capButtonRects.size()) {
                        final GovRect button = this.capButtonRects.get(index);
                        if (button.w() > 0) {
                            final boolean armed = view.granted() && isCapArmed(view.id());
                            GovWidgets.button(graphics, this.font, button,
                                    armed ? this.removeConfirm : view.action(),
                                    !mutationPending(), button.contains(mouseX, mouseY), armed);
                        }
                    }
                }
            } finally {
                if (scissored) {
                    graphics.disableScissor();
                }
            }
        }
        GovWidgets.scrollbar(graphics, this.capScrollTrack, this.capScroll,
                this.capScrollMax);
    }


    void drawBottomBar(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        if (this.bar.w() <= 0) {
            return;
        }
        GovWidgets.rule(graphics, this.bar.x(), this.bar.y(), this.bar.w(),
                GovTheme.BORDER_SOFT);
        switch (this.subtab) {
            case MODE_ROSTER -> {
                GovWidgets.button(graphics, this.font, this.rosterNew, this.newAction,
                        createEnabled(), this.rosterNew.contains(mouseX, mouseY), false);
                final boolean prev = this.jobPage > 0;
                final boolean next = this.jobPage < this.jobPages - 1;
                GovWidgets.button(graphics, this.font, this.rosterPrev, "<",
                        prev, prev && this.rosterPrev.contains(mouseX, mouseY), false);
                GovWidgets.button(graphics, this.font, this.rosterNext, ">",
                        next, next && this.rosterNext.contains(mouseX, mouseY), false);
                graphics.drawString(this.font, this.pageLabel, this.rosterPage.x() + 2,
                        this.rosterPage.y() + 4, GovTheme.MUTED, false);
                final boolean deletable = deleteEnabled();
                final boolean armed = isDeleteArmed();
                GovWidgets.button(graphics, this.font, this.rosterDelete,
                        armed ? this.deleteConfirm : this.deleteAction,
                        deletable, deletable && this.rosterDelete.contains(mouseX, mouseY),
                        armed);
            }
            case MODE_MEMBERS -> {
                final boolean enabled = memberActionsEnabled();
                GovWidgets.button(graphics, this.font, this.memberAdd, this.addMemberAction,
                        enabled, enabled && this.memberAdd.contains(mouseX, mouseY), false);
            }
            default -> {
            }
        }
        GovWidgets.button(graphics, this.font, this.refreshRect, this.refreshAction,
                !mutationPending(), this.refreshRect.contains(mouseX, mouseY), false);
        if (this.modal == Modal.NONE
                && (this.subtab != MODE_ROSTER || this.statusRect.h() > 0)) {
            final GovRect statusArea = this.statusRect.h() > 0
                    ? this.statusRect : statusBar();
            if (statusArea.w() > 0 && !this.statusText.isEmpty()) {
                final int statusY = this.statusRect.h() > 0
                        ? statusArea.y() + 1 : statusArea.y() + 4;
                graphics.drawString(this.font, this.statusText, statusArea.x(), statusY,
                        this.statusIsError ? GovTheme.DANGER : GovTheme.MUTED, false);
            }
        }
    }


}
