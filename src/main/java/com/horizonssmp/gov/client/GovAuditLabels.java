package com.horizonssmp.gov.client;

import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.GovExportSection;

import net.minecraft.network.chat.Component;

final class GovAuditLabels {
    static void rebuildLabels(final GovAuditPanel p) {
        p.applyText = upper(tr("gui.gov.audit.apply"));
        p.exportText = upper(tr("gui.gov.audit.export"));
        p.exportSectionText = upper(tr("gui.gov.audit.export_section"));
        p.prevText = upper(tr("gui.gov.previous"));
        p.nextText = upper(tr("gui.gov.next"));
        p.refreshText = upper(tr("gui.gov.refresh"));
        p.beginText = upper(tr("gui.gov.audit.admin.begin"));
        p.commitText = upper(tr("gui.gov.audit.admin.commit"));
        p.confirmCommitText = upper(tr("gui.gov.audit.admin.confirm_commit"));
        p.confirmExportText = upper(tr("gui.gov.jail.confirm_prefix") + " "
                + tr("gui.gov.audit.export"));
        p.retryText = upper(tr("gui.gov.card.retry"));
        p.actorLabelText = upper(tr("gui.gov.audit.detail.actor"));
        p.actionLabelText = upper(tr("gui.gov.audit.detail.action"));
        p.targetLabelText = upper(tr("gui.gov.audit.detail.target"));
        p.commitContextPrefix = upper(tr("gui.gov.audit.token_hint")) + " ";
        p.arrowLeftText = "<";
        p.arrowRightText = ">";
        p.loadingText = upper(tr("gui.gov.audit.loading"));
        p.errorText = upper(tr("gui.gov.audit.error"));
        p.emptyText = upper(tr("gui.gov.audit.empty"));
        p.detailErrorText = upper(tr("gui.gov.audit.detail_error"));
        p.noSelectionText = upper(tr("gui.gov.audit.no_selection"));
        p.adminLockedLongText = upper(tr("gui.gov.audit.admin_locked_long"));
        p.tokenRequiredText = upper(tr("gui.gov.audit.token_required"));
        p.noneText = upper(tr("gui.gov.audit.none"));
        p.resultOkText = upper(tr("gui.gov.audit.result.ok"));
        p.resultDeniedText = upper(tr("gui.gov.audit.result.denied"));
        p.exportLockedText = upper(tr("gui.gov.audit.export_locked"));
        p.adminHeaderText = upper(tr("gui.gov.audit.mode.admin"));
        p.adminHeaderComponent = GovText.boldCached(p.adminHeaderText);
        p.adminWarningText = upper(tr("gui.gov.audit.admin.warning"));
        p.detailLabels = new String[] {
                upper(tr("gui.gov.audit.detail.at")),
                upper(tr("gui.gov.audit.detail.actor")),
                upper(tr("gui.gov.audit.detail.target")),
                upper(tr("gui.gov.audit.detail.outcome")),
                upper(tr("gui.gov.audit.detail.payload")),
                upper(tr("gui.gov.audit.detail.reason"))
        };
        updateOutcomeLabel(p);
        updateWindowLabel(p);
        updateSectionText(p);
    }

    static String tr(final String key, final Object... args) {
        return GovAuditPanel.tr(key, args);
    }

    static String upper(final String text) {
        return GovAuditPanel.upper(text);
    }

    static void updateOutcomeLabel(final GovAuditPanel p) {
        p.outcomeLabelText = upper(tr("gui.gov.audit.outcome", outcomeDisplay(p)));
    }

    static String outcomeDisplay(final GovAuditPanel p) {
        return switch (p.outcome) {
            case AuditKeys.OUTCOME_ACCEPTED -> tr("gui.gov.audit.outcome.accepted");
            case AuditKeys.OUTCOME_REFUSED -> tr("gui.gov.audit.outcome.refused");
            default -> tr("gui.gov.audit.outcome.all");
        };
    }

    static void updateWindowLabel(final GovAuditPanel p) {
        p.windowLabelText = upper(tr("gui.gov.audit.window", p.window));
    }

    static void updateSectionText(final GovAuditPanel p) {
        p.sectionText = upper(tr("gui.gov.audit.section.label",
                upper(tr(selectedSection(p).labelKey()))));
    }

    static GovExportSection selectedSection(final GovAuditPanel p) {
        return GovExportSection.values()[p.exportSectionIndex];
    }
}