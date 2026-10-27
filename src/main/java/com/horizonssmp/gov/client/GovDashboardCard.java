package com.horizonssmp.gov.client;

import com.horizonssmp.gov.common.SelfCardKeys;
import com.horizonssmp.gov.network.GovActionResultPayload;

import net.minecraft.network.chat.Component;

final class GovDashboardCard {
    private GovDashboardCard() {}

    static void requestCardIfNeeded(final GovDashboardScreen s) {
        if (s.pendingReqId.isEmpty() && s.cardState != GovDashboardScreen.CardState.READY) {
            requestCard(s);
        }
    }

    static void requestCard(final GovDashboardScreen s) {
        s.pendingReqId = java.util.UUID.randomUUID().toString();
        s.cardState = GovDashboardScreen.CardState.LOADING;
        s.cardErrorLine = "";
        s.cardStatus = "";
        s.cardErrorLines = java.util.List.of();
        com.horizonssmp.gov.network.GovNetwork.sendRequest(
                com.horizonssmp.gov.common.OpCode.OPEN_TAB, java.util.Map.of(), s.pendingReqId);
    }

    static boolean handleCardResult(final GovDashboardScreen s, final GovActionResultPayload payload) {
        if (payload.reqId().isEmpty() || !payload.reqId().equals(s.pendingReqId)) {
            return false;
        }
        s.pendingReqId = "";
        if (payload.ok()) {
            s.cardFields = com.horizonssmp.gov.common.GovFields.decode(payload.delta());
            s.cardState = GovDashboardScreen.CardState.READY;
            s.cardErrorLine = "";
        } else {
            s.cardErrorLine = payload.errorText().isBlank()
                    ? s.errorTitle : payload.errorText();
            s.cardState = GovDashboardScreen.CardState.ERROR;
        }
        rebuildCardModel(s);
        return true;
    }

