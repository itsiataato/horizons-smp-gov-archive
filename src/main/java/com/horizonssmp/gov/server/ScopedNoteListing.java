package com.horizonssmp.gov.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.horizonssmp.gov.common.ErrorCode;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.NoteScope;
import com.horizonssmp.gov.common.records.BusinessRecord;
import com.horizonssmp.gov.common.records.JobRecord;
import com.horizonssmp.gov.common.records.NoteRecord;
import com.horizonssmp.gov.server.Sanitizer;

/** Listing and field-map helpers for scoped notes. Package-private. */
final class ScopedNoteListing {

    private ScopedNoteListing() {
    }

    static ScopedNoteService.ListResult list(
            final GovSavedData data,
            final ScopedNoteService.Actor actor,
            final String rawScope,
            final String rawScopeId,
            final java.util.UUID targetUuid,
            final String targetName,
            final int rawPage) {
        final ScopedNoteScopeKey key = ScopedNoteScopeKey.parse(rawScope, rawScopeId);
        if (key == null) {
            return ScopedNoteService.ListResult.refused(
                    ErrorCode.BAD_PAYLOAD, ScopedNoteSupport.scopeMessage(rawScope));
        }
        if (!ScopedNoteSupport.usable(data, actor)) {
            return ScopedNoteService.ListResult.refused(ErrorCode.INVALID_STATE, "No note scope to read.");
        }

        final List<NoteRecord> all;
        final ScopedNoteService.ScopeView view;
        if (key.type() == NoteScope.JOB) {
            final JobRecord job = data.job(key.id());
            if (job == null) {
                return ScopedNoteService.ListResult.refused(
                        ErrorCode.BAD_JOB, JobService.noSuchJobMessage(key.id()));
            }
            if (!ScopedNoteAuthorization.jobCanRead(data, actor, key.id())) {
                return ScopedNoteService.ListResult.refused(ErrorCode.FORBIDDEN, "You may not read that job's notes.");
            }
            all = targetUuid == null
                    ? data.notesInScope(NoteScope.JOB, key.id())
                    : data.notesInScopeFor(NoteScope.JOB, key.id(), targetUuid);
            view = new ScopedNoteService.ScopeView(
                    NoteScope.JOB,
                    key.id(),
                    ScopedNoteSupport.label(job.displayName(), "Job " + key.id(), key.id()),
                    ScopedNoteAuthorization.jobMember(data, actor, key.id()) ? "member" : "supervisor",
                    ScopedNoteAuthorization.jobCanPost(data, actor, key.id()),
                    ScopedNoteAuthorization.jobCanModerate(data, actor, key.id()));
        } else {
            final BusinessRecord business = data.business(key.id());
            if (business == null) {
                return ScopedNoteService.ListResult.refused(
                        ErrorCode.BAD_BUSINESS,
                        BusinessService.noSuchBusinessMessage(key.id()));
            }
            if (targetUuid == null) {
                if (!ScopedNoteAuthorization.boardCanRead(business, actor)) {
                    return ScopedNoteService.ListResult.refused(
                            ErrorCode.FORBIDDEN, "You may not read that business's notes.");
                }
                all = data.notesInScopeFor(NoteScope.BUSINESS, key.id(), null);
            } else {
                if (!ScopedNoteAuthorization.perPlayerCanRead(business, actor)) {
                    return ScopedNoteService.ListResult.refused(
                            ErrorCode.FORBIDDEN, "You may not read that business's notes.");
                }
                all = data.notesInScopeFor(NoteScope.BUSINESS, key.id(), targetUuid);
            }
            view = new ScopedNoteService.ScopeView(
                    NoteScope.BUSINESS,
                    key.id(),
                    ScopedNoteSupport.label(business.name(), "Business " + key.id(), key.id()),
                    ScopedNoteAuthorization.businessRole(business, actor),
                    targetUuid == null
                            ? ScopedNoteAuthorization.boardCanPost(business, actor)
                            : ScopedNoteAuthorization.perPlayerCanPost(business, actor, targetUuid),
                    targetUuid == null
                            ? ScopedNoteAuthorization.businessOwner(business, actor)
                                    || ScopedNoteAuthorization.businessManager(business, actor)
                                    || ScopedNoteAuthorization.businessManage(actor)
                            : ScopedNoteAuthorization.businessOwner(business, actor)
                                    || ScopedNoteAuthorization.businessManage(actor));
        }

        all.sort(ScopedNoteSupport.newestFirst());
        final int total = all.size();
        final int pages = Math.max(1, (total + NoteKeys.PAGE_SIZE - 1) / NoteKeys.PAGE_SIZE);
        final int page = Math.max(0, Math.min(rawPage, pages - 1));
        final List<ScopedNoteService.Row> rows = new ArrayList<>(NoteKeys.PAGE_SIZE);
        final int start = page * NoteKeys.PAGE_SIZE;
        for (int i = start; i < Math.min(total, start + NoteKeys.PAGE_SIZE); i++) {
            final NoteRecord note = all.get(i);
            final String name = note.targetUuid() == null
                    ? ""
                    : (targetUuid != null && targetName != null && !targetName.isBlank()
                            ? Sanitizer.name(targetName)
                            : ScopedNoteSupport.targetName(data, note.targetUuid()));
            rows.add(new ScopedNoteService.Row(note, name));
        }
        return ScopedNoteService.ListResult.ok(view, new ScopedNoteService.Page(rows, total, page, pages));
    }

