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

final class GovBusinessesHits {

    static     void rebuildHits(final GovBusinessesPanel p) {
        p.hits.clear();
        if (p.modal != Modal.NONE) {
            if (p.modalClose.w() > 0 && p.modalClose.h() > 0) {
                p.hits.add(new Hit(p.modalClose, Click.CLOSE_MODAL, ""));
            }
            GovBusinessesHits.addFormHits(p, GovBusinessesHits.modalForm(p));
            return;
        }
        switch (p.subtab) {
            case MODE_REGISTRY -> {
                for (int index = 0; index < p.filterRects.length; index++) {
                    p.hits.add(new Hit(p.filterRects[index], Click.FILTER,
                            FILTER_IDS[index]));
                }
                GovBusinessesHits.addRegistryRowHits(p);
                p.hits.add(new Hit(p.registryPrev, Click.PAGE_PREV, ""));
                p.hits.add(new Hit(p.registryNext, Click.PAGE_NEXT, ""));
                if (p.listState == LoadState.ERROR && p.listRetry.w() > 0) {
                    p.hits.add(new Hit(p.listRetry, Click.RETRY, "list"));
                }
                if (p.detailState == LoadState.ERROR && p.detailRetry.w() > 0
                        && !p.selectedId.isEmpty()) {
                    p.hits.add(new Hit(p.detailRetry, Click.RETRY, "detail"));
                }
                GovBusinessesHits.addRefreshHit(p);
            }
            case MODE_NEW -> {
                GovBusinessesHits.addFormHits(p, p.newForm);
                GovBusinessesHits.addRefreshHit(p);
            }
            case MODE_OWNERSHIP -> {
                GovBusinessesHits.addFormHits(p, p.ownershipForm);
                GovBusinessesHits.addRefreshHit(p);
            }
            case MODE_STAFF -> {
                GovBusinessesHits.addStaffHits(p);
                if (p.detailState == LoadState.ERROR && p.staffRetry.w() > 0
                        && !p.selectedId.isEmpty()) {
                    p.hits.add(new Hit(p.staffRetry, Click.RETRY, "detail"));
                }
                if (GovBusinessesStatus.staffActionsEnabled(p)) {
                    p.hits.add(new Hit(p.staffAdd, Click.STAFF_ADD_OPEN, ""));
                    p.hits.add(new Hit(p.staffLink, Click.STAFF_LINK_OPEN, ""));
                }
                GovBusinessesHits.addRefreshHit(p);
            }
            case MODE_PAYROLL -> {
                GovBusinessesHits.addFormHits(p, p.payrollForm);
                GovBusinessesHits.addRefreshHit(p);
            }
            case MODE_EMPLOYMENT -> {
                GovBusinessesHits.addPositionRowHits(p);
                if (p.positionEditBtn.w() > 0 && p.positionEditBtn.h() > 0) {
                    p.hits.add(new Hit(p.positionEditBtn, Click.POSITION_EDIT_SUBMIT, ""));
                }
                GovBusinessesHits.addRefreshHit(p);
            }
            default -> {
            }
        }
    }

    static     void addRefreshHit(final GovBusinessesPanel p) {
        if (!GovBusinessesRequests.mutationPending(p)) {
            p.hits.add(new Hit(p.refreshRect, Click.REFRESH, ""));
        }
    }

    static     void addFormHits(final GovBusinessesPanel p, final FormGeometry form) {
        if (form == null) {
            return;
        }
        for (final FormRow row : form.rows()) {
            if (row.kind() == FieldKind.BUSINESS) {
                p.hits.add(new Hit(p.businessPrevRect, Click.BUSINESS_PREV, ""));
                p.hits.add(new Hit(p.businessNextRect, Click.BUSINESS_NEXT, ""));
                continue;
            }
            if (row.readonly()) {
                continue;
            }
            if (row.click() != null) {
                p.hits.add(new Hit(row.controlRect(), row.click(), ""));
            } else {
                p.hits.add(new Hit(row.controlRect(), Click.FOCUS_FIELD, row.kind().name()));
            }
        }
        for (final FormAction action : form.actions) {
            p.hits.add(new Hit(action.rect(), action.click(), ""));
        }
    }

    static     void addRegistryRowHits(final GovBusinessesPanel p) {
        if (!p.emptyTitle.isEmpty()) {
            return;
        }
        for (int index = 0; index < p.rowViews.size(); index++) {
            final int y = p.registryRows.y() + index * p.registryPitch - p.registryScroll;
            final GovRect row = new GovRect(p.registryList.x() + 2, y,
                    Math.max(0, p.registryList.w() - 4), p.registryPitch);
            if (!GovBusinessesGeometry.fullyVisible(row, p.registryRows)) {
                continue;
            }
            p.hits.add(new Hit(row, Click.ROW, p.rowViews.get(index).id()));
        }
    }

    static     void addStaffHits(final GovBusinessesPanel p) {
        if (p.selectedId.isEmpty() || p.detailState != LoadState.READY) {
            return;
        }
        for (int index = 0; index < p.staffViews.size(); index++) {
            final StaffView view = p.staffViews.get(index);
            if (!GovBusinessesStatus.canEdit(p) || !view.removable() || index >= p.staffRemoveRects.size()) {
                continue;
            }
            final GovRect remove = p.staffRemoveRects.get(index);
            if (remove.w() <= 0 || remove.h() <= 0) {
                continue;
            }
            p.hits.add(new Hit(remove, Click.STAFF_REMOVE, view.uuid()));
        }
    }

    static     void addPositionRowHits(final GovBusinessesPanel p) {
        if (p.selectedId.isEmpty() || p.positionState != LoadState.READY) {
            return;
        }
        for (int index = 0; index < p.positionRows.size(); index++) {
            final Map<String, String> row = p.positionRows.get(index);
            final String id = row.get(BusinessKeys.POSITION_ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            final int y = p.positionListRows.y() + index * p.positionPitch - p.positionScroll;
            final GovRect rowRect = new GovRect(p.positionList.x() + 2, y,
                    Math.max(0, p.positionList.w() - 4), p.positionPitch);
            if (!GovBusinessesGeometry.fullyVisible(rowRect, p.positionListRows)) {
                continue;
            }
            p.hits.add(new Hit(rowRect, Click.ROW, id));
        }
    }

    static     FormGeometry modalForm(final GovBusinessesPanel p) {
        return switch (p.modal) {
            case SUSPEND -> p.suspendForm;
            case STAFF_ADD -> p.staffAddForm;
            case LINK -> p.linkForm;
            default -> null;
        };
    }

        static GovRect controlFor(final FormGeometry form, final FieldKind kind) {
        if (form == null) {
            return EMPTY;
        }
        for (final FormRow row : form.rows()) {
            if (row.kind() == kind) {
                return row.controlRect();
            }
        }
        return EMPTY;
    }

        static GovRect actionRect(final FormGeometry form, final Click click) {
        if (form == null) {
            return EMPTY;
        }
        for (final FormAction action : form.actions()) {
            if (action.click() == click) {
                return action.rect();
            }
        }
        return EMPTY;
    }

}
