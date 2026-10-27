package com.horizonssmp.gov.client;

import net.minecraft.client.gui.components.EditBox;

import org.lwjgl.glfw.GLFW;

final class GovAuditInput {
    static boolean mouseClicked(final GovAuditPanel p, final double mouseX, final double mouseY, final int button) {
        if (!p.active || button != 0) {
            return false;
        }
        if (p.mode == GovAuditPanel.Mode.AUDIT) {
            if (fieldHit(p, p.actorBox, p.actorRect, mouseX, mouseY)) {
                return focusField(p, p.actorBox);
            }
            if (fieldHit(p, p.actionBox, p.actionRect, mouseX, mouseY)) {
                return focusField(p, p.actionBox);
            }
            if (fieldHit(p, p.targetBox, p.targetRect, mouseX, mouseY)) {
                return focusField(p, p.targetBox);
            }
        } else if (p.canAdmin && fieldHit(p, p.tokenBox, p.tokenRect, mouseX, mouseY)) {
            return focusField(p, p.tokenBox);
        }
        if (!insideVisibleField(p, mouseX, mouseY)) {
            GovAuditWidgets.clearWidgetFocus(p);
        }
        for (final GovAuditPanel.Hit hit : p.hits) {
            if (hit.contains(mouseX, mouseY)) {
                if (hit.action() != GovAuditPanel.Action.SEASON_COMMIT) {
                    disarmCommit(p);
                }
                if (hit.action() != GovAuditPanel.Action.EXPORT_AUDIT
                        && hit.action() != GovAuditPanel.Action.EXPORT_SECTION) {
                    disarmExport(p);
                }
                handle(p, hit);
                return true;
            }
        }
        disarmCommit(p);
        disarmExport(p);
        return p.bounds.contains(mouseX, mouseY);
    }

    static boolean focusField(final GovAuditPanel p, final EditBox box) {
        disarmCommit(p);
        disarmExport(p);
        p.host.setFocused(box);
        return true;
    }

    static void disarmCommit(final GovAuditPanel p) {
        if (!p.commitArmed) {
            return;
        }
        p.commitArmed = false;
        p.status = "";
        p.statusIsError = false;
    }

    static void disarmExport(final GovAuditPanel p) {
        if (p.exportArm == GovAuditPanel.ExportArm.NONE) {
            return;
        }
        p.exportArm = GovAuditPanel.ExportArm.NONE;
        p.status = "";
        p.statusIsError = false;
    }

    static boolean insideVisibleField(final GovAuditPanel p, final double mouseX, final double mouseY) {
        if (p.mode == GovAuditPanel.Mode.AUDIT) {
            return fieldHit(p, p.actorBox, p.actorRect, mouseX, mouseY)
                    || fieldHit(p, p.actionBox, p.actionRect, mouseX, mouseY)
                    || fieldHit(p, p.targetBox, p.targetRect, mouseX, mouseY);
        }
        return p.canAdmin && fieldHit(p, p.tokenBox, p.tokenRect, mouseX, mouseY);
    }

    static boolean fieldHit(final GovAuditPanel p, final EditBox box, final GovRect rect, final double mouseX,
            final double mouseY) {
        return box != null && box.visible && rect.contains(mouseX, mouseY);
    }

    static void handle(final GovAuditPanel p, final GovAuditPanel.Hit hit) {
        switch (hit.action()) {
            case APPLY -> applyFilters(p);
            case OUTCOME -> GovAuditSelection.cycleOutcome(p);
            case WINDOW -> GovAuditSelection.cycleWindow(p);
            case EXPORT_AUDIT -> GovAuditSelection.handleExport(p, GovAuditPanel.ExportArm.AUDIT);
            case ROW -> {
                if (hit.index() >= 0 && hit.index() < p.rowViews.size()) {
                    GovAuditSelection.selectRow(p, p.rowViews.get(hit.index()).seq());
                }
            }
            case PREV -> {
                p.page = Math.max(0, p.page - 1);
                p.listScroll = 0;
                p.commitArmed = false;
                p.status = "";
                p.statusIsError = false;
                GovAuditRequests.requestList(p);
            }
            case NEXT -> {
                p.page = Math.min(p.pages - 1, p.page + 1);
                p.listScroll = 0;
                p.commitArmed = false;
                p.status = "";
                p.statusIsError = false;
                GovAuditRequests.requestList(p);
            }
            case REFRESH -> {
                p.status = "";
                p.statusIsError = false;
                GovAuditRender.refreshOrRetry(p);
            }
            case SEASON_REFRESH -> GovAuditRequests.requestAdmin(p);
            case SEASON_BEGIN -> {
                p.commitArmed = false;
                GovAuditRequests.mutate(p, com.horizonssmp.gov.common.OpCode.SEASON_BEGIN, java.util.Map.of(), GovAuditPanel.RequestKind.BEGIN);
            }
            case SEASON_COMMIT -> GovAuditSelection.handleCommit(p, true);
            case SECTION_PREV -> GovAuditSelection.cycleSection(p, -1);
            case SECTION_NEXT -> GovAuditSelection.cycleSection(p, 1);
            case EXPORT_SECTION -> GovAuditSelection.handleExport(p, GovAuditPanel.ExportArm.SECTION);
            case RETRY_LIST -> GovAuditRequests.requestList(p);
            case RETRY_DETAIL -> GovAuditRequests.requestDetail(p, p.selectedSeq);
            default -> {
            }
        }
    }