    static Map<String, String> scopesFields(final List<ScopedNoteService.ScopeView> scopes) {
        final List<ScopedNoteService.ScopeView> views = scopes == null ? List.of() : scopes;
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(NoteKeys.SCOPES, Integer.toString(views.size()));
        for (int i = 0; i < views.size(); i++) {
            final ScopedNoteService.ScopeView view = views.get(i);
            final String prefix = NoteKeys.SCOPE_PREFIX + i;
            fields.put(prefix + NoteKeys.SCOPE_ROW_TYPE, view.type().wireId());
            fields.put(prefix + NoteKeys.SCOPE_ROW_ID, view.id());
            fields.put(prefix + NoteKeys.SCOPE_ROW_LABEL, view.label());
            fields.put(prefix + NoteKeys.SCOPE_ROW_ROLE, view.role());
            fields.put(prefix + NoteKeys.SCOPE_ROW_CAN_POST, Boolean.toString(view.canPost()));
            fields.put(prefix + NoteKeys.SCOPE_ROW_CAN_MODERATE,
                    Boolean.toString(view.canModerate()));
        }
        return fields;
    }

    static Map<String, String> listFields(final ScopedNoteService.ListResult result) {
        if (result == null || !result.ok()) {
            return Map.of();
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(NoteKeys.SCOPE_TYPE, result.scope().type().wireId());
        fields.put(NoteKeys.SCOPE_ID, result.scope().id());
        fields.put(NoteKeys.SCOPE_LABEL, result.scope().label());
        fields.put(NoteKeys.CAN_POST_NOTES, Boolean.toString(result.scope().canPost()));
        fields.put(NoteKeys.CAN_MODERATE_NOTES, Boolean.toString(result.scope().canModerate()));
        fields.put(NoteKeys.CAN_MANAGE, Boolean.toString(result.scope().canPost()));
        fields.put(NoteKeys.TOTAL, Integer.toString(result.page().total()));
        fields.put(NoteKeys.PAGE, Integer.toString(result.page().page()));
        fields.put(NoteKeys.PAGES, Integer.toString(result.page().pages()));
        for (int i = 0; i < result.page().rows().size(); i++) {
            final ScopedNoteService.Row row = result.page().rows().get(i);
            PersonService.writeNoteRow(fields, i, row.note(), row.targetName());
        }
        return fields;
    }
}