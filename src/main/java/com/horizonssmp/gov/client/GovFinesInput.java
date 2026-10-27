package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import org.lwjgl.glfw.GLFW;

final class GovFinesInput {

    // --------------------------------------------------------------------- input

    static boolean mouseClicked(final GovFinesPanel p, final double mouseX, final double mouseY, final int button) {
        if (!p.active || button != 0) {
            return false;
        }
        clearFieldFocusOutside(p, mouseX, mouseY);
        for (int i = 0; i < p.buttons.size(); i++) {
            final Button hit = p.buttons.get(i);
            if (hit.enabled() && hit.rect().contains(mouseX, mouseY)) {
                handleClick(p, hit);
                return true;
            }
        }
        for (int i = 0; i < p.rowHits.size(); i++) {
            final Hit hit = p.rowHits.get(i);
            if (hit.contains(mouseX, mouseY)) {
                handleHit(p, hit);
                return true;
            }
        }
        for (int i = 0; i < p.fieldRows.size(); i++) {
            final FieldRow row = p.fieldRows.get(i);
            if (row.tab() == p.subtab && row.box() != null && row.box().visible
                    && row.fieldRect().contains(mouseX, mouseY)) {
                p.host.setFocused(row.box());
                return true;
            }
        }
        return false;
    }

    /** The focused field of the visible tab, or null when focus is elsewhere. */
    static EditBox visibleWidgetFocused(final GovFinesPanel p) {
        final Object focused = p.host.getFocused();
        if (!(focused instanceof EditBox box) || !box.visible) {
            return null;
        }
        for (int i = 0; i < p.fieldRows.size(); i++) {
            final FieldRow row = p.fieldRows.get(i);
            if (row.tab() == p.subtab && row.box() == box) {
                return box;
            }
        }
        return null;
    }

    /** A click anywhere but the focused field itself returns keyboard focus to the panel. */
    static void clearFieldFocusOutside(final GovFinesPanel p, final double mouseX, final double mouseY) {
        final EditBox focused = visibleWidgetFocused(p);
        if (focused == null) {
            return;
        }
        for (int i = 0; i < p.fieldRows.size(); i++) {
            final FieldRow row = p.fieldRows.get(i);
            if (row.box() == focused && row.fieldRect().contains(mouseX, mouseY)) {
                return;
            }
        }
        p.host.setFocused(null);
    }

    static boolean mouseScrolled(final GovFinesPanel p, final double mouseX, final double mouseY, final double scrollY) {
        if (!p.active) {
            return false;
        }
        if (p.listRect.w() > 0 && p.listRect.contains(mouseX, mouseY)
                && p.listScrollMax > 0
                && (p.subtab == TAB_REGISTER || p.detailRect.w() > 0)) {
            p.listScroll = GovMetrics.clamp(p.listScroll - (int) (scrollY * 16),
                    0, p.listScrollMax);
            p.scrollByTab[p.subtab] = p.listScroll;
            GovFinesViewButtons.rebuildHits(p);
            return true;
        }
        if (p.detailRect.w() > 0 && p.detailRect.contains(mouseX, mouseY)
                && p.detailScrollMax > 0) {
            p.detailScroll = GovMetrics.clamp(p.detailScroll - (int) (scrollY * 16),
                    0, p.detailScrollMax);
            return true;
        }
        return false;
    }

