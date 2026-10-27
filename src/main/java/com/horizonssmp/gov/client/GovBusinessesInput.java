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

final class GovBusinessesInput {

    static     boolean mouseClicked(final GovBusinessesPanel p, final double mouseX, final double mouseY, final int button) {
        if (!p.active || button != 0) {
            return false;
        }
        // A click that misses the focused field leaves it. The shell has already routed the
        // click to the vanilla widget, so this only clears a miss and never disturbs the caret.
        GovBusinessesWidgets.blurFieldOnMiss(p, mouseX, mouseY);
        if (p.modal != Modal.NONE) {
            for (final Hit hit : p.hits) {
                if (hit.rect().contains(mouseX, mouseY)) {
                    GovBusinessesInput.handleHit(p, hit);
                    return true;
                }
            }
            if (System.currentTimeMillis() - p.modalOpenedAt >= MODAL_GUARD_MS
                    && !p.modalRect.contains(mouseX, mouseY)) {
                GovBusinessesInput2.closeModal(p);
            }
            return true;
        }
        for (final Hit hit : p.hits) {
            if (hit.rect().contains(mouseX, mouseY)) {
                GovBusinessesInput.handleHit(p, hit);
                return true;
            }
        }
        return false;
    }

    static     void handleHit(final GovBusinessesPanel p, final Hit hit) {
        switch (hit.click()) {
            case FILTER -> GovBusinessesResponses.setFilter(p, hit.value());
            case ROW -> {
                if (p.subtab == MODE_EMPLOYMENT) {
                    GovBusinessesResponses.selectPosition(p, hit.value());
                } else {
                    GovBusinessesResponses.selectBusiness(p, hit.value());
                }
            }
            case PAGE_PREV -> GovBusinessesResponses.changePage(p, -1);
            case PAGE_NEXT -> GovBusinessesResponses.changePage(p, 1);
            case REFRESH -> {
                GovBusinessesRequests.requestList(p);
                if (!p.selectedId.isEmpty()) {
                    GovBusinessesRequests.requestDetail(p, p.selectedId);
                }
            }
            case RETRY -> {
                p.status = "";
                p.statusIsError = false;
                GovBusinessesArm.disarmAll(p);
                if ("list".equals(hit.value())) {
                    GovBusinessesRequests.requestList(p);
                } else if (!p.selectedId.isEmpty()) {
                    GovBusinessesRequests.requestDetail(p, p.selectedId);
                }
            }
            case CLOSE_MODAL -> GovBusinessesInput2.closeModal(p);
            case FOCUS_FIELD -> GovBusinessesInput.focusField(p, hit.value());
            case REGISTER_TYPE -> GovBusinessesInput2.cycleRegisterType(p);
            case REGISTER_SUBMIT -> GovBusinessesActions.submitRegister(p);
            case BUSINESS_PREV -> GovBusinessesResponses.cycleBusiness(p, -1);
            case BUSINESS_NEXT -> GovBusinessesResponses.cycleBusiness(p, 1);
            case TRANSFER_SUBMIT -> GovBusinessesActions.submitTransfer(p);
            case SUSPEND_OPEN -> GovBusinessesInput2.openSuspendModal(p);
            case CLOSE_SUBMIT -> GovBusinessesActions.submitClose(p);
            case DELETE_SUBMIT -> GovBusinessesActions.submitDelete(p);
            case SUSPEND_SUBMIT -> GovBusinessesActions.submitSuspend(p);
            case SUSPEND_CANCEL -> GovBusinessesInput2.closeModal(p);
            case STAFF_ADD_OPEN -> GovBusinessesInput2.openStaffAddModal(p);
            case STAFF_LINK_OPEN -> GovBusinessesInput2.openLinkModal(p);
            case STAFF_ROLE -> GovBusinessesInput2.cycleEmployeeRole(p);
            case STAFF_ADD_SUBMIT -> GovBusinessesActions.submitStaffAdd(p);
            case STAFF_ADD_CANCEL -> GovBusinessesInput2.closeModal(p);
            case STAFF_REMOVE -> GovBusinessesActions.submitStaffRemove(p, hit.value());
            case LINK_SUBMIT -> GovBusinessesActions.submitLink(p);
            case LINK_UNLINK -> GovBusinessesActions.submitUnlink(p);
            case LINK_CANCEL -> GovBusinessesInput2.closeModal(p);
            case PAYROLL_SET -> GovBusinessesActions.submitPayrollSet(p);
            case PAYROLL_RECORD -> GovBusinessesActions.submitPayrollRecord(p);
            case RENAME_SUBMIT -> GovBusinessesActions.submitRename(p);
            case PAYROLL_RUN_SUBMIT -> GovBusinessesActions2.submitPayrollRun(p);
            case POSITION_CREATE_SUBMIT -> GovBusinessesActions2.submitPositionCreate(p);
            case POSITION_DELETE_SUBMIT -> GovBusinessesActions2.submitPositionDelete(p);
            case POSITION_MOVE_SUBMIT -> GovBusinessesActions2.submitPositionMove(p);
            case POSITION_EDIT_SUBMIT -> GovBusinessesActions2.submitPositionEdit(p);
            case OFFER_SEND_SUBMIT -> GovBusinessesActions2.submitOfferSend(p);
            case OFFER_RESPOND_SUBMIT -> GovBusinessesActions2.submitOfferRespond(p);
            case CONTRACT_END_SUBMIT -> GovBusinessesActions2.submitContractEnd(p);
            case CONTRACT_PROMOTE_SUBMIT -> GovBusinessesActions2.submitContractPromote(p);
            case COMPLAINT_FILE_SUBMIT -> GovBusinessesActions2.submitComplaintFile(p);
            case CLOSE_REQUEST_SUBMIT -> GovBusinessesActions2.submitCloseRequest(p);
            case CLOSE_APPROVE_SUBMIT -> GovBusinessesActions2.submitCloseApprove(p);
            case CLOSE_REJECT_SUBMIT -> GovBusinessesActions2.submitCloseReject(p);
            default -> {
            }
        }
    }

