package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovFinesTypes.*;
import static com.horizonssmp.gov.client.GovFinesTypes.*;

import java.util.LinkedHashMap;
import java.util.Map;

import com.horizonssmp.gov.common.FineKeys;
import com.horizonssmp.gov.common.OpCode;

final class GovFinesActions {

    // ------------------------------------------------------------------- actions

    /** Enter on the visible form: issue, collect, or confirm an armed waive. */
    static void submitEnter(final GovFinesPanel p) {
        switch (p.subtab) {
            case TAB_ISSUE -> submitIssue(p);
            case TAB_COLLECT -> {
                // Collect is the primary non-destructive action; Pay stays pointer-only.
                if (collectEligible(p)) {
                    collectSelected(p);
                }
            }
            case TAB_WAIVE -> {
                if (GovFinesSupport.isArmed(p, Click.WAIVE_SUBMIT, System.currentTimeMillis())) {
                    submitWaive(p);
                }
            }
            default -> {
                // Register has no form; Enter leaves the list alone.
            }
        }
    }

    static boolean collectEligible(final GovFinesPanel p) {
        return p.canCollect
                && "true".equals(p.detail.getOrDefault(FineKeys.CAN_COLLECT_THIS, "false"))
                && "UNPAID".equals(p.detail.getOrDefault(FineKeys.STATE, ""));
    }

    static boolean payEligible(final GovFinesPanel p) {
        return "true".equals(p.detail.getOrDefault(FineKeys.CAN_PAY, "false"))
                && "UNPAID".equals(p.detail.getOrDefault(FineKeys.STATE, ""));
    }

    static boolean waiveEligible(final GovFinesPanel p) {
        return p.canWaive
                && "true".equals(p.detail.getOrDefault(FineKeys.CAN_WAIVE_THIS, "false"))
                && "UNPAID".equals(p.detail.getOrDefault(FineKeys.STATE, ""));
    }

    static void submitIssue(final GovFinesPanel p) {
        if (!p.canIssue || GovFinesRequests.mutationPending(p)) {
            return;
        }
        final String target = GovOfflineTarget.clean(p.issueTarget);
        if (target.isBlank()) {
            GovFinesSupport.setStatus(p, GovFinesLabels.label(p, GovOfflineTarget.blankKey(
                    p.online.isEmpty(), "gui.gov.fines.no_online", "gui.gov.fines.no_target")), true);
            GovFinesViews.rebuildStatus(p);
            return;
        }
        final String reason = p.issueReasonBox == null
                ? "" : p.issueReasonBox.getValue().strip();
        if (reason.isEmpty()) {
            GovFinesSupport.setStatus(p, GovFinesLabels.label(p, "gui.gov.fines.reason_required"), true);
            GovFinesViews.rebuildStatus(p);
            return;
        }
        final String restitutionRaw = p.restitutionBox == null
                ? "" : p.restitutionBox.getValue().strip();
        final Long restitution = restitutionRaw.isEmpty() ? Long.valueOf(0L)
                : GovFinesHelpers.parseWhole(restitutionRaw);
        if (restitution == null) {
            GovFinesHelpers.numberError(p);
            return;
        }
        final String exileRaw = p.exileBox == null
                ? "" : p.exileBox.getValue().strip();
        final Long exile;
        if ("AGGRAVATED".equals(p.issueBand) && !exileRaw.isEmpty()) {
            exile = GovFinesHelpers.parseWhole(exileRaw);
            if (exile == null) {
                GovFinesHelpers.numberError(p);
                return;
            }
        } else {
            exile = Long.valueOf(0L);
        }
        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put("target", target);
        payload.put(FineKeys.KEY_BAND, p.issueBand);
        payload.put(FineKeys.KEY_ARTICLE,
                p.articleBox == null || p.articleBox.getValue().isBlank()
                        ? "9.11" : p.articleBox.getValue().strip());
        payload.put(FineKeys.KEY_RESTITUTION, Long.toString(restitution));
        payload.put(FineKeys.KEY_VICTIM,
                p.victimBox == null ? "" : p.victimBox.getValue().strip());
        payload.put(FineKeys.KEY_EXILE_DAYS, exile > 0L ? Long.toString(exile) : "");
        payload.put(FineKeys.KEY_REASON, reason);
        GovFinesRequests.mutate(p, OpCode.FINE_ISSUE, payload);
        GovFinesViewButtons.rebuildButtons(p);
    }