    /**
     * Escape blurs the visible field first, then disarms; Enter submits the visible form once per
     * 250 ms. Waive is destructive, so Enter only confirms it after a click has armed it.
     */
    static boolean keyPressed(final GovFinesPanel p, final int keyCode, final int scanCode, final int modifiers) {
        if (!p.active) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (visibleWidgetFocused(p) != null) {
                p.host.setFocused(null);
                return true;
            }
            if (p.armedClick != null) {
                GovFinesSupport.disarm(p);
                return true;
            }
            return false;
        }
        if (GovKeyRepeat.isEnter(keyCode)) {
            if (p.host.getFocused() instanceof MultiLineEditBox) {
                return false;
            }
            if (p.enterRepeat.accepts(System.currentTimeMillis())) {
                GovFinesActions.submitEnter(p);
            }
            return true;
        }
        if (p.host.getFocused() != null) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            moveSelection(p, -1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            moveSelection(p, 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP && p.subtab == TAB_REGISTER) {
            changePage(p, -1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN && p.subtab == TAB_REGISTER) {
            changePage(p, 1);
            return true;
        }
        return false;
    }

    static boolean charTyped(final GovFinesPanel p, final char codePoint, final int modifiers) {
        return false;
    }

    static void moveSelection(final GovFinesPanel p, final int delta) {
        if (p.subtab == TAB_ISSUE || p.rows.isEmpty()) {
            return;
        }
        int index = -1;
        for (int i = 0; i < p.rows.size(); i++) {
            if (p.rows.get(i).id().equals(p.selectedId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            index = 0;
        } else {
            index = GovMetrics.clamp(index + delta, 0, p.rows.size() - 1);
        }
        GovFinesSelection.selectFine(p, p.rows.get(index).id());
    }

    static void changePage(final GovFinesPanel p, final int delta) {
        final int next = GovMetrics.clamp(p.page + delta, 0, p.pages - 1);
        if (next == p.page) {
            return;
        }
        p.page = next;
        p.pageByTab[p.subtab] = next;
        p.listState = LoadState.LOADING;
        GovFinesSupport.disarm(p);
        GovFinesRequests.requestList(p, GovFinesRequests.modeFilter(p), next);
        GovFinesViews.rebuildAll(p);
    }

    static void handleHit(final GovFinesPanel p, final Hit hit) {
        if (hit.click() == Click.ROW) {
            GovFinesSelection.selectFine(p, hit.value());
        }
    }

    static void handleClick(final GovFinesPanel p, final Button button) {
        if (button.click() != Click.WAIVE_SUBMIT) {
            GovFinesSupport.disarm(p);
        }
        switch (button.click()) {
            case FILTER -> GovFinesSelection.setFilter(p, button.value());
            case PAGE -> changePage(p, "-1".equals(button.value()) ? -1 : 1);
            case RETRY_LIST -> {
                GovFinesRequests.requestList(p, GovFinesRequests.modeFilter(p), p.page);
                GovFinesViews.rebuildAll(p);
            }
            case RETRY_DETAIL -> {
                GovFinesRequests.requestDetail(p, p.selectedId);
                GovFinesViews.rebuildAll(p);
            }
            case REFRESH -> {
                GovFinesSupport.disarm(p);
                GovFinesRequests.requestList(p, GovFinesRequests.modeFilter(p), p.page);
                if (!p.selectedId.isEmpty()) {
                    GovFinesRequests.requestDetail(p, p.selectedId);
                }
                GovFinesViews.rebuildAll(p);
            }
            case ONLINE_PREV -> GovFinesSelection.cycleIssueTarget(p, -1);
            case ONLINE_NEXT -> GovFinesSelection.cycleIssueTarget(p, 1);
            case BAND -> {
                p.issueBand = button.value();
                p.status = "";
                GovFinesSupport.syncWidgets(p);
                GovFinesViews.rebuildAll(p);
            }
            case ISSUE_SUBMIT -> GovFinesActions.submitIssue(p);
            case COLLECT -> GovFinesActions.collectSelected(p);
            case PAY -> GovFinesActions.paySelected(p);
            case WAIVE_SUBMIT -> GovFinesActions.submitWaive(p);
            case WAIVE_PREV -> GovFinesSelection.cycleSelected(p, -1);
            case WAIVE_NEXT -> GovFinesSelection.cycleSelected(p, 1);
            default -> {
                // No other click is reachable.
            }
        }
    }

}
