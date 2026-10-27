package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.horizonssmp.gov.common.GovFields;
import com.horizonssmp.gov.common.NoteKeys;
import com.horizonssmp.gov.common.OpCode;
import com.horizonssmp.gov.network.GovNetwork;

class GovNotesScopes extends GovNotesGeometryCache {
    GovNotesScopes(final GovDashboardScreen host) {
        super(host);
    }

    protected void ensureMode() {
        if (this.mode == 0) {
            ensureView(this.allView);
            return;
        }
        if (this.scopesState == Load.NONE) {
            requestScopes();
            return;
        }
        if (this.scopesState == Load.LOADING) {
            if (System.currentTimeMillis() - this.scopesSentAt >= 8000L) {
                requestScopes();
            }
            return;
        }
        if (this.scopesState != Load.READY) {
            return;
        }
        if (this.mode == 1) {
            ensureView(this.jobViews.get(this.selectedJobId));
        } else if (this.mode == 2) {
            final BusinessState state = this.bizStates.get(this.selectedBizId);
            if (state != null) {
                ensureView(state.current());
            }
        }
    }

    protected void ensureView(final View view) {
        if (view == null) {
            return;
        }
        if (view.mode == 2 && view.personal && view.target.isBlank()) {
            syncWidgets();
            return;
        }
        if (view.state == Load.NONE) {
            requestList(view, view.page, view.target);
        } else if (view.state == Load.READY && view.stale) {
            requestList(view, view.page, view.target);
        }
    }

    protected void requestScopes() {
        final long now = System.currentTimeMillis();
        if (this.scopesPending && now - this.scopesSentAt < 8000L) {
            return;
        }
        final String reqId = UUID.randomUUID().toString();
        this.scopesPending = true;
        this.scopesSentAt = now;
        this.latestScopesReqId = reqId;
        this.scopesState = Load.LOADING;
        this.scopesError = "";
        this.pending.put(reqId, new Pending(RequestKind.SCOPES, OpCode.NOTE_SCOPES,
                null, "", 0));
        GovNetwork.sendRequest(OpCode.NOTE_SCOPES, Map.of(), reqId);
        rebuildScopeGeometry();
        rebuildViewGeometry(currentView());
        syncWidgets();
    }

    protected void requestList(final View view, final int page, final String target) {
        if (view == null) {
            return;
        }
        expireMutationIfStale();
        final long now = System.currentTimeMillis();
        if (view.listPending && now - view.listSentAt < 8000L) {
            return;
        }
        disarm();
        if (view.mode == 2 && view.personal && (target == null || target.isBlank())) {
            view.state = Load.NONE;
            view.listPending = false;
            view.stale = false;
            view.latestListReqId = "";
            view.target = "";
            view.notes = NO_NOTES;
            view.display = NO_DISPLAY;
            view.hits = NO_HITS;
            view.total = 0;
            view.page = 0;
            view.pages = 1;
            updateViewText(view);
            rebuildViewGeometry(view);
            syncWidgets();
            return;
        }
        final OpCode op = view.mode == 0 ? OpCode.NOTE_LIST : OpCode.NOTE_SCOPED_LIST;
        final String reqId = UUID.randomUUID().toString();
        view.listPending = true;
        view.listSentAt = now;
        view.latestListReqId = reqId;
        view.state = Load.LOADING;
        view.error = "";
        view.target = target == null ? "" : target;
        final int previousPage = view.page;
        view.page = Math.max(0, page);
        if (view.page != previousPage) {
            view.scroll = 0;
        }
        view.stale = false;
        this.pending.put(reqId, new Pending(RequestKind.LIST, op, view, view.target, view.page));

        final Map<String, String> payload = new LinkedHashMap<>();
        payload.put(NoteKeys.KEY_PAGE, Integer.toString(view.page));
        if (view.mode == 1 || view.mode == 2) {
            payload.put(NoteKeys.KEY_SCOPE, view.scopeType);
            payload.put(NoteKeys.KEY_SCOPE_ID, view.scopeId);
            if (!view.target.isBlank()) {
                payload.put("target", view.target);
            }
        }
        GovNetwork.sendRequest(op, payload, reqId);
        updateViewText(view);
        rebuildViewGeometry(view);
        syncWidgets();
    }

    protected void mutate(final OpCode op, final Map<String, String> payload, final View view,
            final String target, final int page) {
        if (mutationBusy()) {
            return;
        }
        if (this.mutationPending) {
            this.pending.entrySet().removeIf(entry -> entry.getValue().kind() == RequestKind.MUTATE);
            this.mutationPending = false;
        }
        final String reqId = UUID.randomUUID().toString();
        this.mutationPending = true;
        this.mutationSentAt = System.currentTimeMillis();
        this.status = "";
        this.statusIsError = false;
        this.pending.put(reqId, new Pending(RequestKind.MUTATE, op, view, target, page));
        GovNetwork.sendRequest(op, payload, reqId);
        syncWidgets();
    }

