package com.horizonssmp.gov.server;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.horizonssmp.gov.Gov;
import com.horizonssmp.gov.common.ActionResult;
import com.horizonssmp.gov.common.AuditKeys;
import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.GovExportSection;
import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.enums.AuditOutcome;
import com.horizonssmp.gov.common.records.AuditEntry;
import com.horizonssmp.gov.common.records.PermitRecord;


import static com.horizonssmp.gov.server.GovHandlerSupport.*;

public final class GovAuditHandlers {

    private GovAuditHandlers() {
    }

    // ------------------------------------------------------------------ audit

    /** {@code AUDIT_PAGE}: one filtered page of the append-only log. A read; never audited. */
    static ActionResult auditPage(final GovActionDispatcher.ActionContext context) {
        final AuditService.Filter filter = AuditService.filter(
                context.payload(AuditKeys.KEY_ACTOR),
                context.payload(AuditKeys.KEY_ACTION),
                context.payload(AuditKeys.KEY_TARGET),
                context.payload(AuditKeys.KEY_OUTCOME),
                context.payload(AuditKeys.KEY_WINDOW),
                context.now());
        final Map<String, String> fields = AuditService.listFields(
                context.data(), filter,
                parsePage(context.payload(AuditKeys.KEY_PAGE)), context.capabilities());
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    /** {@code AUDIT_INFO}: every field of one line. A read; never audited. */
    static ActionResult auditInfo(final GovActionDispatcher.ActionContext context) {
        final long seq = parseLong(context.payload(AuditKeys.KEY_SEQ), -1L);
        final AuditEntry entry = AuditService.bySeq(context.data(), seq);
        if (entry == null) {
            return ActionResult.failure(
                    context.request().reqId(), ErrorCode.BAD_AUDIT,
                    AuditService.noSuchEntryMessage(context.payload(AuditKeys.KEY_SEQ)));
        }
        return ActionResult.success(
                context.request().reqId(),
                GovFields.encode(AuditService.detailFields(entry)));
    }

    /** {@code AUDIT_EXPORT}: writes the log to a fixed, verified JSON file and audits the export. */
    static ActionResult auditExport(final GovActionDispatcher.ActionContext context) {
        final GovExportService.Result result = GovExportService.exportSection(
                context.data(),
                context.actor().getServer().getServerDirectory(),
                GovExportSection.AUDIT,
                context.now());
        return exportResult(context, result, GovExportSection.AUDIT, "AUDIT_EXPORT");
    }

    /** {@code ADMIN_EXPORT}: writes one registry section to a fixed, verified JSON file. */
    static ActionResult adminExport(final GovActionDispatcher.ActionContext context) {
        final String raw = context.payload(AuditKeys.KEY_SECTION);
        final GovExportSection section = GovExportSection.byId(raw).orElse(null);
        if (section == null) {
            final String message = "Unknown export section '" + Sanitizer.singleLine(raw, Sanitizer.MAX_ID)
                    + "'. Sections: " + GovExportSection.ids() + ".";
            AuditService.appendOutcome(
                    context.data(), context.actor().getUUID(),
                    context.actor().getName().getString(),
                    context.actorRecord().primaryJobId(),
                    "ADMIN_EXPORT", AuditOutcome.REFUSED, null, "",
                    "section=" + Sanitizer.singleLine(raw, Sanitizer.MAX_ID), message);
            return ActionResult.failure(context.request().reqId(), ErrorCode.BAD_PAYLOAD, message);
        }
        final GovExportService.Result result = GovExportService.exportSection(
                context.data(),
                context.actor().getServer().getServerDirectory(),
                section,
                context.now());
        return exportResult(context, result, section, "ADMIN_EXPORT");
    }

    /** Turns an export receipt into the structured result the GUI and the audit line need. */
    static ActionResult exportResult(
            final GovActionDispatcher.ActionContext context,
            final GovExportService.Result result,
            final GovExportSection section,
            final String action) {
        final String actorName = Sanitizer.name(context.actor().getName().getString());
        if (!result.ok()) {
            AuditService.appendOutcome(
                    context.data(), context.actor().getUUID(), actorName,
                    context.actorRecord().primaryJobId(), action, AuditOutcome.REFUSED,
                    null, "", "section=" + section.id(), result.message());
            return ActionResult.failure(
                    context.request().reqId(), result.code(), result.message());
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(AuditKeys.EXPORT_PATH, result.relativePath());
        fields.put(AuditKeys.EXPORT_BYTES, Long.toString(result.bytes()));
        fields.put(AuditKeys.EXPORT_SHA256, result.sha256());
        fields.put(AuditKeys.EXPORT_SECTION, section.id());
        fields.put(AuditKeys.EXPORT_AT, Long.toString(context.now()));
        fields.put(AuditKeys.EXPORT_FORMAT, result.format());
        AuditService.appendOutcome(
                context.data(), context.actor().getUUID(), actorName,
                context.actorRecord().primaryJobId(), action, AuditOutcome.ACCEPTED,
                null, "",
                "section=" + section.id() + "; path=" + result.relativePath()
                        + "; bytes=" + result.bytes() + "; sha256=" + result.sha256(),
                "export written");
        return ActionResult.success(
                context.request().reqId(), GovFields.encode(fields));
    }

    /** {@code SEASON_STATUS}: the plan, counts and this actor's pending token. A read. */
    static ActionResult seasonStatus(final GovActionDispatcher.ActionContext context) {
        final Map<String, String> fields = SeasonResetService.statusFields(
                context.data(),
                seasonActor(context),
                SeasonResetService.Policy.fromConfig(),
                context.now());
        return ActionResult.success(context.request().reqId(), GovFields.encode(fields));
    }

    /** {@code SEASON_BEGIN}: returns a single-use token and the exact scope. */
    static ActionResult seasonBegin(final GovActionDispatcher.ActionContext context) {
        final SeasonResetService.Outcome outcome = SeasonResetService.begin(
                context.data(),
                seasonActor(context),
                SeasonResetService.Policy.fromConfig(),
                context.now());
        if (!outcome.ok()) {
            return ActionResult.failure(
                    context.request().reqId(), outcome.code(), outcome.message());
        }
        return ActionResult.success(
                context.request().reqId(),
                GovFields.encode(SeasonResetService.beginFields(outcome)));
    }

    /** {@code SEASON_COMMIT}: verifies the token and the full backup, then clears the season. */
    static ActionResult seasonCommit(final GovActionDispatcher.ActionContext context) {
        final SeasonResetService.Outcome outcome = SeasonResetService.commit(
                context.data(),
                context.actor().getServer().getServerDirectory(),
                seasonActor(context),
                context.payload(AuditKeys.KEY_TOKEN),
                context.now());
        if (!outcome.ok()) {
            return ActionResult.failure(
                    context.request().reqId(), outcome.code(), outcome.message());
        }
        return ActionResult.success(
                context.request().reqId(),
                GovFields.encode(SeasonResetService.commitFields(outcome)));
    }

    static SeasonResetService.Actor seasonActor(
            final GovActionDispatcher.ActionContext context) {
        return new SeasonResetService.Actor(
                context.actor().getUUID(), context.actor().getName().getString());
    }


}
