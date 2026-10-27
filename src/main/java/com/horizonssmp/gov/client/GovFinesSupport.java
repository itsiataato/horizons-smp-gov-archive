package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;

final class GovFinesSupport {

    /** Shows or hides the panel, its fields and its armed confirmation. */
    static void setActive(final GovFinesPanel p, final boolean active) {
        p.active = active;
        if (!active) {
            disarm(p);
            p.enterRepeat.reset();
            unfocusAll(p);
        } else {
            GovFinesRequests.requestListIfNeeded(p);
        }
        syncWidgets(p);
        GovFinesViewButtons.rebuildButtons(p);
        GovFinesViewButtons.rebuildHits(p);
    }

    /** Forgets every request in flight; called when the screen closes. */
    static void reset(final GovFinesPanel p) {
        p.pending.clear();
        p.latestListReqId = "";
        p.latestDetailReqId = "";
        setStatus(p, "", false);
        disarm(p);
        unfocusAll(p);
        syncWidgets(p);
    }

    /**
     * Selects the shell's sub-category: 0 Register, 1 Issue, 2 Collect, 3 Waive.
     *
     * <p>The shell owns the strip and calls this after a tab click. The panel never paints its own
     * mode buttons; switching modes only changes which cached form and list are visible.
     */
    static void setSubtab(final GovFinesPanel p, final int index) {
        final int next = GovMetrics.clamp(index, 0, TAB_WAIVE);
        if (next == p.subtab) {
            return;
        }
        p.scrollByTab[p.subtab] = p.listScroll;
        p.pageByTab[p.subtab] = p.page;
        p.subtab = next;
        p.listScroll = p.scrollByTab[next];
        p.page = p.pageByTab[next];
        p.status = "";
        p.statusIsError = false;
        disarm(p);
        unfocusAll(p);
        if (next == TAB_ISSUE) {
            GovFinesSelection.ensureIssueTarget(p);
        }
        if (next == TAB_COLLECT || next == TAB_WAIVE) {
            GovFinesSelection.ensureUnpaidSelection(p);
        }
        syncWidgets(p);
        GovFinesRequests.requestListIfNeeded(p);
        GovFinesViews.rebuildAll(p);
    }

    /** Recomputes every rectangle inside the content area. Called on init and resize only. */
    static void syncWidgets(final GovFinesPanel p) {
        final boolean issue = p.active && p.subtab == TAB_ISSUE;
        final boolean aggravated = "AGGRAVATED".equals(p.issueBand);
        final boolean withRestitution = "FELONY".equals(p.issueBand) || aggravated;
        setVisible(p, p.articleBox, issue);
        setVisible(p, p.restitutionBox, issue);
        setVisible(p, p.victimBox, issue && withRestitution);
        setVisible(p, p.exileBox, issue && aggravated);
        setVisible(p, p.issueReasonBox, issue);
        setVisible(p, p.waiveReasonBox, p.active && p.subtab == TAB_WAIVE);
    }

    static void setVisible(final GovFinesPanel p, final EditBox box, final boolean visible) {
        if (box == null) {
            return;
        }
        box.setVisible(visible);
        if (!visible && p.host.getFocused() == box) {
            p.host.setFocused(null);
        }
    }

    static void unfocusAll(final GovFinesPanel p) {
        if (p.widgets.isEmpty()) {
            return;
        }
        for (int i = 0; i < p.widgets.size(); i++) {
            final EditBox box = p.widgets.get(i);
            if (box != null && p.host.getFocused() == box) {
                p.host.setFocused(null);
            }
        }
    }

    static void disarm(final GovFinesPanel p) {
        p.armedClick = null;
        p.armedAt = 0L;
    }

    static boolean isArmed(final GovFinesPanel p, final Click click, final long now) {
        return p.armedClick == click
                && now - p.armedAt >= ARM_FLOOR_MS
                && now - p.armedAt <= ARM_WINDOW_MS;
    }

    static boolean isArmedVisual(final GovFinesPanel p, final Click click, final long now) {
        return p.armedClick == click && now - p.armedAt >= 0L
                && now - p.armedAt <= ARM_WINDOW_MS;
    }

    static void setStatus(final GovFinesPanel p, final String text, final boolean error) {
        p.status = text == null ? "" : text;
        p.statusIsError = error;
    }


}