    protected void applyScopes(final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        final int total = intField(fields, NoteKeys.SCOPES);
        final List<ScopeRow> jobs = new ArrayList<>(MAX_SCOPES);
        final List<ScopeRow> businesses = new ArrayList<>(MAX_SCOPES);
        final int limit = Math.min(Math.max(0, total), 64);
        for (int index = 0; index < limit; index++) {
            final String prefix = NoteKeys.SCOPE_PREFIX + index;
            final String type = fields.get(prefix + NoteKeys.SCOPE_ROW_TYPE);
            final String id = fields.get(prefix + NoteKeys.SCOPE_ROW_ID);
            if (type == null || id == null || id.isBlank()) {
                continue;
            }
            String label = fields.getOrDefault(prefix + NoteKeys.SCOPE_ROW_LABEL, id);
            if (label.isBlank()) {
                label = id;
            }
            final String role = upper(fields.getOrDefault(prefix + NoteKeys.SCOPE_ROW_ROLE, ""));
            final boolean canPost = "true".equals(fields.get(prefix + NoteKeys.SCOPE_ROW_CAN_POST));
            final boolean canModerate = "true".equals(
                    fields.get(prefix + NoteKeys.SCOPE_ROW_CAN_MODERATE));
            final ScopeRow row = new ScopeRow(type, id, label, role, canPost, canModerate,
                    GovText.boldCached(label));
            if ("job".equalsIgnoreCase(type)) {
                if (jobs.size() < MAX_SCOPES) {
                    jobs.add(row);
                }
            } else if ("business".equalsIgnoreCase(type)) {
                if (businesses.size() < MAX_SCOPES) {
                    businesses.add(row);
                }
            }
        }
        this.jobScopes = List.copyOf(jobs);
        this.businessScopes = List.copyOf(businesses);
        this.scopesState = Load.READY;
        this.scopesError = "";

        ScopeRow job = findScope(this.jobScopes, this.selectedJobId);
        if (job == null) {
            job = this.jobScopes.isEmpty() ? null : this.jobScopes.get(0);
        }
        this.selectedJobId = job == null ? "" : job.id();
        this.selectedJobLabel = job == null ? "" : job.label();
        ScopeRow business = findScope(this.businessScopes, this.selectedBizId);
        if (business == null) {
            business = this.businessScopes.isEmpty() ? null : this.businessScopes.get(0);
        }
        this.selectedBizId = business == null ? "" : business.id();
        this.selectedBizLabel = business == null ? "" : business.label();

        for (final ScopeRow row : this.jobScopes) {
            if (!this.jobViews.containsKey(row.id())) {
                this.jobViews.put(row.id(), new View(1, "job", row.id(), false));
            }
        }
        for (final ScopeRow row : this.businessScopes) {
            BusinessState state = this.bizStates.get(row.id());
            if (state == null) {
                state = new BusinessState(row.id(), row.label());
                this.bizStates.put(row.id(), state);
            } else {
                state.label = row.label();
            }
        }

        rebuildScopeGeometry();
        layoutComposer();
        rebuildViewGeometry(currentView());
        syncWidgets();
        if (this.active) {
            ensureMode();
        }
    }

    protected void applyList(final View view, final Pending request, final String delta) {
        final Map<String, String> fields = GovFields.decode(delta);
        if (view.mode != 0) {
            final String scopeType = fields.getOrDefault(NoteKeys.SCOPE_TYPE, view.scopeType);
            final String scopeId = fields.getOrDefault(NoteKeys.SCOPE_ID, view.scopeId);
            if (!scopeType.equalsIgnoreCase(view.scopeType) || !scopeId.equals(view.scopeId)) {
                view.state = Load.ERROR;
                view.error = fallbackError("gui.gov.notes.error");
                view.notes = NO_NOTES;
                view.display = NO_DISPLAY;
                view.hits = NO_HITS;
                view.total = 0;
                view.pages = 1;
                updateViewText(view);
                rebuildViewGeometry(view);
                if (view == currentView()) {
                    syncWidgets();
                }
                return;
            }
            final String label = fields.get(NoteKeys.SCOPE_LABEL);
            if (label != null && !label.isBlank()) {
                view.scopeLabel = label;
                if (view.mode == 1) {
                    if (view.scopeId.equals(this.selectedJobId)) {
                        this.selectedJobLabel = label;
                    }
                } else if (view.mode == 2) {
                    final BusinessState state = this.bizStates.get(view.scopeId);
                    if (state != null) {
                        state.label = label;
                    }
                    if (view.scopeId.equals(this.selectedBizId)) {
                        this.selectedBizLabel = label;
                    }
                }
            }
        }
        view.canPost = "true".equals(fields.get(NoteKeys.CAN_POST_NOTES))
                || "true".equals(fields.get(NoteKeys.CAN_MANAGE));
        view.canModerate = "true".equals(fields.get(NoteKeys.CAN_MODERATE_NOTES));
        view.total = Math.max(0, intField(fields, NoteKeys.TOTAL));
        final int oldPage = view.page;
        view.page = Math.max(0, intField(fields, NoteKeys.PAGE));
        view.pages = Math.max(1, intField(fields, NoteKeys.PAGES));
        view.notes = readNotes(fields);
        view.state = Load.READY;
        view.error = "";
        view.stale = false;
        view.target = request.target() == null ? "" : request.target();
        if (view.page != oldPage) {
            view.scroll = 0;
        }
        updateViewText(view);
        rebuildViewGeometry(view);
        layoutComposer();
        if (view == currentView()) {
            syncWidgets();
        }
    }

}