    static     void focusField(final GovBusinessesPanel p, final String kindName) {
        final FieldKind kind;
        try {
            kind = FieldKind.valueOf(kindName);
        } catch (final IllegalArgumentException e) {
            return;
        }
        final EditBox box = GovBusinessesWidgets.boxFor(p, kind);
        if (box != null && box.visible) {
            p.host.setFocused(box);
        }
    }

    static     boolean mouseScrolled(final GovBusinessesPanel p, final double mouseX, final double mouseY, final double scrollY) {
        if (!p.active) {
            return false;
        }
        if (p.modal != Modal.NONE) {
            return true;
        }
        final int step = (int) (scrollY * 18);
        if (p.subtab == MODE_REGISTRY) {
            if (p.registryRows.contains(mouseX, mouseY)) {
                p.registryScroll = GovMetrics.clamp(p.registryScroll - step,
                        0, p.registryScrollMax);
                GovBusinessesHits.rebuildHits(p);
                return true;
            }
            if (p.detailBody.contains(mouseX, mouseY)) {
                p.detailScroll = GovMetrics.clamp(p.detailScroll - step,
                        0, p.detailScrollMax);
                GovBusinessesHits.rebuildHits(p);
                return true;
            }
        }
        if (p.subtab == MODE_STAFF && p.staffRows.contains(mouseX, mouseY)) {
            p.staffScroll = GovMetrics.clamp(p.staffScroll - step,
                    0, p.staffScrollMax);
            GovBusinessesCaches2.rebuildStaffRemoveRects(p);
            GovBusinessesHits.rebuildHits(p);
            return true;
        }
        if (p.subtab == MODE_EMPLOYMENT && p.positionListRows.contains(mouseX, mouseY)) {
            p.positionScroll = GovMetrics.clamp(p.positionScroll - step,
                    0, p.positionScrollMax);
            GovBusinessesHits.rebuildHits(p);
            return true;
        }
        return false;
    }

    static     boolean charTyped(final GovBusinessesPanel p, final char codePoint, final int modifiers) {
        return false;
    }

    static     boolean keyPressed(final GovBusinessesPanel p, final int keyCode, final int scanCode, final int modifiers) {
        if (!p.active) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Escape consumes one layer only: the field, then the modal, then an armed action.
            // With none of those open the panel declines, so the shell closes the dashboard.
            if (GovBusinessesWidgets.blurVisibleField(p)) {
                return true;
            }
            if (p.modal != Modal.NONE) {
                GovBusinessesInput2.closeModal(p);
                return true;
            }
            if (GovBusinessesArm.anyArmed(p)) {
                GovBusinessesArm.disarmAll(p);
                p.status = "";
                p.statusIsError = false;
                GovBusinessesCaches.rebuildCaches(p);
                return true;
            }
            return false;
        }
        if (GovKeyRepeat.isEnter(keyCode)) {
            if (p.host.getFocused() instanceof MultiLineEditBox) {
                return false;
            }
            if (p.enterRepeat.accepts(System.currentTimeMillis())) {
                return GovBusinessesInput2.submitEnter(p);
            }
            // A rate-limited Enter is still swallowed while a modal or an armed action is up, so
            // it cannot leak into the shell or a vanilla widget.
            return p.modal != Modal.NONE || GovBusinessesArm.anyArmed(p);
        }
        if (p.modal != Modal.NONE) {
            return false;
        }
        if (p.host.getFocused() instanceof EditBox) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_UP && p.subtab == MODE_REGISTRY) {
            GovBusinessesResponses.cycleBusiness(p, -1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN && p.subtab == MODE_REGISTRY) {
            GovBusinessesResponses.cycleBusiness(p, 1);
            return true;
        }
        return false;
    }








}