    static void rebuildCardModel(final GovDashboardScreen s) {
        if (s.layout == null || s.font() == null) {
            return;
        }
        final boolean ready = s.cardState == GovDashboardScreen.CardState.READY;
        if (!ready) {
            s.cardRows = java.util.List.of();
            s.restrictions = java.util.List.of();
            s.cardStatus = "";
            s.railJob = s.noAppointment;
            s.railDuty = s.noAppointment;
            s.receptionNoteLines = java.util.List.of();
            if (s.cardState == GovDashboardScreen.CardState.ERROR) {
                final int errorWidth = Math.max(1, s.cardRect.w() - s.cardPad * 2);
                final String message = s.cardErrorLine;
                s.cardErrorLines = message.isBlank() || message.equals(s.errorTitle)
                        ? java.util.List.of()
                        : GovText.wrapCached(s.font(), message, errorWidth);
            } else {
                s.cardErrorLines = java.util.List.of();
            }
            return;
        }
        s.cardErrorLines = java.util.List.of();

        final String name = value(s, SelfCardKeys.NAME);
        final String job = value(s, SelfCardKeys.JOB);
        final String appointment = "-".equals(job) || job.isBlank() ? s.noAppointment : job;
        final String rank = value(s, SelfCardKeys.RANK);
        final String duty = dutyText(s, value(s, SelfCardKeys.DUTY));
        final boolean suspect = "true".equals(value(s, SelfCardKeys.SUSPECT));
        final boolean held = SelfCardKeys.JAIL_HELD.equals(value(s, SelfCardKeys.JAIL));
        final int fines = parseInt(value(s, SelfCardKeys.FINES));
        s.canPeopleTab = capabilityFlag(s, SelfCardKeys.CAP_PEOPLE, false);
        s.canNotesTab = capabilityFlag(s, SelfCardKeys.CAP_NOTES, false);
        s.canAuditTab = capabilityFlag(s, SelfCardKeys.CAP_AUDIT, false);
        s.canJobsManageTab = capabilityFlag(s, SelfCardKeys.CAP_JOBS_MANAGE, false);
        GovDashboardTabs.rebuildVisibleTabs(s);
        if (!s.visibleTabs.contains(s.activeTab)) {
            s.selectTab(GovDashboardScreen.Tab.DASHBOARD);
        }
        final String cell = value(s, SelfCardKeys.JAIL_CELL);
        final long until = parseLong(value(s, SelfCardKeys.JAIL_UNTIL));
        final String detention = detentionText(s, held, cell, until);

        s.cardStatus = held ? s.heldText
                : suspect ? s.flaggedText
                : fines > 0 ? s.dueText : s.clearText;
        s.railJob = appointment;
        s.railDuty = duty;

        final java.util.List<GovDashboardScreen.CardRow> rows = new java.util.ArrayList<>(7);
        rows.add(new GovDashboardScreen.CardRow(s.cardLabels[0], name, null));
        rows.add(new GovDashboardScreen.CardRow(s.cardLabels[1], appointment, null));
        rows.add(new GovDashboardScreen.CardRow(s.cardLabels[2], rank, null));
        rows.add(new GovDashboardScreen.CardRow(s.cardLabels[3], duty, null));
        rows.add(new GovDashboardScreen.CardRow(s.cardLabels[4], "", suspect ? s.flaggedStamp : s.clearStamp));
        rows.add(new GovDashboardScreen.CardRow(s.cardLabels[5], held ? detention : "",
                held ? s.heldStamp : s.clearStamp));
        rows.add(new GovDashboardScreen.CardRow(s.cardLabels[6], Integer.toString(fines),
                fines > 0 ? s.dueStamp : null));

        if (rows.size() > s.cardMaxRows) {
            rows.remove(2);
        }
        if (rows.size() > s.cardMaxRows) {
            rows.remove(1);
        }
        if (rows.size() > s.cardMaxRows) {
            rows.remove(1);
        }
        while (rows.size() > s.cardMaxRows) {
            rows.remove(rows.size() - 1);
        }
        s.cardRows = java.util.List.copyOf(rows);

        final java.util.List<GovDashboardScreen.RestrictionRow> found = new java.util.ArrayList<>(3);
        if (suspect) {
            found.add(new GovDashboardScreen.RestrictionRow(s.flaggedStamp, GovDashboardScreen.tr("gui.gov.card.suspect")));
        }
        if (held) {
            found.add(new GovDashboardScreen.RestrictionRow(s.heldStamp,
                    detention.isBlank() ? GovDashboardScreen.tr("gui.gov.jail.held") : detention));
        }
        if (fines > 0) {
            found.add(new GovDashboardScreen.RestrictionRow(s.dueStamp,
                    fines + "  " + GovDashboardScreen.tr("gui.gov.card.fines")));
        }
        s.restrictions = java.util.List.copyOf(found);

        final int noteWidth = Math.max(1, s.cardRect.w() - s.cardPad * 2);
        s.receptionNoteLines = s.receptionNote.isEmpty()
                ? java.util.List.of() : GovText.wrapCached(s.font(), s.receptionNote, noteWidth);
    }

    static String detentionText(final GovDashboardScreen s, final boolean held, final String cell, final long until) {
        if (!held) {
            return "";
        }
        final StringBuilder out = new StringBuilder();
        if (!"-".equals(cell) && !cell.isBlank()) {
            out.append(cell);
        }
        if (until > 0L) {
            final long remaining = until - System.currentTimeMillis();
            if (out.length() > 0) {
                out.append("  ");
            }
            if (remaining <= 0L) {
                out.append(GovDashboardScreen.tr("gui.gov.jail.release_due"));
            } else {
                out.append(GovDashboardScreen.tr("gui.gov.jail.remaining", Math.max(1L, remaining / 60000L)));
            }
        }
        return out.length() == 0 ? GovDashboardScreen.tr("gui.gov.jail.held") : out.toString();
    }

    static String dutyText(final GovDashboardScreen s, final String duty) {
        return switch (duty) {
            case "ON_DUTY" -> s.onDuty;
            case "OFF_DUTY" -> s.offDuty;
            default -> s.noAppointment;
        };
    }

    static boolean capabilityFlag(final GovDashboardScreen s, final String key, final boolean defaultValue) {
        final String raw = s.cardFields.get(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return "true".equals(raw);
    }

    static String value(final GovDashboardScreen s, final String key) {
        return com.horizonssmp.gov.common.GovFields.value(s.cardFields, key);
    }

    static int parseInt(final String value) {
        return com.horizonssmp.gov.common.GovFields.parseInt(value, 0);
    }

    static long parseLong(final String value) {
        return com.horizonssmp.gov.common.GovFields.parseLong(value, 0L);
    }
}
