package com.horizonssmp.gov.client;

import com.horizonssmp.gov.client.GovNotesTypes.*;
import static com.horizonssmp.gov.client.GovNotesTypes.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.horizonssmp.gov.common.NoteKeys;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

class GovNotesUtil extends GovNotesState {
    GovNotesUtil(final GovDashboardScreen host) {
        super(host);
    }

    protected static String tr(final String key, final Object... args) {
        return Component.translatable(key, args).getString();
    }

    protected static String upper(final String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }

    protected static String fallbackError(final String key) {
        return Component.translatable(key).getString();
    }

    protected static String localPlayerName() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return "";
        }
        return minecraft.player.getGameProfile().getName();
    }

    protected static String ageText(final long epochMillis) {
        if (epochMillis <= 0L) {
            return "UNKNOWN";
        }
        final long delta = Math.max(0L, System.currentTimeMillis() - epochMillis);
        if (delta < 60_000L) {
            return "NOW";
        }
        if (delta < 3_600_000L) {
            return (delta / 60_000L) + " MIN";
        }
        if (delta < 86_400_000L) {
            return (delta / 3_600_000L) + " H";
        }
        if (delta < 2_592_000_000L) {
            return (delta / 86_400_000L) + " D";
        }
        return (delta / 2_592_000_000L) + " MO";
    }

    protected static ScopeRow findScope(final List<ScopeRow> scopes, final String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (final ScopeRow scope : scopes) {
            if (scope.id().equals(id)) {
                return scope;
            }
        }
        return null;
    }

    protected static List<NoteRow> readNotes(final Map<String, String> fields) {
        final List<NoteRow> rows = new ArrayList<>(NoteKeys.PAGE_SIZE);
        for (int index = 0; index < NoteKeys.PAGE_SIZE; index++) {
            final String prefix = NoteKeys.ROW_PREFIX + index;
            final String id = fields.get(prefix + NoteKeys.ROW_ID);
            if (id == null || id.isBlank()) {
                continue;
            }
            rows.add(new NoteRow(
                    id,
                    fields.getOrDefault(prefix + NoteKeys.ROW_TARGET_UUID, ""),
                    fields.getOrDefault(prefix + NoteKeys.ROW_TARGET, ""),
                    fields.getOrDefault(prefix + NoteKeys.ROW_AUTHOR, ""),
                    fields.getOrDefault(prefix + NoteKeys.ROW_TEXT, ""),
                    longField(fields, prefix + NoteKeys.ROW_CREATED),
                    longField(fields, prefix + NoteKeys.ROW_EDITED)));
        }
        return List.copyOf(rows);
    }

    protected static int intField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.intField(fields, key);
    }

    protected static long longField(final Map<String, String> fields, final String key) {
        return com.horizonssmp.gov.common.GovFields.longField(fields, key);
    }

    protected static Map<String, String> basePayload(final View view) {
        final Map<String, String> payload = new LinkedHashMap<>();
        if (view.mode == 1 || view.mode == 2) {
            payload.put(NoteKeys.KEY_SCOPE, view.scopeType);
            payload.put(NoteKeys.KEY_SCOPE_ID, view.scopeId);
        }
        return payload;
    }

    protected boolean mutationBusy() {
        return this.mutationPending
                && System.currentTimeMillis() - this.mutationSentAt < MUTATION_TIMEOUT_MS;
    }

    protected boolean listBusy(final View view) {
        return view != null && view.listPending
                && System.currentTimeMillis() - view.listSentAt < 8000L;
    }

    protected void expireMutationIfStale() {
        if (this.mutationPending && !mutationBusy()) {
            this.pending.entrySet().removeIf(entry -> entry.getValue().kind() == RequestKind.MUTATE);
            this.mutationPending = false;
            this.mutationSentAt = 0L;
            this.status = this.mutationErrorText;
            this.statusIsError = true;
            syncWidgets();
        }
    }

    protected List<ScopeRow> currentScopes() {
        if (this.mode == 1) {
            return this.jobScopes;
        }
        if (this.mode == 2) {
            return this.businessScopes;
        }
        return NO_SCOPES;
    }

    protected View currentView() {
        if (this.mode == 0) {
            return this.allView;
        }
        if (this.mode == 1) {
            return this.selectedJobId.isEmpty() ? null : this.jobViews.get(this.selectedJobId);
        }
        if (this.mode == 2) {
            final BusinessState state = this.bizStates.get(this.selectedBizId);
            return state == null ? null : state.current();
        }
        return null;
    }

    protected void syncWidgets() {
        if (this.targetBox == null || this.textBox == null) {
            return;
        }
        final boolean targetVisible = targetFieldVisible();
        final boolean textVisible = textFieldVisible();
        this.targetBox.setVisible(targetVisible && this.targetFieldRect.w() > 8
                && this.targetFieldRect.h() > 0);
        this.targetBox.setEditable(targetVisible && this.editingNoteId.isEmpty()
                && !mutationBusy());
        this.textBox.setVisible(textVisible && this.textFieldRect.w() > 8
                && this.textFieldRect.h() > 0);
        this.textBox.setEditable(textVisible && !mutationBusy());
        if (!this.targetBox.isVisible() && this.host.getFocused() == this.targetBox) {
            this.host.setFocused(null);
        }
        if (!this.textBox.isVisible() && this.host.getFocused() == this.textBox) {
            this.host.setFocused(null);
        }
    }

    protected boolean targetFieldVisible() {
        if (!this.active || this.pickerOpen || !this.editingNoteId.isEmpty()
                || this.targetBox == null) {
            return false;
        }
        if (this.mode != 0 && this.scopesState != Load.READY) {
            return false;
        }
        if (this.mode == 0 || this.mode == 1) {
            final View view = currentView();
            return view != null && view.state == Load.READY && view.canPost;
        }
        if (this.mode == 2) {
            final BusinessState state = this.bizStates.get(this.selectedBizId);
            return state != null && state.personalMode;
        }
        return false;
    }

    protected boolean textFieldVisible() {
        if (!this.active || this.pickerOpen || this.textBox == null) {
            return false;
        }
        if (this.mode != 0 && this.scopesState != Load.READY) {
            return false;
        }
        final View view = currentView();
        return view != null && view.state == Load.READY && view.canPost;
    }

    protected boolean targetRelevant() {
        if (this.mode == 0 || this.mode == 1) {
            return true;
        }
        if (this.mode == 2) {
            final BusinessState state = this.bizStates.get(this.selectedBizId);
            return state != null && state.personalMode;
        }
        return false;
    }

    protected boolean requiresTarget() {
        return this.mode == 0 || (this.mode == 2 && targetRelevant());
    }

    protected void disarm() {
        this.deleteArmedNoteId = "";
        this.deleteArmedAt = 0L;
    }

    protected String refreshLabel(final View view) {
        if (view != null && view.mode == 2 && view.personal && view.state == Load.NONE
                && this.hasTarget) {
            return this.loadText;
        }
        return this.refreshText;
    }

    protected boolean prevEnabled(final View view) {
        return view != null && view.state == Load.READY && !listBusy(view)
                && !mutationBusy() && view.page > 0;
    }

    protected boolean nextEnabled(final View view) {
        return view != null && view.state == Load.READY && !listBusy(view)
                && !mutationBusy() && view.page < view.pages - 1;
    }

    protected boolean refreshEnabled(final View view) {
        if (view == null || listBusy(view) || mutationBusy()
                || view.state == Load.LOADING) {
            return false;
        }
        if (this.mode == 2 && targetRelevant() && !this.hasTarget) {
            return false;
        }
        return true;
    }

    protected boolean postEnabled(final View view) {
        if (view == null || view.state != Load.READY || !view.canPost
                || mutationBusy() || !this.hasText) {
            return false;
        }
        if (!this.editingNoteId.isEmpty()) {
            return true;
        }
        if (requiresTarget() && !this.hasTarget) {
            return false;
        }
        return !(view.mode == 2 && view.personal && this.targetDirty);
    }
}