    static void applyFilters(final GovAuditPanel p) {
        p.page = 0;
        p.listScroll = 0;
        p.status = "";
        p.statusIsError = false;
        p.commitArmed = false;
        p.exportArm = GovAuditPanel.ExportArm.NONE;
        GovAuditRequests.requestList(p);
    }

    static void submitEnter(final GovAuditPanel p) {
        if (p.mode == GovAuditPanel.Mode.AUDIT) {
            applyFilters(p);
        } else if (p.commitArmed) {
            GovAuditSelection.handleCommit(p, false);
        }
    }

    static boolean mouseScrolled(final GovAuditPanel p, final double mouseX, final double mouseY, final double scrollY) {
        if (!p.active || p.mode != GovAuditPanel.Mode.AUDIT) {
            return false;
        }
        final int step = scrollY < 0 ? 1 : -1;
        if (p.listRect.contains(mouseX, mouseY)) {
            p.listScroll = GovMetrics.clamp(
                    p.listScroll + step * GovMetrics.ROW_H, 0, p.listScrollMax);
            GovAuditCaches.rebuildHits(p);
            return true;
        }
        if (!p.shortDetail && p.detailRect.contains(mouseX, mouseY)
                && p.detailScrollMax > 0) {
            p.detailScroll = GovMetrics.clamp(
                    p.detailScroll + step * GovMetrics.LINE_BODY, 0, p.detailScrollMax);
            return true;
        }
        return false;
    }

    static boolean keyPressed(final GovAuditPanel p, final int keyCode, final int scanCode, final int modifiers) {
        if (!p.active) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (GovAuditWidgets.clearWidgetFocus(p)) {
                return true;
            }
            if (p.commitArmed) {
                disarmCommit(p);
                return true;
            }
            if (p.exportArm != GovAuditPanel.ExportArm.NONE) {
                disarmExport(p);
                return true;
            }
            return false;
        }
        if (GovKeyRepeat.isEnter(keyCode)) {
            if (p.enterRepeat.accepts(System.currentTimeMillis())) {
                submitEnter(p);
            }
            return true;
        }
        if (p.mode == GovAuditPanel.Mode.AUDIT
                && !(p.host.getFocused() instanceof EditBox)
                && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            return moveSelection(p, keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
        }
        return false;
    }

    static boolean moveSelection(final GovAuditPanel p, final int delta) {
        if (p.rowViews.isEmpty()) {
            return false;
        }
        int index = -1;
        for (int i = 0; i < p.rowViews.size(); i++) {
            if (p.rowViews.get(i).seq() == p.selectedSeq) {
                index = i;
                break;
            }
        }
        final int next = GovMetrics.clamp(index + delta, 0, p.rowViews.size() - 1);
        final GovAuditPanel.RowView view = p.rowViews.get(next);
        GovAuditSelection.selectRow(p, view.seq());
        final int top = next * GovMetrics.ROW_H;
        if (top < p.listScroll) {
            p.listScroll = top;
        } else if (top + GovMetrics.ROW_H > p.listScroll + p.listRect.h()) {
            p.listScroll = top + GovMetrics.ROW_H - p.listRect.h();
        }
        p.listScroll = GovMetrics.clamp(p.listScroll, 0, p.listScrollMax);
        p.listScroll = (p.listScroll / GovMetrics.ROW_H) * GovMetrics.ROW_H;
        GovAuditCaches.rebuildHits(p);
        return true;
    }
}