    static void collectSelected(final GovFinesPanel p) {
        if (GovFinesRequests.mutationPending(p) || p.selectedId.isEmpty()) {
            return;
        }
        final String target = p.detail.getOrDefault(FineKeys.TARGET_UUID, "");
        if (target.isBlank()) {
            GovFinesSupport.setStatus(p, GovFinesLabels.label(p, "gui.gov.fines.detail_error"), true);
            GovFinesViews.rebuildStatus(p);
            return;
        }
        GovFinesRequests.mutate(p, OpCode.FINE_COLLECT, Map.of(
                "target", target,
                FineKeys.KEY_FINE_ID, p.selectedId));
        GovFinesViewButtons.rebuildButtons(p);
    }

    static void paySelected(final GovFinesPanel p) {
        if (GovFinesRequests.mutationPending(p) || p.selectedId.isEmpty()) {
            return;
        }
        final String target = p.viewerUuid.isBlank()
                ? p.detail.getOrDefault(FineKeys.TARGET_UUID, "") : p.viewerUuid;
        GovFinesRequests.mutate(p, OpCode.FINE_PAY, Map.of(
                "target", target,
                FineKeys.KEY_FINE_ID, p.selectedId));
        GovFinesViewButtons.rebuildButtons(p);
    }

    static void submitWaive(final GovFinesPanel p) {
        if (GovFinesRequests.mutationPending(p) || p.selectedId.isEmpty()) {
            return;
        }
        final String reason = p.waiveReasonBox == null
                ? "" : p.waiveReasonBox.getValue().strip();
        if (reason.isEmpty()) {
            GovFinesSupport.setStatus(p, GovFinesLabels.label(p, "gui.gov.fines.reason_required"), true);
            GovFinesViews.rebuildStatus(p);
            return;
        }
        final long now = System.currentTimeMillis();
        if (p.armedClick == Click.WAIVE_SUBMIT) {
            final long elapsed = now - p.armedAt;
            if (elapsed < ARM_FLOOR_MS) {
                return;
            }
            if (elapsed <= ARM_WINDOW_MS) {
                p.armedClick = null;
                final String target = p.detail.getOrDefault(FineKeys.TARGET_UUID, "");
                if (target.isBlank()) {
                    GovFinesSupport.setStatus(p, GovFinesLabels.label(p, "gui.gov.fines.detail_error"), true);
                    GovFinesViews.rebuildStatus(p);
                    return;
                }
                GovFinesRequests.mutate(p, OpCode.FINE_WAIVE, Map.of(
                        "target", target,
                        FineKeys.KEY_FINE_ID, p.selectedId,
                        FineKeys.KEY_REASON, reason));
                GovFinesViewButtons.rebuildButtons(p);
                return;
            }
        }
        p.armedClick = Click.WAIVE_SUBMIT;
        p.armedAt = now;
    }

    static void clearIssueFields(final GovFinesPanel p) {
        if (p.issueReasonBox != null) {
            p.issueReasonBox.setValue("");
        }
        if (p.victimBox != null) {
            p.victimBox.setValue("");
        }
        if (p.restitutionBox != null) {
            p.restitutionBox.setValue("0");
        }
        if (p.waiveReasonBox != null) {
            p.waiveReasonBox.setValue("");
        }
        p.issueTarget = "";
        p.issueBand = "PETTY";
        GovFinesSelection.ensureIssueTarget(p);
    }


}
